package com.sik.comm.interceptors

import com.sik.comm.core.model.CommMessage

/**
 * Mock 规则：
 * - 当 predicate 返回 true 时，responder 生成模拟响应，拦截链路并直接返回
 * - 否则返回 null，交给链路继续处理
 *
 * 可用于：特定命令、特定设备、随机失败、固定延迟等场景
 */
data class MockRule(
    /** 是否命中本规则（基于待发送的 CommMessage 判断） */
    val predicate: (CommMessage) -> Boolean,
    /** 命中规则时如何构造模拟响应 */
    val responder: suspend (original: CommMessage) -> CommMessage,
    /** 人为延迟（毫秒），便于模拟真实设备的处理耗时 */
    val delayMillis: Long = 0L
)
