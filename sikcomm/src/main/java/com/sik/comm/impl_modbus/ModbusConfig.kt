package com.sik.comm.impl_modbus

import com.sik.comm.codec.Crc16ModbusCodec
import com.sik.comm.core.codec.MessageCodec
import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.protocol.ProtocolType

/**
 * Modbus 协议配置。
 *
 * 可用于 RS485 或 TCP 封装的 Modbus。
 * 包含 Modbus 所需的基础参数：
 * - 串口路径 / TCP 地址
 * - 波特率 / 端口
 * - 数据位 / 停止位 / 校验位
 */
open class ModbusConfig(
    deviceId: String,
    val portName: String,
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Char = 'N',
    val codec: MessageCodec = Crc16ModbusCodec(),
    val defaultUnitId: Int? = 1,
    val requestTimeoutMs: Int = 3000,
    val responseGapMs: Int = 30,
    val connectTimeoutMs: Int = 5000,
    override val additionalPlugins: List<CommPlugin> = emptyList(),
    override val additionalInterceptors: List<CommInterceptor> = emptyList(),
    enableMock: Boolean = false
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.RS485,
    enableMock = enableMock
)
