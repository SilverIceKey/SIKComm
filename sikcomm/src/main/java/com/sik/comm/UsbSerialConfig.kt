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

    /**
     * 扫码枪“行为对齐 demo”的拼包策略：
     * - 400ms 内多段数据拼接成一次回调
     * - 超过 3s 清空旧数据，避免遗留
     * - 可选剔除 \r\n（13/10）
     *
     * 说明：
     * - 该策略开启后，UsbSerialChannelImpl 上抛给 receiver 的将不再是“原始分段 chunk”，
     *   而是“按策略拼好的完整二维码文本（UTF-8）对应的字节数组”。
     * - 如果你想保留原始字节流语义，请保持为 null。
     */
    val qrAssemblePolicy: QrAssemblePolicy? = null,

    override val readTimeoutMs: Int = 200,
    override val writeTimeoutMs: Int = 200,
) : CommConfig

/**
 * 扫码枪拼包策略（严格对齐 demo：UsbService.mCallback.onReceivedData）
 */
data class QrAssemblePolicy(
    /** 400ms 内返回的分段二维码拼接成一条 */
    val mergeWindowMs: Long = 400L,
    /** 超过 3s 清空旧数据 */
    val resetTimeoutSeconds: Int = 3,
    /** 剔除 \r(13) 与 \n(10) */
    val dropCrLf: Boolean = true,
)

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

    /**
     * demo 里的扫码枪 VID/PID 白名单（UsbService#findSerialPortDevice / usbReceiver）。
     *
     * 对应列表：
     * - 0x27DD:0x0002
     * - 0x0103:0x6061
     * - 0x26F1:0x5650
     * - 0x26F1:0xD001
     * - 0x0483:0x5740
     * - 0x26F1:0x8802
     * - 0x152A:0x880F
     * - 0x1EAB:0x1A06
     */
    data object QrScannerDemoWhitelist : UsbDeviceMatcher {
        private val whitelist: Set<Pair<Int, Int>> = setOf(
            0x27DD to 0x0002,
            0x0103 to 0x6061,
            0x26F1 to 0x5650,
            0x26F1 to 0xD001,
            0x0483 to 0x5740,
            0x26F1 to 0x8802,
            0x152A to 0x880F,
            0x1EAB to 0x1A06,
        )

        override fun match(device: UsbDevice): Boolean =
            whitelist.contains(device.vendorId to device.productId)
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
