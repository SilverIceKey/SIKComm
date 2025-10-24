package com.sik.comm.impl_modbus

import com.sik.comm.core.inject.CommInjection
import com.sik.comm.core.interceptor.InterceptorChain
import com.sik.comm.core.interceptor.InterceptorScope
import com.sik.comm.core.logger.DefaultProtocolLogger
import com.sik.comm.core.logger.ProtocolLogger
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.model.TxPlan
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.policy.ChainPolicy
import com.sik.comm.core.protocol.LinkIO
import com.sik.comm.core.protocol.Protocol
import com.sik.comm.core.protocol.ProtocolType
import com.sik.comm.core.state.DeviceStateCenter
import com.sik.comm.core.task.DeviceTaskManager
import kotlinx.coroutines.delay
import java.net.URI

/**
 * Modbus 协议实现，支持 RS485 与 TCP 封装。
 */
class ModbusProtocol(
    private val logger: ProtocolLogger = DefaultProtocolLogger,
    private val transportFactory: (ModbusConfig) -> ModbusTransport = { cfg ->
        val name = cfg.portName
        when {
            name.startsWith("tcp://", ignoreCase = true) -> {
                val uri = runCatching { URI.create(name) }.getOrElse {
                    throw IllegalArgumentException(
                        "Invalid tcp url: ${cfg.portName}",
                        it
                    )
                }
                val host = uri.host ?: error("Invalid tcp url: ${cfg.portName}")
                val port = if (uri.port != -1) uri.port else 502
                TcpModbusTransport(host, port, cfg.connectTimeoutMs)
            }

            !name.startsWith("/") && name.contains(":") -> {
                val parts = name.split(":", limit = 2)
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 502
                TcpModbusTransport(host, port, cfg.connectTimeoutMs)
            }

            else -> SerialModbusTransport(cfg)
        }
    }
) : Protocol {

    private val devices = mutableMapOf<String, ModbusDevice>()
    private val configs = mutableMapOf<String, ModbusConfig>()

    fun registerConfig(config: ModbusConfig) {
        configs[config.deviceId] = config
    }

    override fun connect(deviceId: String) {
        val existing = devices[deviceId]
        if (existing?.transport?.isOpen() == true) {
            return
        }
        val cfg = requireModbusConfig(deviceId)
        val injected = CommInjection.injectTo(cfg)

        DeviceStateCenter.updateState(deviceId, ProtocolState.CONNECTING)
        notifyState(injected.plugins, cfg, ProtocolState.CONNECTING)

        val transport = transportFactory(cfg)
        try {
            transport.open()
        } catch (t: Throwable) {
            runCatching { transport.close() }
            DeviceStateCenter.updateState(deviceId, ProtocolState.ERROR)
            notifyState(injected.plugins, cfg, ProtocolState.ERROR)
            injected.plugins.forEach { plugin ->
                runCatching {
                    plugin.onError(
                        ModbusPluginScopes.scope(cfg, ProtocolState.ERROR),
                        t
                    )
                }
            }
            logger.onError(deviceId, t)
            throw t
        }

        val device = ModbusDevice(cfg, transport, injected.interceptors, injected.plugins)
        devices[deviceId] = device

        val attachScope = ModbusPluginScopes.scope(cfg, ProtocolState.CONNECTED)
        device.plugins.forEach { plugin ->
            runCatching { plugin.onAttach(attachScope) }
        }

        DeviceStateCenter.updateState(deviceId, ProtocolState.CONNECTED)
        notifyState(device.plugins, cfg, ProtocolState.CONNECTED)

        DeviceStateCenter.updateState(deviceId, ProtocolState.READY)
        notifyState(device.plugins, cfg, ProtocolState.READY)

        logger.onConnect(deviceId)
    }

    override fun disconnect(deviceId: String) {
        val device = devices.remove(deviceId) ?: return
        device.close()
        DeviceTaskManager.clear(deviceId)
        DeviceStateCenter.updateState(deviceId, ProtocolState.DISCONNECTED)
        notifyState(device.plugins, device.config, ProtocolState.DISCONNECTED)
        val detachScope = ModbusPluginScopes.scope(device.config, ProtocolState.DISCONNECTED)
        device.plugins.forEach { plugin ->
            runCatching { plugin.onDetach(detachScope) }
        }
        logger.onDisconnect(deviceId)
    }

    override fun isConnected(deviceId: String): Boolean {
        return devices[deviceId]?.transport?.isOpen() == true
    }

    override suspend fun send(deviceId: String, msg: CommMessage): CommMessage {
        val device = devices[deviceId] ?: error("Device [$deviceId] not connected.")
        if (!device.transport.isOpen()) {
            error("Device [$deviceId] transport is closed.")
        }
        val cfg = device.config
        val scope = InterceptorScope(deviceId, ProtocolType.RS485, cfg, msg)
        val chain = InterceptorChain(scope, device.interceptors) { message ->
            performSend(device, message)
        }
        return chain.proceed(msg)
    }

    private suspend fun performSend(device: ModbusDevice, message: CommMessage): CommMessage {
        val cfg = device.config
        val prepared = prepareRequest(cfg, message)
        val timeoutMs = (message.metadata[ModbusMetaKeys.TIMEOUT] as? Number)?.toInt()
            ?: cfg.requestTimeoutMs
        val silenceGap = (message.metadata[ModbusMetaKeys.SILENCE_GAP] as? Number)?.toInt()
            ?: cfg.responseGapMs
        val expected = (message.metadata[ModbusMetaKeys.EXPECTED_LENGTH] as? Number)?.toInt()
            ?: computeExpectedLength(prepared.functionCode, message)

        return DeviceTaskManager.withLock(cfg.deviceId) {
            DeviceStateCenter.updateState(cfg.deviceId, ProtocolState.BUSY)
            notifyState(device.plugins, cfg, ProtocolState.BUSY)

            val beforeScope = ModbusPluginScopes.scope(cfg, ProtocolState.BUSY, prepared.codecInput)
            device.plugins.forEach { plugin ->
                runCatching { plugin.onBeforeSend(beforeScope) }
            }

            try {
                val frame = cfg.codec.encode(prepared.codecInput)
                device.transport.write(frame)
                val responseBytes = device.transport.readFrame(timeoutMs, expected, silenceGap)
                val decoded = cfg.codec.decode(responseBytes)
                val response =
                    buildResponseMessage(message, decoded, responseBytes, prepared.headerBytes)

                DeviceStateCenter.updateState(cfg.deviceId, ProtocolState.READY)
                notifyState(device.plugins, cfg, ProtocolState.READY)

                val receiveScope = ModbusPluginScopes.scope(cfg, ProtocolState.READY, response)
                device.plugins.forEach { plugin ->
                    runCatching { plugin.onReceive(receiveScope) }
                }
                response
            } catch (t: Throwable) {
                DeviceStateCenter.updateState(cfg.deviceId, ProtocolState.ERROR)
                notifyState(device.plugins, cfg, ProtocolState.ERROR)
                val errorScope =
                    ModbusPluginScopes.scope(cfg, ProtocolState.ERROR, prepared.codecInput)
                device.plugins.forEach { plugin ->
                    runCatching { plugin.onError(errorScope, t) }
                }
                logger.onError(cfg.deviceId, t)
                throw t
            }
        }
    }

    private fun prepareRequest(config: ModbusConfig, message: CommMessage): PreparedRequest {
        val functionCode = (message.metadata[ModbusMetaKeys.FUNCTION_CODE] as? Number)?.toInt()
        val unitId = (message.metadata[ModbusMetaKeys.UNIT_ID] as? Number)?.toInt()
            ?: config.defaultUnitId

        val header = when {
            functionCode != null -> {
                val id = unitId ?: error("UnitId required when functionCode is provided")
                byteArrayOf(id.requireByte("unitId"), functionCode.requireByte("functionCode"))
            }

            unitId != null -> byteArrayOf(unitId.requireByte("unitId"))
            else -> ByteArray(0)
        }
        val payload = header + message.payload
        val codecInput = CommMessage(
            command = message.command,
            payload = payload,
            metadata = message.metadata
        )
        return PreparedRequest(codecInput, header.size, functionCode)
    }

    private fun computeExpectedLength(functionCode: Int?, message: CommMessage): Int? {
        if (functionCode == null) return null
        val quantity = (message.metadata[ModbusMetaKeys.QUANTITY] as? Number)?.toInt()
            ?: parseQuantity(functionCode, message.payload)
        return when (functionCode) {
            0x01, 0x02 -> quantity?.let { 5 + ((it + 7) / 8) }
            0x03, 0x04 -> quantity?.let { 5 + (it * 2) }
            0x05, 0x06 -> 8
            0x0F, 0x10 -> 8
            else -> null
        }
    }

    private fun parseQuantity(functionCode: Int, payload: ByteArray): Int? {
        return when (functionCode) {
            0x01, 0x02, 0x03, 0x04, 0x0F, 0x10 -> if (payload.size >= 4) {
                ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
            } else {
                null
            }

            else -> null
        }
    }

    private fun buildResponseMessage(
        request: CommMessage,
        decoded: CommMessage,
        rawFrame: ByteArray,
        headerBytes: Int
    ): CommMessage {
        val payload = decoded.payload
        val metadata = request.metadata.toMutableMap()
        metadata[ModbusMetaKeys.RAW_FRAME] = rawFrame.copyOf()
        metadata[ModbusMetaKeys.RAW_PAYLOAD] = payload.copyOf()
        decoded.metadata["crc"]?.let { crc ->
            metadata[ModbusMetaKeys.CRC] = crc
        }
        if (payload.isNotEmpty()) {
            metadata[ModbusMetaKeys.UNIT_ID] = payload[0].toInt() and 0xFF
        }
        if (payload.size >= 2) {
            val function = payload[1].toInt() and 0xFF
            metadata[ModbusMetaKeys.FUNCTION_CODE] = function
            if ((function and 0x80) != 0 && payload.size >= 3) {
                metadata[ModbusMetaKeys.EXCEPTION_CODE] = payload[2].toInt() and 0xFF
            }
        }
        val strip = headerBytes.coerceAtMost(payload.size)
        val body = payload.copyOfRange(strip, payload.size)
        return CommMessage(
            command = request.command,
            payload = body,
            metadata = metadata
        )
    }

    override fun toString(): String {
        return "ModbusProtocol(devices=${devices.keys})"
    }

    private fun notifyState(plugins: List<CommPlugin>, config: ModbusConfig, state: ProtocolState) {
        val scope = ModbusPluginScopes.scope(config, state)
        plugins.forEach { plugin ->
            runCatching { plugin.onStateChanged(scope) }
        }
    }

    private fun requireModbusConfig(deviceId: String): ModbusConfig {
        return configs[deviceId]
            ?: error("ModbusConfig for deviceId=$deviceId not registered. Call ModbusProtocol.registerConfig().")
    }

    private data class PreparedRequest(
        val codecInput: CommMessage,
        val headerBytes: Int,
        val functionCode: Int?
    )

    private fun Int.requireByte(name: String): Byte {
        require(this in 0..0xFF) { "$name out of range: $this" }
        return toByte()
    }

    /**
     * 多包链式发送：按 plan.frames 顺序写出，每步交给 policy 决定如何等待/校验/是否继续。
     * - 不走 Modbus 编解码，直接原样把 payload 写到传输层。
     * - 仍然发出插件事件（onBeforeSend/onReceive/onError）和状态通知。
     */
    override suspend fun sendChain(
        deviceId: String,
        plan: TxPlan,
        policy: ChainPolicy
    ): List<CommMessage> {
        val device = devices[deviceId] ?: error("Device [$deviceId] not connected.")
        val cfg = device.config
        require(device.transport.isOpen()) { "Device [$deviceId] transport is closed." }
        // 将 ModbusTransport 适配成裸 I/O
        val io = object : LinkIO {
            override suspend fun writeRaw(msg: CommMessage) {
                val scope = ModbusPluginScopes.scope(cfg, ProtocolState.BUSY, msg)
                device.plugins.forEach { p -> runCatching { p.onBeforeSend(scope) } }
                device.transport.write(msg.payload)
            }

            override suspend fun readRaw(
                timeoutMs: Int,
                expectedSize: Int?,
                silenceGapMs: Int
            ): CommMessage {
                val bytes = device.transport.readFrame(timeoutMs, expectedSize, silenceGapMs)
                val rsp = CommMessage(command = "CHAIN_RSP", payload = bytes, metadata = emptyMap())
                val scope = ModbusPluginScopes.scope(cfg, ProtocolState.READY, rsp)
                device.plugins.forEach { p -> runCatching { p.onReceive(scope) } }
                return rsp
            }
        }

        val all = mutableListOf<CommMessage>()
        DeviceStateCenter.updateState(cfg.deviceId, ProtocolState.BUSY)
        notifyState(device.plugins, cfg, ProtocolState.BUSY)

        try {
            plan.frames.forEachIndexed { idx, frame ->
                // 每步下发
                io.writeRaw(frame)
                // 交给策略按需阻塞读取/校验
                val r = policy.afterSendStep(idx, frame, io)
                if (r.received.isNotEmpty()) all += r.received
                if (!r.continueNext) return@forEachIndexed
                if (r.interFrameDelayMs > 0) delay(r.interFrameDelayMs.toLong())
            }
            all += io.readRaw(cfg.requestTimeoutMs, null, 0)

            DeviceStateCenter.updateState(cfg.deviceId, ProtocolState.READY)
            notifyState(device.plugins, cfg, ProtocolState.READY)
            return all
        } catch (t: Throwable) {
            DeviceStateCenter.updateState(cfg.deviceId, ProtocolState.ERROR)
            notifyState(device.plugins, cfg, ProtocolState.ERROR)
            val errScope = ModbusPluginScopes.scope(cfg, ProtocolState.ERROR)
            device.plugins.forEach { p -> runCatching { p.onError(errScope, t) } }
            logger.onError(cfg.deviceId, t)
            throw t
        }
    }
}
