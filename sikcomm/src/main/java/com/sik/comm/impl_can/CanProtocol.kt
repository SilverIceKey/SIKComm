package com.sik.comm.impl_can

import com.sik.comm.core.extension.metaBool
import com.sik.comm.core.extension.metaNum
import com.sik.comm.core.injection.CommInjection
import com.sik.comm.core.interceptor.InterceptorChain
import com.sik.comm.core.interceptor.InterceptorScope
import com.sik.comm.core.logger.DefaultProtocolLogger
import com.sik.comm.core.logger.ProtocolLogger
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.protocol.Protocol
import com.sik.comm.core.protocol.ProtocolType
import com.sik.comm.core.state.DeviceStateCenter
import com.sik.comm.core.task.DeviceTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import com.sik.comm.impl_can.MetaKeys as MK

/**
 * CAN 协议实现（依赖 SIKComm）。
 *
 * 功能：
 * - connect/disconnect：打开/关闭底层 I/O，并启动/停止读循环
 * - send：走拦截器链，进入终端发送；等待 Router 匹配到应答返回
 * - 状态流转：DeviceStateCenter 统一派发；插件 onStateChanged/onBeforeSend/onReceive/onError
 *
 * 约定（默认按你方文档）：
 * - 发送与应答均走 ID = txBaseId/rxBaseId + nodeId（默认 0x600 + nodeId）
 * - SDO 帧结构：Byte0=命令字，Byte1..2=Index(LE)，Byte3=SubIndex，Byte4..7=数据小端（读时为 00）
 */
