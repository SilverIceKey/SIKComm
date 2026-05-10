package com.sik.comm.internal.transport

import android.util.Log
import com.sik.comm.CanConfig
import com.sik.comm.CommConfig
import com.sik.comm.internal.native.NativeCan

/**
 * SocketCAN Transport（JNI / socketcan）。
 *
 * 注意：read 时只返回 payload 字节数，frameId / flags 在 JNI 层消费但不向上暴露。
 * 如需 frameId，请在 CommChannel 之上再封装一层协议解析。
 */
internal class JniCanTransport : Transport {

    companion object {
        private const val TAG = "SIKComm-Transport-CAN"
        private const val DEFAULT_FRAME_ID = 0
        private const val DEFAULT_FLAGS = 0
    }

    override fun open(config: CommConfig): Long {
        val c = config as CanConfig
        val bitrate = c.bitrate
        if (bitrate != null) {
            NativeCan.bringUp(c.ifName, bitrate, c.fdMode)
        }
        val fd = NativeCan.open(c.ifName)
        if (fd <= 0L) {
            Log.e(TAG, "open failed: ifName=${c.ifName}, fd=$fd")
        }
        return fd
    }

    override fun close(handle: Long) {
        NativeCan.close(handle)
    }

    override fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        val frameIdArr = IntArray(1)
        val flagsArr = IntArray(1)
        return NativeCan.read(
            handle,
            frameIdArr,
            flagsArr,
            buffer,
            0,
            buffer.size,
            timeoutMs
        )
    }

    override fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        return NativeCan.write(
            handle,
            DEFAULT_FRAME_ID,
            DEFAULT_FLAGS,
            data,
            offset,
            length,
            timeoutMs
        )
    }
}
