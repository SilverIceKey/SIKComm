package com.sik.comm.internal.transport

import android.util.Log
import com.sik.comm.CommConfig
import com.sik.comm.NativeSerial
import com.sik.comm.SerialConfig

/**
 * 原生串口 Transport（JNI / termios）。
 */
internal class JniSerialTransport : Transport {

    companion object {
        private const val TAG = "SIKComm-Transport-Serial"
    }

    override fun open(config: CommConfig): Long {
        val c = config as SerialConfig
        val fd = NativeSerial.open(
            c.devicePath,
            c.baudRate,
            c.dataBits,
            c.stopBits,
            c.parity
        )
        if (fd <= 0L) {
            Log.e(TAG, "open failed: path=${c.devicePath}, fd=$fd")
        }
        return fd
    }

    override fun close(handle: Long) {
        NativeSerial.close(handle)
    }

    override fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        return NativeSerial.read(handle, buffer, 0, buffer.size, timeoutMs)
    }

    override fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        return NativeSerial.write(handle, data, offset, length, timeoutMs)
    }
}
