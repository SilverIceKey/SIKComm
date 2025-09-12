package com.sik.comm.impl_ble.android

import java.util.UUID

/** 扫描过滤项（不含任何业务常量，纯通用） */
data class ScanOptions(
    val whitelist: Set<String> = emptySet(),      // 仅接受这些 MAC（代码层 AND 校验）
    val deviceNameExact: String? = null,          // 完整本地名精确匹配
    val deviceNamePrefix: String? = null,         // 本地名前缀匹配（代码层 AND 校验）
    val serviceUuids: List<UUID> = emptyList(),   // 至少包含其中一个 Service UUID
    val manufacturerId: Int? = null,              // 厂商 ID 过滤（存在即可）
    val manufacturerData: ByteArray? = null,      // 厂商数据模式（可选）
    val manufacturerMask: ByteArray? = null       // 厂商数据掩码（可选）
)
