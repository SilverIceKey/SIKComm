package com.sik.comm.impl_ble

import android.content.Context
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
import com.sik.comm.impl_ble.io.AndroidGattIo
import com.sik.comm.impl_ble.io.BleIo
import com.sik.comm.impl_ble.internal.ConnectionPool
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 协议实现（无业务）。
 * - connect/disconnect：打开 I/O、发现、订阅、状态流转、插件回调
 * - send：拦截器链 → 最终把 payload 下发；如配置了路由键，则等待匹配到的通知返回
 */
class BleProtocol(
    private val appContext: Context,
    private val logger: ProtocolLogger = DefaultProtocolLogger,
    private val ioFactory: (BleConfig) -> BleIo = { AndroidGattIo(appContext) }
) : Protocol {

    private val devices = ConcurrentHashMap<String, BleDevice>()
    private val configs = ConcurrentHashMap<String, BleConfig>()
    private var pool: ConnectionPool = ConnectionPool(2)

    fun registerConfig(config: BleConfig) {
        configs[config.deviceId] = config
        pool = ConnectionPool(config.maxGlobalConnections)
    }

    override fun connect(deviceId: String) {
        val cfg = requireConfig(deviceId)
        val injected = CommInjection.injectTo(cfg)

        DeviceStateCenter.updateState(deviceId, ProtocolState.CONNECTING)
        injected.plugins.forEach { p -> runCatching { p.onStateChanged(BlePluginScopes.scope(cfg, ProtocolState.CONNECTING)) } }

        val io = ioFactory(cfg)
        // 扫描策略：若给定 whitelist，取其首；否则直接尝试（保持最小实现；更复杂策略可在外部先定位 MAC 再传入）
        val mac = cfg.whitelist.firstOrNull() ?: error("BleConfig.whitelist must contain target MAC for connect()")

        // 用连接池控制并发
        kotlinx.coroutines.runBlocking {
            pool.withPermit {
                io.open(mac)
                DeviceStateCenter.updateState(deviceId, ProtocolState.CONNECTED)
                injected.plugins.forEach { p -> runCatching { p.onStateChanged(BlePluginScopes.scope(cfg, ProtocolState.CONNECTED)) } }

                io.discover()
                if (cfg.expectMtu != null) io.requestMtu(cfg.expectMtu)
                if (cfg.enableNotifyOnReady) io.subscribe(GattRoute(cfg.service, cfg.writeChar, cfg.notifyChar))

                val dev = BleDevice(cfg, io, injected.interceptors, injected.plugins)
                devices[deviceId] = dev

                DeviceStateCenter.updateState(deviceId, ProtocolState.READY)
                injected.plugins.forEach { p -> runCatching { p.onStateChanged(BlePluginScopes.scope(cfg, ProtocolState.READY)) } }
                logger.onConnect(deviceId)
            }
        }
    }

    override fun disconnect(deviceId: String) {
        devices.remove(deviceId)?.let { dev ->
            runCatching { dev.io.close() }
            DeviceStateCenter.updateState(deviceId, ProtocolState.DISCONNECTED)
            dev.plugins.forEach { p -> runCatching { p.onStateChanged(BlePluginScopes.scope(dev.config, ProtocolState.DISCONNECTED)) } }
            logger.onDisconnect(deviceId)
        }
    }

    override fun isConnected(deviceId: String): Boolean {
        val d = devices[deviceId] ?: return false
        return try { kotlinx.coroutines.runBlocking { d.io.isOpen() } } catch (_: Throwable) { false }
    }

    override suspend fun send(deviceId: String, msg: CommMessage): CommMessage {
        val dev = devices[deviceId] ?: error("Device[$deviceId] not connected.")
        val cfg = dev.config
        val scope = InterceptorScope(deviceId, ProtocolType.BLE, cfg, msg)
        val route = GattRoute(cfg.service, cfg.writeChar, cfg.notifyChar)

        // 拦截器链：最终执行写入；若配置了路由键，则等待匹配通知作为响应
        val chain = InterceptorChain(scope, dev.interceptors) { finalMsg ->
            // 1) beforeSend 插件
            dev.plugins.forEach { p ->
                runCatching {
                    p.onBeforeSend(
                        BlePluginScopes.scope(
                            cfg,
                            DeviceStateCenter.getState(deviceId),
                            finalMsg
                        )
                    )
                }
            }

            // 2) 发送（分片/ACK 在 IO 层处理）
            val payload = finalMsg.payload
            val withResp = cfg.preferWriteWithResponse
            val timeout = cfg.writeTimeoutMs
            // 低性能模块：片间延时（由外部调用 send 多次控制；此处仅一次写入）
            dev.io.write(route, payload, withResp)

            // 3) 是否等待应答：如果配置了键提取器，则注册并等待匹配通知
            val rsp: ByteArray? = if (cfg.requestKeyOf != null && cfg.notifyKeyOf != null) {
                val reqKey = cfg.requestKeyOf.invoke(payload)
                val waiter = dev.router.register(reqKey)
                // 利用一个轻量轮询把通知匹配进路由器
                val deadline = System.currentTimeMillis() + timeout
                while (System.currentTimeMillis() < deadline) {
                    val n = dev.io.read(50) ?: continue
                    val key = cfg.notifyKeyOf.invoke(n) ?: continue
                    dev.router.resolve(key, n)
                    if (key == reqKey) break
                }
                try { dev.router.await(reqKey, timeout) } catch (t: Throwable) { throw t }
            } else null

            // 4) afterReceive 插件
            rsp?.let {
                dev.plugins.forEach { p ->
                    runCatching {
                        p.onReceive(BlePluginScopes.scope(cfg, DeviceStateCenter.getState(deviceId), CommMessage("BLE_RSP", it, emptyMap())))
                    }
                }
            }
            // 返回 CommMessage（若无应答则返回空载荷）
            if (rsp != null) CommMessage("BLE_RSP", rsp, emptyMap()) else CommMessage("BLE_SENT", ByteArray(0), emptyMap())
        }
        return chain.proceed(scope.message)
    }

    private fun requireConfig(deviceId: String): BleConfig =
        configs[deviceId] ?: error("BleConfig for deviceId=$deviceId not registered. Call BleProtocol.registerConfig().")
}