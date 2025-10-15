package com.sik.comm.core.policy

import com.sik.comm.core.model.ChainStepResult
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.protocol.LinkIO

/**
 * 针对不同协议（单包 / 多包）的链路策略。
 * afterSendStep 会在每次写出一帧之后被调用，用来【阻塞等待并消费】响应帧，
 * 决定是否继续后续步骤，以及两步之间的间隔。
 */
interface ChainPolicy {
    suspend fun afterSendStep(
        stepIndex: Int,
        sent: CommMessage,
        io: LinkIO
    ): ChainStepResult
}