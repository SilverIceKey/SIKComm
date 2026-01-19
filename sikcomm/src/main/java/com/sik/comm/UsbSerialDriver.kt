package com.sik.comm

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint

internal interface UsbSerialDriver {
    val name: String

    /**
     * 是否支持该设备（用于 AUTO 探测）
     */
    fun probe(device: UsbDevice): Boolean

    /**
     * 打开并返回数据 endpoints。
     * - 必须 claimInterface
     * - 必须完成 line coding 等初始化
     */
    fun open(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        config: UsbSerialConfig
    ): OpenedPort

    data class OpenedPort(
        val inEndpoint: UsbEndpoint,
        val outEndpoint: UsbEndpoint,
        val close: () -> Unit
    )
}
