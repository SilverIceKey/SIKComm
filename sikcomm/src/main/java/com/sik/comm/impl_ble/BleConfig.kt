package com.sik.comm.impl_ble

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.protocol.ProtocolType
import java.util.UUID

/**
 * BLE 协议配置（纯通用、无业务）。
 * - 扫描/连接/发现/订阅/写入/分片/重试/兼容策略均可配置
 * - 可选请求-应答路由键提取器（匿名函数）
 */
open class BleConfig(
    deviceId: String,

    // —— 设备发现 —— //
    val whitelist: Set<String> = emptySet(),           // 目标 MAC 白名单（可为空）
    val deviceNameExact: String? = null,
    val deviceNamePrefix: String? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val manufacturerMask: ByteArray? = null,

    // —— GATT 路由 —— //
    val service: UUID,
    val writeChar: UUID,
    val notifyChar: UUID,

    // —— 链路参数 —— //
    val expectMtu: Int? = 185,             // 期望 MTU；null 表示不主动请求
    val enableNotifyOnReady: Boolean = true,

    // —— 超时控制 —— //
    val connectTimeoutMs: Long = 15_000,
    val discoverTimeoutMs: Long = 10_000,
    val subscribeTimeoutMs: Long = 3_000,
    val writeTimeoutMs: Long = 5_000,
    val scanWindowMs: Long = 1000,
    val totalScanBudgetMs: Long = 8_000,

    // —— 写入分片/兼容策略 —— //
    val preferWriteWithResponse: Boolean = true,  // 低性能模块可改为 false（NO_RESPONSE）
    val interChunkDelayMs: Long = 0,              // 低性能模块可插入片间延时
    val maxInFlightChunks: Int = 1,               // 一般保持 1，部分模块可放宽
    val maxRetriesPerChunk: Int = 0,              // 失败重试次数（谨慎使用）

    // —— 并发连接上限（全局池） —— //
    val maxGlobalConnections: Int = 2,

    // —— 可选路由键提取器（通用请求-应答匹配；无需业务常量） —— //
    val requestKeyOf: ((ByteArray) -> String)? = null,    // 对发送帧提取 key
    val notifyKeyOf: ((ByteArray) -> String)? = null,     // 对通知帧提取 key

    // —— 扩展 —— //
    override val additionalPlugins: List<CommPlugin> = emptyList(),
    override val additionalInterceptors: List<CommInterceptor> = emptyList()
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.BLE,
    enableMock = false
)