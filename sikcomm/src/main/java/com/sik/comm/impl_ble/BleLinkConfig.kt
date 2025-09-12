package com.sik.comm.impl_ble

/** 纯链路配置（不含任何业务/协议字段） */
data class BleLinkConfig(
    val maxGlobalConnections: Int = 2,
    val advertiseWaitInitialMs: Long = 0,     // 前几轮不休息
    val advertiseWaitMaxMs: Long = 0,         // 一直不休息（持续扫）
    val scanOnceMs: Long = 3000,              // 单次窗口拉长
    val connectTimeoutMs: Long = 15_000,
    val discoverTimeoutMs: Long = 10_000,
    val writeTimeoutMs: Long = 5_000,
    val inboxCapacity: Int = 256
)

