package com.sik.comm

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object NativeUsbHid {
    private const val TAG = "SIKComm-HID"

    private val idGen = AtomicLong(1)
    private val ports = ConcurrentHashMap<Long, Port>()

    private data class Port(
        val device: UsbDevice,
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val epIn: UsbEndpoint,
        val epOut: UsbEndpoint?, // 有些 HID 没 OUT
        val inMaxPacketSize: Int
    )

    fun listDevices(context: Context): List<UsbDevice> {
        val um = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.deviceList?.values?.toList().orEmpty()
    }

    fun findDevice(context: Context, matcher: UsbDeviceMatcher): UsbDevice? {
        return listDevices(context).firstOrNull { matcher.match(it) }
    }

    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val um = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.hasPermission(device)
    }

    fun open(context: Context, config: UsbHidConfig): Long {
        val app = context.applicationContext
        val um = app.getSystemService(Context.USB_SERVICE) as UsbManager

        val device = findDevice(app, config.deviceMatcher) ?: run {
            Log.e(TAG, "open: no matched device (id=${config.id})")
            return 0L
        }

        if (!um.hasPermission(device)) {
            Log.e(TAG, "open: no permission yet (id=${config.id})")
            return 0L
        }

        val conn = um.openDevice(device) ?: run {
            Log.e(TAG, "open: openDevice returned null (id=${config.id})")
            return 0L
        }

        val intf = device.getInterface(config.interfaceIndex)
        if (!conn.claimInterface(intf, true)) {
            Log.e(TAG, "open: claimInterface failed (id=${config.id})")
            try { conn.close() } catch (_: Throwable) {}
            return 0L
        }

        // ✅ endpoint 选择要严谨：优先 INTERRUPT IN
        val endpoints = (0 until intf.endpointCount).map { intf.getEndpoint(it) }

        val epInInt = endpoints.firstOrNull {
            it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_INT
        }
        val epInBulk = endpoints.firstOrNull {
            it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
        val epIn = epInInt ?: epInBulk

        val epOutInt = endpoints.firstOrNull {
            it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_INT
        }
        val epOutBulk = endpoints.firstOrNull {
            it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
        val epOut = epOutInt ?: epOutBulk

        if (epIn == null) {
            Log.e(TAG, "open: no IN endpoint found (id=${config.id})")
            try { conn.releaseInterface(intf) } catch (_: Throwable) {}
            try { conn.close() } catch (_: Throwable) {}
            return 0L
        }

        val handle = idGen.getAndIncrement()
        ports[handle] = Port(
            device = device,
            connection = conn,
            usbInterface = intf,
            epIn = epIn,
            epOut = epOut,
            inMaxPacketSize = epIn.maxPacketSize.coerceAtLeast(1)
        )

        Log.i(
            TAG,
            "open: success handle=$handle vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} " +
                    "cls=${intf.interfaceClass} if=${config.interfaceIndex} " +
                    "epIn(addr=${epIn.address},type=${epIn.type},max=${epIn.maxPacketSize}) " +
                    "epOut=${epOut?.let { "addr=${it.address},type=${it.type},max=${it.maxPacketSize}" } ?: "null"} " +
                    "(id=${config.id})"
        )

        return handle
    }

    fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        val port = ports[handle] ?: return -1

        // ✅ HID/interrupt 读：强制按 maxPacketSize 读，别拿 4096 去读
        val len = minOf(buffer.size, port.inMaxPacketSize)

        // interrupt endpoint 也是 bulkTransfer 读
        return port.connection.bulkTransfer(port.epIn, buffer, len, timeoutMs)
    }

    fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        val port = ports[handle] ?: return -1
        val epOut = port.epOut ?: return -2 // 没 OUT endpoint

        val slice = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        val len = minOf(slice.size, epOut.maxPacketSize.coerceAtLeast(1))
        return port.connection.bulkTransfer(epOut, slice, len, timeoutMs)
    }

    fun close(handle: Long) {
        val port = ports.remove(handle) ?: return
        try { port.connection.releaseInterface(port.usbInterface) } catch (_: Throwable) {}
        try { port.connection.close() } catch (_: Throwable) {}
        Log.i(TAG, "close: handle=$handle")
    }
}
