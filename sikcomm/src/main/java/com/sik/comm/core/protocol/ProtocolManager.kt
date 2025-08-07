package com.sik.comm.core.protocol

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolConfig

/**
 * ProtocolManager 是所有协议实例的统一调度中心。
 * 它根据设备配置（ProtocolConfig）分配协议实例，
 * 并负责维护设备的连接状态和收发操作。
 */
object ProtocolManager {

    /** 协议实例注册表，按协议类型存储 */
    private val protocolMap: MutableMap<ProtocolType, Protocol> = mutableMapOf()

    /** 每个设备 ID 对应的协议类型（根据初始化配置决定） */
    private val deviceProtocolTypeMap: MutableMap<String, ProtocolType> = mutableMapOf()

    /**
     * 注册某一协议类型的实现类（在应用初始化时调用）。
     *
     * @param type 协议类型
     * @param impl 协议实现类实例（通常为单例）
     */
    fun register(type: ProtocolType, impl: Protocol) {
        protocolMap[type] = impl
    }

    /**
     * 初始化设备连接配置。
     * 应在设备首次接入前调用，决定其使用的协议类型。
     *
     * @param config 协议配置（包含设备 ID 和协议类型）
     */
    fun bindDeviceConfig(config: ProtocolConfig) {
        deviceProtocolTypeMap[config.deviceId] = config.protocolType
    }

    /**
     * 获取指定设备当前使用的协议实现类。
     *
     * @param deviceId 设备唯一 ID
     * @return 协议实现类实例
     * @throws IllegalStateException 若设备尚未绑定协议类型或协议未注册
     */
    fun getProtocol(deviceId: String): Protocol {
        val type = deviceProtocolTypeMap[deviceId]
            ?: error("Device [$deviceId] is not bound to any ProtocolType.")
        return protocolMap[type]
            ?: error("Protocol for type [$type] not registered.")
    }

    /**
     * 向指定设备发送消息。
     *
     * @param deviceId 设备 ID
     * @param msg 消息体
     * @return 响应消息
     */
    suspend fun send(deviceId: String, msg: CommMessage): CommMessage {
        return getProtocol(deviceId).send(deviceId, msg)
    }

    /**
     * 主动连接指定设备。
     */
    fun connect(deviceId: String) {
        getProtocol(deviceId).connect(deviceId)
    }

    /**
     * 主动断开设备连接。
     */
    fun disconnect(deviceId: String) {
        getProtocol(deviceId).disconnect(deviceId)
    }

    /**
     * 判断设备是否已连接。
     */
    fun isConnected(deviceId: String): Boolean {
        return getProtocol(deviceId).isConnected(deviceId)
    }
}
