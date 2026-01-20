package com.sik.comm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbConstants

/**
 * USB HID 配置（给 class=3 的设备用：HID 扫码枪/键盘类/其它 HID 设备）
 */
data class UsbHidConfig(
    override val id: String,
    val context: Context,

    /** 设备匹配（建议用 VID/PID 白名单，避免插 Hub/别的 HID 时选错） */
    val deviceMatcher: UsbDeviceMatcher = UsbDeviceMatcher.Any,

    /**
     * 选择哪个 interface（默认 0）
     * 你的设备日志显示 ifCount=1，所以 0 正好。
     */
    val interfaceIndex: Int = 0,

    /**
     * 读端点方向默认 IN；通常 HID 扫码枪会有一个 interrupt IN endpoint
     */
    val inDirection: Int = UsbConstants.USB_DIR_IN,

    override val readTimeoutMs: Int = 200,
    override val writeTimeoutMs: Int = 200,
) : CommConfig
