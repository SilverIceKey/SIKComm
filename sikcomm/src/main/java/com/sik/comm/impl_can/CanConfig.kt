package com.sik.comm.impl_can

import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolType

/**
 * CAN 协议配置。
 *
 * 用于描述一个 CAN 通信通道所需的参数：
 * - 通道号（channel）
 * - 波特率
 */
open class CanConfig(
    deviceId: String,
    val channel: Int,
    val baudRate: Int
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.CAN
)
