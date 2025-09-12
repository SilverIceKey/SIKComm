package com.sik.comm.impl_ble

import java.util.UUID

/** 由上层注入的 GATT 路由（库内不内置任何协议常量） */
data class GattRoute(
    val service: UUID,
    val writeChar: UUID,
    val notifyChar: UUID,
    /** 期望 MTU；平台实现可忽略或降级 */
    val requestMtu: Int? = 517
)
