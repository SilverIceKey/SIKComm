package com.sik.comm.impl_ble

import com.sik.comm.impl_ble.android.AndroidBlePlatform
import com.sik.comm.impl_ble.android.ScanOptions

suspend fun scanAddedSince(
    platform: AndroidBlePlatform,
    durationMs: Long,
    baseline: Set<String>,
    options: ScanOptions
): List<Pair<String, Int>> {
    return platform.scan(durationMs, options).filter { it.first !in baseline }
}
