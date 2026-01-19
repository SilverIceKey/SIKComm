package com.sik.comm

import android.hardware.usb.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class CdcAcmDriver : UsbSerialDriver {

    override val name: String = "CDC-ACM"

    override fun probe(device: UsbDevice): Boolean {
        // 粗探测：存在 CDC data interface 或 COMM interface
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                intf.interfaceClass == UsbConstants.USB_CLASS_COMM
            ) return true
        }
        return false
    }

    override fun open(device: UsbDevice, connection: UsbDeviceConnection, config: UsbSerialConfig): UsbSerialDriver.OpenedPort {
        val dataIntf = findDataInterface(device)
            ?: error("CDC-ACM: no data interface (class=CDC_DATA)")

        // claim 数据接口
        if (!connection.claimInterface(dataIntf, true)) {
            error("CDC-ACM: claimInterface failed")
        }

        val (epIn, epOut) = findBulkEndpoints(dataIntf)
            ?: error("CDC-ACM: bulk endpoints not found")

        // 设置 line coding + DTR/RTS
        setLineCoding(connection, dataIntf, config)
        setControlLineState(connection, dataIntf, dtr = true, rts = true)

        return UsbSerialDriver.OpenedPort(
            inEndpoint = epIn,
            outEndpoint = epOut,
            close = {
                try {
                    connection.releaseInterface(dataIntf)
                } catch (_: Throwable) {}
            }
        )
    }

    private fun findDataInterface(device: UsbDevice): UsbInterface? {
        // 优先 class=CDC_DATA(10)
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) return intf
        }
        // 兜底：有些设备把数据接口也标成 vendor，这就得靠 AUTO+其它 driver 了
        return null
    }

    private fun findBulkEndpoints(intf: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            when (ep.direction) {
                UsbConstants.USB_DIR_IN -> epIn = ep
                UsbConstants.USB_DIR_OUT -> epOut = ep
            }
        }
        return if (epIn != null && epOut != null) epIn!! to epOut!! else null
    }

    private fun setLineCoding(connection: UsbDeviceConnection, intf: UsbInterface, config: UsbSerialConfig) {
        // CDC-ACM line coding: 7 bytes
        // dwDTERate(4, little-endian), bCharFormat(1), bParityType(1), bDataBits(1)
        val stopBits = when (config.stopBits) {
            1 -> 0 // 1 stop bit
            2 -> 2 // 2 stop bits
            else -> 0
        }
        val parity = when (config.parity) {
            0 -> 0 // none
            1 -> 1 // odd
            2 -> 2 // even
            else -> 0
        }

        val buf = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(config.baudRate)
        buf.put(stopBits.toByte())
        buf.put(parity.toByte())
        buf.put(config.dataBits.toByte())
        val data = buf.array()

        val requestType = 0x21 // Host to device | Class | Interface
        val request = 0x20 // SET_LINE_CODING
        val value = 0
        val index = intf.id

        val r = connection.controlTransfer(requestType, request, value, index, data, data.size, 1000)
        if (r < 0) error("CDC-ACM: SET_LINE_CODING failed")
    }

    private fun setControlLineState(connection: UsbDeviceConnection, intf: UsbInterface, dtr: Boolean, rts: Boolean) {
        val requestType = 0x21
        val request = 0x22 // SET_CONTROL_LINE_STATE
        val value = (if (dtr) 1 else 0) or (if (rts) 2 else 0)
        val index = intf.id

        val r = connection.controlTransfer(requestType, request, value, index, null, 0, 1000)
        if (r < 0) error("CDC-ACM: SET_CONTROL_LINE_STATE failed")
    }
}
