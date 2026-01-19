package com.sik.comm

import android.content.Context
import android.hardware.usb.UsbDevice

/**
 * USB-Serial 配置（对标 SerialConfig / CanConfig）
 */
data class UsbSerialConfig(
    override val id: String,
    val context: Context,

    /** 设备选择策略（默认 Any；常用是 VID/PID 白名单） */
    val deviceMatcher: UsbDeviceMatcher = UsbDeviceMatcher.Any,

    /**
     * 驱动族策略（粒度1）：
     * - Auto: 交给 felHR85 自动挑选（基于 VID/PID 表 & CDC 等）
     * - Prefer(x): 期望最终挑出来的驱动族是 x（不满足则直接失败）
     */
    val driverPolicy: UsbDriverPolicy = UsbDriverPolicy.Auto,

    /** 串口参数 */
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0, // 0=NONE（具体映射由 fel 内部处理）

    override val readTimeoutMs: Int = 200,
    override val writeTimeoutMs: Int = 200,
) : CommConfig

/**
 * USB 设备匹配器
 */
fun interface UsbDeviceMatcher {
    fun match(device: UsbDevice): Boolean

    data object Any : UsbDeviceMatcher {
        override fun match(device: UsbDevice): Boolean = true
    }

    data class VidPidWhitelist(
        val pairs: Set<Pair<Int, Int>>
    ) : UsbDeviceMatcher {
        override fun match(device: UsbDevice): Boolean =
            pairs.contains(device.vendorId to device.productId)
    }
}

/**
 * 粒度1：驱动族分类（CDC / CH34x / CP210x / FTDI / PL2303）
 */
enum class UsbDriverFamily {
    CDC,
    CH34X,
    CP210X,
    FTDI,
    PL2303
}

sealed class UsbDriverPolicy {
    data object Auto : UsbDriverPolicy()
    data class Prefer(val family: UsbDriverFamily) : UsbDriverPolicy()
}
