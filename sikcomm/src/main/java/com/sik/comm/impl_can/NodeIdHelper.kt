package com.sik.comm.impl_can

/**
 * NodeId 枚举工具：
 * - 方式A：线性递增 0x01..0xFF
 * - 方式B：按置位个数递增 + 位索引字典序（组合序）
 */
object NodeIdHelper {

    private const val MAX_BITS = 8
    private const val MAX_MASK = (1 shl MAX_BITS) - 1 // 0xFF

    /**
     * 方式A：线性枚举（默认 0x01..0xFF）
     * @param startInclusive 起始（含），会被裁剪到 [1, 0xFF]
     * @param endInclusive   结束（含），会被裁剪到 [1, 0xFF]
     */
    suspend fun scanRangeLinear(
        emit: suspend (Int) -> Unit,
        startInclusive: Int = 1,
        endInclusive: Int = MAX_MASK
    ) {
        val start = (startInclusive and MAX_MASK).coerceIn(1, MAX_MASK)
        val end = (endInclusive and MAX_MASK).coerceIn(1, MAX_MASK)
        require(start <= end) { "start must be <= end (within 1..$MAX_MASK)" }
        for (id in start..end) emit(id)
    }

    /**
     * 方式B：按置位个数递增（1..8），同一置位个数内按“位索引组合字典序”。
     * 输出范围：0x01..0xFF，且无 0。
     */
    suspend fun scanRangeBySetBits(emit: suspend (Int) -> Unit) {
        for (k in 1..MAX_BITS) {
            generateKBitMasks(k, MAX_BITS, emit)
        }
    }

    /** 生成固定置位个数 k 的所有掩码（组合字典序）。*/
    private suspend fun generateKBitMasks(
        k: Int,
        n: Int,
        emit: suspend (Int) -> Unit
    ) {
        val idx = IntArray(k) { it } // [0,1,2,...,k-1]
        while (true) {
            var mask = 0
            for (i in 0 until k) mask = mask or (1 shl idx[i])
            emit(mask)

            var pos = k - 1
            while (pos >= 0 && idx[pos] == pos + n - k) pos--
            if (pos < 0) break
            idx[pos]++
            for (j in pos + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }

    /** 小工具：线性下一个（到头回 0x01） */
    fun nextAfterLinear(cur: Int): Int {
        val x = cur and MAX_MASK
        return if (x in 1 until MAX_MASK) x + 1 else 1
    }

    /** 小工具：格式化成 0xNN */
    fun toHex(id: Int): String = "0x%02X".format(id and MAX_MASK)
}
