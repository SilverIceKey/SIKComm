package com.sik.comm.impl_can

/**
 * 请求-应答匹配键：按 (nodeId, index, subIndex) 匹配。
 */
data class CanRequestKey(
    val nodeId: Int,
    val index: Int,
    val subIndex: Int
)
