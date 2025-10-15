package com.sik.comm.core.model


/** 执行一步后的结果 */
data class ChainStepResult(
    val received: List<CommMessage> = emptyList(), // 本步收到的帧（0..N）
    val continueNext: Boolean = true,              // 是否继续下一步
    val interFrameDelayMs: Int = 0                 // 下一步发送前的间隔
)