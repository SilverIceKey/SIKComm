package com.sik.comm.impl_modbus

import com.sik.comm.core.model.ChainStepResult
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.policy.ChainPolicy
import com.sik.comm.core.protocol.LinkIO

/**
 * 每发一步就期待一个 ACK（例如指纹模组的 01/02…/08 → 每步收 07）。
 * 用法：构 TxPlan(frames= 3/4/N 联包)，然后 policy 指定:
 *  - isAck:      如何识别“是 ACK 帧”
 *  - ackTimeout: 每步等 ACK 的超时
 *  - gap:        步与步之间的发送间隔
 *  - expected:   可选的“预期长度”，可用来加速 readRaw 返回
 */
class AckPerStepPolicy(
    private val ackTimeoutMs: Int = 3000,
    private val interFrameGapMs: Int = 0,
    private val expectedSizeHint: (stepIndex: Int, sent: CommMessage) -> Int? = { _, _ -> null },
    private val isAck: (CommMessage) -> Boolean,
    private val validateAck: (stepIndex: Int, sent: CommMessage, ack: CommMessage) -> Unit = { _, _, _ -> }
) : ChainPolicy {
    override suspend fun afterSendStep(
        stepIndex: Int,
        sent: CommMessage,
        io: LinkIO
    ): ChainStepResult {
        val expected = expectedSizeHint(stepIndex, sent)
        val ack = io.readRaw(
            timeoutMs = ackTimeoutMs,
            expectedSize = expected,
            silenceGapMs = interFrameGapMs.coerceAtLeast(1)
        )
        require(isAck(ack)) { "Unexpected frame (not ACK) at step=$stepIndex" }
        validateAck(stepIndex, sent, ack)
        return ChainStepResult(
            received = listOf(ack),
            continueNext = true,
            interFrameDelayMs = interFrameGapMs
        )
    }
}
