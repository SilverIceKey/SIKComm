package com.sik.comm.internal.pipeline

import com.sik.comm.CommReceiver

/**
 * 接收数据处理阶段接口。
 *
 * 每个 stage 接收原始字节，处理后决定：
 * - 直接丢弃
 * - 加工后传给下游 [downstream]
 * - 缓存等待更多数据
 */
internal fun interface PipelineStage {

    /**
     * @param data       原始字节数组（实现可复用 buffer）
     * @param offset     有效数据起始下标
     * @param length     有效数据长度
     * @param downstream 下游接收者（可能是另一个 stage 或最终 CommReceiver）
     */
    fun process(data: ByteArray, offset: Int, length: Int, downstream: CommReceiver)
}
