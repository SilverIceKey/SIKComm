package com.sik.comm.impl_ble

import com.sik.comm.impl_ble.android.AndroidBlePlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure link layer: wait-for-advertising → connect → discover → subscribe → ready.
 * Android-only platform is injected via AndroidBlePlatform.
 */
class BleConnection(
    private val platform: AndroidBlePlatform,
    private val pool: ConnectionPool,
    private val cfg: BleLinkConfig
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    @Volatile private var mac: String = ""
    @Volatile internal var onNotify: ((ByteArray) -> Unit)? = null

    /** Block (suspend) until target MAC is seen advertising; exponential backoff with jitter. */
    private suspend fun awaitAdvertising(targetMac: String) {
        var wait = cfg.advertiseWaitInitialMs
        while (true) {
            val seen = platform.scan(cfg.scanOnceMs, setOf(targetMac))
            if (seen.any { it.first.equals(targetMac, ignoreCase = true) }) return
            wait = min((wait * 1.25).toLong(), cfg.advertiseWaitMaxMs)
        }
    }

    /** Ensure READY. Waiting for advertisements never consumes a pool permit. */
    suspend fun ensureReady(targetMac: String, route: GattRoute) {
        if (_ready.value && mac.equals(targetMac, true)) return
        mac = targetMac
        awaitAdvertising(mac)
        pool.withPermit {
            platform.connectAndDiscover(
                mac = mac,
                route = route,
                onNotify = { bytes -> onNotify?.invoke(bytes) },
                connectTimeoutMs = cfg.connectTimeoutMs,
                discoverTimeoutMs = cfg.discoverTimeoutMs
            )
            _ready.value = true
        }
    }

    fun currentMtu(): Int = platform.currentMtu()

    suspend fun write(route: GattRoute, payload: ByteArray) {
        platform.write(route, payload, cfg.writeTimeoutMs)
    }

    suspend fun disconnect() {
        runCatching { platform.disconnect() }
        _ready.value = false
    }
}