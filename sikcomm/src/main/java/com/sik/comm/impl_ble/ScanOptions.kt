package com.sik.comm.impl_ble

import java.util.UUID

data class ScanOptions(
    val whitelist: Set<String> = emptySet(),
    val deviceNameExact: String? = null,
    val deviceNamePrefix: String? = null,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val manufacturerMask: ByteArray? = null
)