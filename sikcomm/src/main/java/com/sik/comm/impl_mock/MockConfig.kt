package com.sik.comm.impl_mock

import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolType

/**
 * Mock 协议配置。
 *
 * 用于描述一个虚拟设备（无真实硬件）。
 * 典型用途：
 * - 联调测试
 * - 单元测试
 * - 压力测试
 */
open class MockConfig(
    deviceId: String
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.MOCK,
    enableMock = true
)
