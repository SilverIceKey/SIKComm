package com.sik.comm.core.model

/**
 * 一次链式事务的“发送计划”：按顺序下发这些帧。
 * 每帧的 payload 必须是“底层可直接发送”的原始字节（已按对应协议编好）。
 * metadata 可携带每步的 timeout/expected/gap 等提示。
 */
data class TxPlan(
    val frames: List<CommMessage>,
    val txnId: Int = 0
)