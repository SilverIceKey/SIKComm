package com.sik.comm.impl_ble.scan

import android.os.SystemClock
import com.sik.comm.impl_ble.BleConfigSeed
import com.sik.comm.impl_ble.BleScanConfig
import com.sik.comm.impl_ble.ScanOptions

suspend fun ScanResolver.resolveKnownSeed(
    opts: ScanOptions,
    cfg: BleScanConfig,
    base: BleConfigSeed
): BleConfigSeed {
    require(opts.whitelist.isNotEmpty()) { "KNOWN_MAC_ONLY requires whitelist" }
    val deadline = SystemClock.elapsedRealtime() + cfg.totalBudgetMs
    while (SystemClock.elapsedRealtime() < deadline) {
        val batch = backend.scan(cfg.windowMs, opts)
        val mac = batch.firstOrNull { it.mac in opts.whitelist }?.mac
        if (mac != null) return base.copy(whitelist = setOf(mac))
    }
    error("Target MAC not seen within budget")
}