package com.sik.comm.impl_can

/**
 * NodeId 工具类
 *
 * 提供拨码开关 NodeId 组合生成工具。
 * 可以指定最大位数 maxBits（默认为 8）。
 */
object NodeIdUtils {

    /**
     * 挂起版本：顺序枚举所有 NodeId 掩码
     *
     * @param maxBits 拨码位数，默认 8
     * @param emit 对每个掩码执行的挂起回调
     */
    suspend fun scanRange(maxBits: Int = 8, emit: suspend (Int) -> Unit) {
        for (k in 1..maxBits) {
            generateKBitMasks(k, maxBits, emit)
        }
    }

    /**
     * 非挂起版本：返回 Sequence，可直接 for-each
     *
     * @param maxBits 拨码位数，默认 8
     */
    fun scanRangeSeq(maxBits: Int = 8): Sequence<Int> = sequence {
        for (k in 1..maxBits) {
            yieldAll(generateKBitMasksSeq(k, maxBits))
        }
    }

    /** 生成 k 个置位的组合（挂起版本） */
    private suspend fun generateKBitMasks(
        k: Int,
        n: Int,
        emit: suspend (Int) -> Unit
    ) {
        val idx = IntArray(k) { it }
        while (true) {
            emit(maskFromIdx(idx))
            var pos = k - 1
            while (pos >= 0 && idx[pos] == pos + n - k) pos--
            if (pos < 0) break
            idx[pos]++
            for (j in pos + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }

    /** 生成 k 个置位的组合（Sequence 版本） */
    private fun generateKBitMasksSeq(k: Int, n: Int): Sequence<Int> = sequence {
        val idx = IntArray(k) { it }
        while (true) {
            yield(maskFromIdx(idx))
            var pos = k - 1
            while (pos >= 0 && idx[pos] == pos + n - k) pos--
            if (pos < 0) break
            idx[pos]++
            for (j in pos + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }

    /** 工具方法：由索引组合生成掩码 */
    private fun maskFromIdx(idx: IntArray): Int {
        var mask = 0
        for (i in idx) mask = mask or (1 shl i)
        return mask
    }
}