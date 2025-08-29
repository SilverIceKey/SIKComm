package com.sik.comm.impl_can

/**
 * 统一的 metadata Key 字典。
 * 注意：TIMEOUT 与你现在的实现保持一致，使用 "timeoutMs" 作为键名。
 */
object MetaKeys {
    const val NODE_ID   = "nodeId"
    const val INDEX     = "index"
    const val SUBINDEX  = "subIndex"
    const val CAN_ID    = "canId"
    const val SIZE      = "size"
    const val TIMEOUT   = "timeoutMs"
    const val IS_READ   = "isRead"

    // 新增：SDO 异常的 abort code（CANopen 标准 32bit 错误码）
    const val ABORT     = "abortCode"
}
