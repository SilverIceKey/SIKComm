package com.sik.comm.impl_ble.scan

import android.os.SystemClock
import android.util.Log
import com.sik.comm.impl_ble.BleScanConfig
import com.sik.comm.impl_ble.ScanOptions

class ScanResolver(val backend: AndroidBleScanner) {

    suspend fun resolveKnown(opts: ScanOptions, cfg: BleScanConfig): String {
        val wl = opts.whitelist
        require(wl.isNotEmpty()) { "KNOWN_MAC_ONLY requires non-empty whitelist" }
        val deadline = SystemClock.elapsedRealtime() + cfg.totalBudgetMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val list = backend.scan(durationMs = cfg.windowMs, opts)
            val hit = list.firstOrNull { it.mac in wl }
            if (hit != null) return hit.mac
        }
        error("Target MAC not seen within budget")
    }

    /** 新设备发现：返回“所有符合条件”的候选，不做排序/裁决 */
    suspend fun discoverNewDevices(opts: ScanOptions, cfg: BleScanConfig): List<NewDevice> {
        // 1) 建基线：把当前场内的都视作“旧”
        val baseline = backend.scan(cfg.ambientMs, opts).toMutableSet()
        Log.i("扫描结果","${baseline.joinToString { "${it.name},${it.mac},${it.rssi}" }}")

        // 2) 收集新出现的
        val firstSeen = HashMap<String, Long>()
        val lastSeen = HashMap<String, Long>()
        val sightings = HashMap<String, Int>()
        val lastRssi = HashMap<String, Int>()
        val names = HashMap<String, String?>()

        val start = SystemClock.elapsedRealtime()
        val notBefore = start + cfg.notBeforeMs

        while (SystemClock.elapsedRealtime() - start < cfg.totalBudgetMs) {
            val batch = backend.scan(cfg.windowMs, opts)
            Log.i("扫描结果","${baseline.joinToString { "${it.name},${it.mac},${it.rssi}" }}")
            val now = SystemClock.elapsedRealtime()
            for (a in batch) {
                if (a.rssi < cfg.minRssi) continue
                if (a.mac in baseline.map { it.mac }) continue
                if (now < notBefore) continue

                if (firstSeen[a.mac] == null) firstSeen[a.mac] = now
                lastSeen[a.mac] = now
                sightings[a.mac] = (sightings[a.mac] ?: 0) + 1
                lastRssi[a.mac] = a.rssi
                names[a.mac] = a.name
            }
        }

        // 3) 只输出达到稳定门槛的（不排序，给全部）
        val out = ArrayList<NewDevice>()
        for ((mac, n) in sightings) {
            if (n >= cfg.minSightings) {
                out += NewDevice(
                    mac = mac,
                    name = names[mac],
                    sightings = n,
                    firstSeenMs = firstSeen[mac] ?: 0L,
                    lastSeenMs = lastSeen[mac] ?: 0L,
                    lastRssi = lastRssi[mac] ?: Int.MIN_VALUE
                )
            }
        }
        if (out.isEmpty()) error("No new device matched within budget")
        return out
    }
}