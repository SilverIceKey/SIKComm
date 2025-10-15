package com.sik.comm.impl_modbus

import com.sik.comm.core.model.ChainStepResult
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.policy.ChainPolicy
import com.sik.comm.core.protocol.LinkIO

/**
 * 单包策略：发一帧，阻塞收一帧；超时/校验失败抛异常。
 * 校验钩子可对响应进行协议级检查（比如功能码、CRC、业务码）。
 */
class SingleExchangePolicy(
    private val timeoutMs: Int = 3000,
    private val silenceGapMs: Int = 30,
    private val expectedSize: Int? = null,
    private val validate: (sent: CommMessage, rsp: CommMessage) -> Unit = { _, _ -> }
) : ChainPolicy {
    override suspend fun afterSendStep(
        stepIndex: Int,
        sent: CommMessage,
        io: LinkIO
    ): ChainStepResult {
        val rsp = io.readRaw(timeoutMs, expectedSize, silenceGapMs)
        validate(sent, rsp)
        return ChainStepResult(received = listOf(rsp), continueNext = false)
    }
}
