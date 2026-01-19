package com.sik.comm

/**
 * 截取（含范围保护）
 */
fun ByteArray.sliceFast(start: Int, end: Int): ByteArray {
    require(start in 0..end && end <= size) { "Invalid slice range" }
    val len = end - start
    return ByteArray(len).also {
        System.arraycopy(this, start, it, 0, len)
    }
}