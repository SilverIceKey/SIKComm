package com.sik.comm.impl_can

/**
 * 辅助方法：大小端读写 / SDO 数据域填充。
 */
object CanUtils {

    fun putU16LE(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    fun getU16LE(src: ByteArray, off: Int): Int {
        val lo = src[off].toInt() and 0xFF
        val hi = src[off + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    fun putU32LE(dst: ByteArray, off: Int, v: Long) {
        dst[off]     = (v and 0xFF).toByte()
        dst[off + 1] = ((v ushr 8) and 0xFF).toByte()
        dst[off + 2] = ((v ushr 16) and 0xFF).toByte()
        dst[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    fun getU32LE(src: ByteArray, off: Int): Long {
        val b0 = src[off].toLong() and 0xFF
        val b1 = src[off + 1].toLong() and 0xFF
        val b2 = src[off + 2].toLong() and 0xFF
        val b3 = src[off + 3].toLong() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}
