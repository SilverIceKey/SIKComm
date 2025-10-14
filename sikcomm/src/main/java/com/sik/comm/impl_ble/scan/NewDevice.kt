package com.sik.comm.impl_ble.scan

import com.sik.comm.impl_ble.BleConfigSeed

data class NewDevice(
    val mac: String,
    val name: String?,
    val sightings: Int,          // 目击次数
    val firstSeenMs: Long,       // 首次见到的 elapsedRealtime()
    val lastSeenMs: Long,        // 最近一次见到
    val lastRssi: Int            // 最近一次 RSSI
)

/** 单个新设备 → 种子（把 MAC 写入 whitelist；其它用 base 缺省） */
fun NewDevice.toSeed(base: BleConfigSeed): BleConfigSeed =
    base.copy(whitelist = setOf(mac))

/** 批量新设备 → 种子列表（库不做选择，全部交给上层挑） */
fun List<NewDevice>.toSeeds(base: BleConfigSeed): List<BleConfigSeed> =
    map { it.toSeed(base) }