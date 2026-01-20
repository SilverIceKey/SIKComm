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

fun ByteArray.toHex(lowercase: Boolean = false): String {
    val hexChars = if (lowercase) "0123456789abcdef" else "0123456789ABCDEF"
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = hexChars[v ushr 4]
        out[i * 2 + 1] = hexChars[v and 0x0F]
    }
    return String(out)
}