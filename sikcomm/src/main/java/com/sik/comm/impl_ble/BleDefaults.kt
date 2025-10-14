package com.sik.comm.impl_ble

object BleDefaults {
    // 超时
    const val CONNECT_TIMEOUT_MS: Long = 15_000
    const val DISCOVER_TIMEOUT_MS: Long = 10_000
    const val SUBSCRIBE_TIMEOUT_MS: Long = 3_000
    const val WRITE_TIMEOUT_MS: Long = 5_000
    const val SCAN_WINDOW_MS: Long = 1_000
    const val TOTAL_SCAN_BUDGET_MS: Long = 8_000

    // 链路
    const val EXPECT_MTU: Int = 500
    const val ENABLE_NOTIFY_ON_READY: Boolean = true
    const val PREFER_WRITE_WITH_RESPONSE: Boolean = true
    const val INTER_CHUNK_DELAY_MS: Long = 0
    const val MAX_IN_FLIGHT_CHUNKS: Int = 1
    const val MAX_RETRIES_PER_CHUNK: Int = 0

    // 连接并发
    const val MAX_GLOBAL_CONNECTIONS: Int = 2

    // deviceId 缺省生成策略（不强制业务传）
    fun defaultDeviceId(mac: String?, name: String?): String =
        when {
            !mac.isNullOrBlank() -> mac
            !name.isNullOrBlank() -> "ble-${name}"
            else -> "ble-${System.currentTimeMillis()}"
        }
}