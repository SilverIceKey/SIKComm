package com.sik.comm.impl_tcp

import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolType

/**
 * TCP 协议配置。
 *
 * 用于描述一个 TCP 设备所需的基础连接参数：
 * - 主机地址
 * - 端口号
 * - 是否启用 KeepAlive
 */
open class TcpConfig(
    deviceId: String,
    val host: String,
    val port: Int,
    val keepAlive: Boolean = true
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.TCP
)
