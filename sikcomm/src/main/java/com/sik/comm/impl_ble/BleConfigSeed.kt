package com.sik.comm.impl_ble

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.plugin.CommPlugin
import java.util.UUID

/** 发现阶段的配置种子：不包含 deviceId，GATT 路由可空 */
data class BleConfigSeed(
    // 设备发现
    val whitelist: Set<String> = emptySet(),
    val deviceNameExact: String? = null,
    val deviceNamePrefix: String? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val manufacturerMask: ByteArray? = null,

    // GATT 路由（可空，连接时再校验）
    val service: UUID? = null,
    val writeChar: UUID? = null,
    val notifyChar: UUID? = null,

    // 链路参数
    val expectMtu: Int? = BleDefaults.EXPECT_MTU,
    val enableNotifyOnReady: Boolean = BleDefaults.ENABLE_NOTIFY_ON_READY,

    // 超时控制
    val connectTimeoutMs: Long = BleDefaults.CONNECT_TIMEOUT_MS,
    val discoverTimeoutMs: Long = BleDefaults.DISCOVER_TIMEOUT_MS,
    val subscribeTimeoutMs: Long = BleDefaults.SUBSCRIBE_TIMEOUT_MS,
    val writeTimeoutMs: Long = BleDefaults.WRITE_TIMEOUT_MS,
    val scanWindowMs: Long = BleDefaults.SCAN_WINDOW_MS,
    val totalScanBudgetMs: Long = BleDefaults.TOTAL_SCAN_BUDGET_MS,

    // 写入分片/兼容策略
    val preferWriteWithResponse: Boolean = BleDefaults.PREFER_WRITE_WITH_RESPONSE,
    val interChunkDelayMs: Long = BleDefaults.INTER_CHUNK_DELAY_MS,
    val maxInFlightChunks: Int = BleDefaults.MAX_IN_FLIGHT_CHUNKS,
    val maxRetriesPerChunk: Int = BleDefaults.MAX_RETRIES_PER_CHUNK,

    // 并发连接上限（全局池）
    val maxGlobalConnections: Int = BleDefaults.MAX_GLOBAL_CONNECTIONS,

    // 路由键（可选）
    val requestKeyOf: ((ByteArray) -> String)? = null,
    val notifyKeyOf: ((ByteArray) -> String)? = null,

    // 扩展
    val additionalPlugins: List<CommPlugin> = emptyList(),
    val additionalInterceptors: List<CommInterceptor> = emptyList()
)