class CanProtocol(
    private val logger: ProtocolLogger = DefaultProtocolLogger,
    private val ioFactory: (CanConfig) -> CanIo = { cfg ->
        SocketCanIo(
            ifName = cfg.interfaceName,
            bitrate = cfg.bitrate,
            samplePoint = cfg.samplePoint,
            upOnOpen = cfg.upOnOpen,
            useSu = cfg.useSu,
            readTimeoutMs = cfg.readTimeoutMs,
            filters = cfg.filters
        )
    },
    parentContext: CoroutineContext = SupervisorJob() + Dispatchers.IO
) : Protocol {

    /** 每个 deviceId 对应一个 CanDevice（包含 io、router、拦截器、插件、读环） */
    private val devices = mutableMapOf<String, CanDevice>()

    /** 用于读线程等 */
    private val scope = CoroutineScope(parentContext)

    /** 本协议内部维护的 config 注册表（因为 ProtocolManager 只存协议类型，不存 config） */
    private val configs = mutableMapOf<String, CanConfig>()

    /**
     * 将具体的 CanConfig 注册给本协议（建议在 ProtocolManager.bindDeviceConfig 之后调用一次）
     */
    fun registerConfig(config: CanConfig) {
        configs[config.deviceId] = config
    }

    override fun connect(deviceId: String) {
        val cfg = requireCanConfig(deviceId)
        val injected = CommInjection.injectTo(cfg)

        DeviceStateCenter.updateState(deviceId, ProtocolState.CONNECTING)
        injected.plugins.forEach { p ->
            runCatching { p.onStateChanged(CanPluginScopes.scope(cfg, ProtocolState.CONNECTING)) }
        }

        // ✅ 这里带上初始化参数创建 IO
        val io = ioFactory(cfg)

        // 打开底层 I/O（open 内部会先尝试 ip link up）
        io.open(cfg.interfaceName)

        val dev = CanDevice(
            config = cfg,
            io = io,
            interceptors = injected.interceptors,
            plugins = injected.plugins,
            externalScope = scope
        )
        devices[deviceId] = dev

        DeviceStateCenter.updateState(deviceId, ProtocolState.CONNECTED)
        injected.plugins.forEach { p ->
            runCatching { p.onStateChanged(CanPluginScopes.scope(cfg, ProtocolState.CONNECTED)) }
        }

        dev.startReaderLoop { frame -> handleIncomingFrame(dev, frame) }

        DeviceStateCenter.updateState(deviceId, ProtocolState.READY)
        injected.plugins.forEach { p ->
            runCatching { p.onStateChanged(CanPluginScopes.scope(cfg, ProtocolState.READY)) }
        }
        logger.onConnect(deviceId)
    }

    override fun disconnect(deviceId: String) {
        devices.remove(deviceId)?.let { dev ->
            dev.stop()
            DeviceStateCenter.updateState(deviceId, ProtocolState.DISCONNECTED)
            dev.plugins.forEach { p ->
                runCatching {
                    p.onStateChanged(
                        CanPluginScopes.scope(
                            dev.config, ProtocolState.DISCONNECTED
                        )
                    )
                }
            }
            logger.onDisconnect(deviceId)
        }
    }

    override fun isConnected(deviceId: String): Boolean {
        return devices[deviceId]?.io?.isOpen() == true
    }

    suspend fun send(deviceId: String, req: SdoRequest): SdoResponse {
        val dev = devices[deviceId] ?: error("Device[$deviceId] not connected.")
        val cfg = dev.config
        val sdo = cfg.sdo

        return DeviceTaskManager.withLock(deviceId) {
            // beforeSend 插件（可选）
            dev.plugins.forEach { p ->
                runCatching {
                    p.onBeforeSend(
                        CanPluginScopes.scope(
                            cfg,
                            DeviceStateCenter.getState(deviceId),
                            CommMessage(SdoOp.READ_REQ.name, ByteArray(0), emptyMap()) // 仅用于回调埋点
                        )
                    )
                }
            }

            // 目标 CAN-ID
            val canId = req.canIdOverride ?: (cfg.txBaseId + req.nodeId)

            // 组帧 + 下发
            val frame = when (req) {
                is SdoRequest.Read -> buildReadSdo(sdo, canId, req.index, req.subIndex)
                is SdoRequest.Write -> buildWriteSdo(
                    sdo, canId, req.index, req.subIndex, req.payload, req.size
                )
            }
            dev.io.write(frame)

            // 注册 waiter 并等待
            val key = CanRequestKey(req.nodeId, req.index, req.subIndex)
            dev.router.register(key)
            val rsp = dev.router.await(key, req.timeoutMs)

            // afterReceive 插件（可选）
            dev.plugins.forEach { p ->
                runCatching {
                    p.onReceive(
                        CanPluginScopes.scope(
                            cfg, DeviceStateCenter.getState(deviceId), rsp.toCommMessage()
                        )
                    )
                }
            }
            rsp
        }
    }

    override suspend fun send(deviceId: String, msg: CommMessage): CommMessage {
        val dev = devices[deviceId] ?: error("Device[$deviceId] not connected.")
        val cfg = dev.config
        val scope = InterceptorScope(deviceId, ProtocolType.CAN, cfg, msg)
        val chain = InterceptorChain(scope, dev.interceptors) { finalMsg ->
            val req = finalMsg.toSdoRequest {
                Triple(
                    // 优先取消息里的 nodeId，其次 deviceId@X，最后 defaultNodeId
                    finalMsg.metaNum<Int>(MK.NODE_ID)
                        ?: parseNodeIdFromDeviceId(cfg.deviceId)
                        ?: cfg.defaultNodeId,
                    cfg.txBaseId,
                    cfg.rxBaseId
                )
            }
            send(deviceId, req).toCommMessage()
        }
        // 从头启动链；返回最终响应
        return chain.proceed(scope.message)
    }

    // —— 私有辅助 —— //

    private fun requireCanConfig(deviceId: String): CanConfig {
        return (configs[deviceId]
            ?: error("CanConfig for deviceId=$deviceId not registered. Call CanProtocol.registerConfig()."))
    }


    private fun buildReadSdo(sdo: SdoDialect, canId: Int, index: Int, subIndex: Int): CanFrame {
        val data = ByteArray(8)
        data[0] = sdo.READ.toByte()
        CanUtils.putU16LE(data, 1, index)
        data[3] = (subIndex and 0xFF).toByte()
        // data[4..7] = 0
        return CanFrame(canId, 8, data)
    }

    private fun buildWriteSdo(
        sdo: SdoDialect, canId: Int, index: Int, subIndex: Int, payload: ByteArray, size: Int
    ): CanFrame {
        require(size == 1 || size == 2 || size == 4) { "SDO write size must be 1/2/4 bytes." }
        require(payload.size >= size) { "payload length ${payload.size} < size $size" }

        val data = ByteArray(8)
        val cmd = when (size) {
            1 -> sdo.WRITE_1B
            2 -> sdo.WRITE_2B
            else -> sdo.WRITE_4B
        }
        data[0] = cmd.toByte()
        CanUtils.putU16LE(data, 1, index)
        data[3] = (subIndex and 0xFF).toByte()
        // 小端填充 data[4..7]
        when (size) {
            1 -> data[4] = payload[0]
            2 -> {
                data[4] = payload[0]
                data[5] = payload[1]
            }

            4 -> {
                data[4] = payload[0]
                data[5] = payload[1]
                data[6] = payload[2]
                data[7] = payload[3]
            }
        }
        return CanFrame(canId, 8, data)
    }

    /**
     * 处理读环收到的每一帧：解析 SDO，应答匹配或异步上报。
     */
    private fun handleIncomingFrame(dev: CanDevice, frame: CanFrame) {
        if (frame.dlc < 1 || frame.data.isEmpty()) return
        val cfg = dev.config
        val sdo = cfg.sdo
        val cmd = frame.data[0].toInt() and 0xFF

        val index = CanUtils.getU16LE(frame.data, 1)
        val sub = frame.data[3].toInt() and 0xFF

        val candidates = listOf(cfg.rxBaseId, if (cfg.rxBaseId == 0x600) 0x580 else 0x600)
        val nodeId =
            candidates.map { base -> frame.canId - base }.firstOrNull { it in 0..0x7FF } ?: return
        val key = CanRequestKey(nodeId, index, sub)

        when (cmd) {
            sdo.WRITE_ACK -> {
                dev.router.resolve(key, SdoResponse.WriteAck(nodeId, index, sub, frame.canId))
            }

            sdo.ERROR -> {
                val abort = CanUtils.getU32LE(frame.data, 4).toLong() and 0xFFFF_FFFFL
                dev.router.resolve(
                    key,
                    SdoResponse.Error(nodeId, index, sub, frame.canId, abort, frame.data.copyOf())
                )
            }

            sdo.WRITE_1B, sdo.WRITE_2B, sdo.WRITE_4B -> {
                val size = when (cmd) {
                    sdo.WRITE_1B -> 1
                    sdo.WRITE_2B -> 2
                    else -> 4
                }
                val pl = ByteArray(size).also { System.arraycopy(frame.data, 4, it, 0, size) }
                dev.router.resolve(key, SdoResponse.ReadData(nodeId, index, sub, frame.canId, pl))
            }

            else -> {
                // 异步/广播；按需投递插件（如果你要保留）
                dev.plugins.forEach { p ->
                    runCatching {
                        p.onReceive(
                            CanPluginScopes.scope(
                                cfg, DeviceStateCenter.getState(cfg.deviceId),
                                // 仍可给插件一个兼容消息
                                SdoResponse.ReadData(
                                    nodeId, index, sub, frame.canId, frame.data.copyOf()
                                ).toCommMessage()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun parseNodeIdFromDeviceId(deviceId: String): Int? {
        val at = deviceId.lastIndexOf('@')
        if (at <= 0 || at == deviceId.length - 1) return null
        return deviceId.substring(at + 1).toIntOrNull()
    }
}
