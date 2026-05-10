package com.sik.comm.internal.transport

import com.sik.comm.CommConfig
import com.sik.comm.internal.native.NativeUsbHid
import com.sik.comm.UsbHidConfig

/**
 * USB-HID Transport（基于 Android UsbManager bulkTransfer）。
 */
internal class AndroidUsbHidTransport : Transport {

    override fun open(config: CommConfig): Long {
        return NativeUsbHid.open((config as UsbHidConfig).context, config)
    }

    override fun close(handle: Long) {
        NativeUsbHid.close(handle)
    }

    override fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        return NativeUsbHid.read(handle, buffer, timeoutMs)
    }

    override fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        return NativeUsbHid.write(handle, data, offset, length, timeoutMs)
    }
}
