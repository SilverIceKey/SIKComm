package com.sik.comm.core.protocol

import com.sik.comm.core.logger.DefaultProtocolLogger
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolConfig
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ProtocolManager {

    private val protocolMap: MutableMap<ProtocolType, Protocol> = mutableMapOf()
    private val deviceProtocolTypeMap: MutableMap<String, ProtocolType> = mutableMapOf()

    // 新增：设备完整配置缓存 & 连接态
    private val deviceConfigMap: MutableMap<String, ProtocolConfig> = mutableMapOf()
    private val deviceConnectedMap: MutableMap<String, Boolean> = mutableMapOf()

    // 新增：设备级串行锁（避免交叉帧）
    private val deviceMutex: MutableMap<String, Mutex> = mutableMapOf()

    // 缺省超时兜底（用户传 <=0 时使用）
    private const val DEFAULT_TIMEOUT_MS: Long = 5000

    fun register(type: ProtocolType, impl: Protocol) {
        protocolMap[type] = impl
    }

    fun bindDeviceConfig(config: ProtocolConfig) {
        deviceProtocolTypeMap[config.deviceId] = config.protocolType
        deviceConfigMap[config.deviceId] = config
        deviceMutex.getOrPut(config.deviceId) { Mutex() }
    }

    fun getProtocol(deviceId: String): Protocol {
        val type = deviceProtocolTypeMap[deviceId]
            ?: error("Device [$deviceId] is not bound to any ProtocolType.")
        return protocolMap[type]
            ?: error("Protocol for type [$type] not registered.")
    }

    private fun getDeviceDefaultTimeout(deviceId: String): Long {
        val cfg = deviceConfigMap[deviceId]
        // 若你的 ProtocolConfig 有更具体的超时字段，可在此读取
        return (cfg?.defaultTimeoutMs ?: 0L).takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
    }

    suspend fun send(deviceId: String, msg: CommMessage, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommMessage {
        val realTimeout = (timeoutMs.takeIf { it > 0 } ?: getDeviceDefaultTimeout(deviceId))
        val mutex = deviceMutex.getOrPut(deviceId) { Mutex() }
        return mutex.withLock {
            try {
                withTimeout(realTimeout) {
                    getProtocol(deviceId).send(deviceId, msg)
                }
            } catch (e: Exception) {
                DefaultProtocolLogger.onError(deviceId, e)
                throw e
            }
        }
    }

    fun connect(deviceId: String) {
        val proto = getProtocol(deviceId)
        // 幂等：已连就不重复
        if (deviceConnectedMap[deviceId] == true) return
        try {
            proto.connect(deviceId)
            deviceConnectedMap[deviceId] = true
        } catch (e: Exception) {
            DefaultProtocolLogger.onError(deviceId, e)
            throw e
        }
    }

    fun disconnect(deviceId: String) {
        val proto = getProtocol(deviceId)
        if (deviceConnectedMap[deviceId] != true) return
        try {
            proto.disconnect(deviceId)
        } catch (e: Exception) {
            DefaultProtocolLogger.onError(deviceId, e)
        } finally {
            deviceConnectedMap[deviceId] = false
        }
    }

    fun isConnected(deviceId: String): Boolean {
        return try {
            getProtocol(deviceId).isConnected(deviceId)
        } catch (_: Exception) {
            false
        }
    }
}
