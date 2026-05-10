package com.sik.comm.internal.transport

import com.sik.comm.CommConfig
import com.sik.comm.internal.native.NativeUsbSerial
import com.sik.comm.UsbSerialConfig

/**
 * USB-Serial Transport（基于 felHR85/UsbSerial）。
 */
internal class FelUsbSerialTransport : Transport {

    override fun open(config: CommConfig): Long {
        return NativeUsbSerial.open((config as UsbSerialConfig).context, config)
    }

    override fun close(handle: Long) {
        NativeUsbSerial.close(handle)
    }

    override fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        return NativeUsbSerial.read(handle, buffer, timeoutMs)
    }

    override fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        return NativeUsbSerial.write(handle, data, offset, length, timeoutMs)
    }
}
