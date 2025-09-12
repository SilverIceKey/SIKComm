package com.sik.comm.impl_ble

import android.os.SystemClock
import com.sik.comm.impl_ble.android.AndroidBlePlatform
import com.sik.comm.impl_ble.android.ScanOptions

/** 已知 MAC：连续扫，命中即返（不休眠，工业场景） */
suspend fun waitForKnownMac(
    platform: AndroidBlePlatform,
    mac: String,
    timeoutMs: Long = 10_000,
    windowMs: Long = 1_200
): Pair<String, Int>? {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        // AndroidBlePlatformImpl 内部对 whitelist 已做命中短路
        val hit = platform.scan(windowMs, setOf(mac))
        if (hit.isNotEmpty()) return hit.first()
    }
    return null
}

/**
 * 未知 MAC：连续扫描判定“新出现”的设备。
 * - 先采一小段环境快照（ambient），记为 baseline；
 * - 后续窗口里，只要出现“未在 baseline 中”的候选，并且满足稳定性门槛就返回；
 * - 可加 RSSI/名字/UUID 等过滤，减少干扰。
 */
suspend fun waitForNewDevice(
    platform: AndroidBlePlatform,
    options: ScanOptions,              // 建议至少给 deviceNamePrefix 或 serviceUuids
    timeoutMs: Long = 12_000,          // 总预算
    ambientMs: Long = 800,             // 环境快照时长（通电前/刚通电立即采一轮）
    windowMs: Long = 800,              // 连续扫描窗口
    minRssi: Int = -85,                // 过滤远端噪声
    minSightings: Int = 2,             // 稳定性：至少被看到 N 次才确认是“新”
    notBeforeMs: Long = 0              // 若你知道通电到广播的最小延迟，填 start+delay
): Pair<String, Int>? {
    val start = SystemClock.elapsedRealtime()
    val deadline = start + timeoutMs

    // 1) 环境基线：只记录“符合过滤条件”的设备，避免把无关设备塞进 baseline
    val baseline = platform.scan(ambientMs, options).map { it.first }.toMutableSet()

    // 2) 连续扫描，找“新增 + 稳定”的目标
    val sightings = HashMap<String, Int>()
    val latestRssi = HashMap<String, Int>()
    val firstSeenAt = HashMap<String, Long>()

    while (SystemClock.elapsedRealtime() < deadline) {
        val batch = platform.scan(windowMs, options)
        val now = SystemClock.elapsedRealtime()

        for ((mac, rssi) in batch) {
            if (rssi < minRssi) continue
            if (mac in baseline) continue                   // 已在环境里，忽略
            if (notBeforeMs > 0 && now < notBeforeMs) continue // 早到的也忽略（防误判）

            // 记录第一次出现时间 + 次数 + 最新 RSSI
            firstSeenAt.putIfAbsent(mac, now)
            sightings[mac] = (sightings[mac] ?: 0) + 1
            latestRssi[mac] = rssi
        }

        // 满足稳定性门槛的候选（出现次数达到要求）
        val stable = sightings.filter { it.value >= minSightings }.keys
        if (stable.isNotEmpty()) {
            // 选 RSSI 最强的那一台返回（避免“炸”到别的）
            val best = stable.maxBy { latestRssi[it] ?: Int.MIN_VALUE }
            return best to (latestRssi[best] ?: -127)
        }

        // 没命中，下一轮继续（不休眠）
    }
    return null
}
