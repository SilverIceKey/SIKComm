package com.sik.comm.impl_ble

import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolType

/**
 * BLE 协议配置。
 *
 * 用于描述一个 BLE 设备所需的基础连接参数：
 * - Service UUID
 * - Write 特征 UUID
 * - Notify 特征 UUID
 * - MTU 配置
 * - 是否自动重连
 */
open class BleConfig(
    deviceId: String,
    val serviceUuid: String,
    val writeUuid: String,
    val notifyUuid: String,
    val mtu: Int = 500,
    val autoReconnect: Boolean = true
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.BLE
)
