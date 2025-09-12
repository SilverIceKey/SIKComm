package com.sik.comm.impl_ble.android

import com.sik.comm.impl_ble.GattRoute

interface AndroidBlePlatform {
    /** 推荐的新接口：带过滤选项 */
    suspend fun scan(durationMs: Long, options: ScanOptions): List<Pair<String, Int>>

    /** 兼容旧接口：只用 MAC 白名单 */
    suspend fun scan(
        durationMs: Long,
        whitelist: Set<String> = emptySet()
    ): List<Pair<String, Int>> =
        scan(durationMs, ScanOptions(whitelist = whitelist))

    suspend fun connectAndDiscover(
        mac: String,
        route: GattRoute,
        onNotify: (ByteArray) -> Unit,
        connectTimeoutMs: Long,
        discoverTimeoutMs: Long
    )

    suspend fun write(route: GattRoute, payload: ByteArray, timeoutMs: Long)
    suspend fun disconnect()
    fun currentMtu(): Int
}
