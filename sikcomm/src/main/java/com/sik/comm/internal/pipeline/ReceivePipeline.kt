package com.sik.comm.internal.pipeline

import com.sik.comm.CommReceiver

/**
 * 接收数据处理管道。
 *
 * 将多个 [PipelineStage] 串联，数据按顺序流经每个 stage，最终交给业务 [finalReceiver]。
 */
internal class ReceivePipeline(
    private val stages: List<PipelineStage>,
    private val finalReceiver: CommReceiver?
) : CommReceiver {

    override fun onBytesReceived(data: ByteArray, offset: Int, length: Int) {
        if (stages.isEmpty()) {
            finalReceiver?.onBytesReceived(data, offset, length)
            return
        }
        // 构建调用链
        val chain = buildChain(0)
        chain(data, offset, length)
    }

    private fun buildChain(index: Int): (ByteArray, Int, Int) -> Unit {
        if (index >= stages.size) {
            return { d, o, l -> finalReceiver?.onBytesReceived(d, o, l) }
        }
        val stage = stages[index]
        val next = buildChain(index + 1)
        val downstream = CommReceiver { d, o, l -> next(d, o, l) }
        return { d, o, l -> stage.process(d, o, l, downstream) }
    }
}
