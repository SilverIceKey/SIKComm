package com.sik.comm

/**
 * 串口通道配置（适用于 RS232 / RS485 / USB-Serial）。
 *
 * 这里只定义参数，不做任何逻辑。
 * 串口“上电”默认由上层负责，本框架只负责 open/close + 收发。
 */
data class SerialConfig(
    override val id: String,
    val devicePath: String,          // 如: "/dev/ttyS1" / "/dev/ttyUSB0"
    val baudRate: Int,               // 波特率
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0,             // 0: None, 1: Odd, 2: Even ... 具体枚举可以上层再封装
    override val readTimeoutMs: Int = 500,
    override val writeTimeoutMs: Int = 500,
    val extra: Map<String, Any?> = emptyMap() // 预留扩展字段
) : CommConfig
