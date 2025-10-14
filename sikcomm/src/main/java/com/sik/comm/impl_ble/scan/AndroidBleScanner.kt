package com.sik.comm.impl_ble.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import com.sik.comm.impl_ble.ScanOptions
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class AndroidBleScanner(private val context: Context) {
    private val btMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter get() = btMgr.adapter

    data class Adv(val mac: String, val name: String?, val rssi: Int)

    companion object {
        @Volatile
        private var lastStopElapsedMs: Long = 0L
        private const val COOLDOWN_MS = 3000L // 避免频繁启停
    }

    @OptIn(InternalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun scan(durationMs: Long, opts: ScanOptions): List<Adv> =
        suspendCancellableCoroutine { cont ->
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                cont.resume(emptyList()); return@suspendCancellableCoroutine
            }

            val now = SystemClock.elapsedRealtime()
            val wait = (lastStopElapsedMs + COOLDOWN_MS - now).coerceAtLeast(0L)

            val finished = AtomicBoolean(false)
            var started = false
            var stopTimer: Timer? = null
            val startTimer = Timer()
            var cb: ScanCallback? = null                      // ✅ 先声明成可空

            val seen = LinkedHashMap<String, Adv>()

            fun finishOnce(result: List<Adv>) {               // ✅ 可在 cb 之前定义
                if (!finished.compareAndSet(false, true)) return
                if (started) runCatching { scanner.stopScan(cb!!) } // 或者用 cb?.let { ... }
                lastStopElapsedMs = SystemClock.elapsedRealtime()
                runCatching { startTimer.cancel() }
                runCatching { stopTimer?.cancel() }
                val token = cont.tryResume(result) { }
                if (token != null) cont.completeResume(token)
            }

            fun buildFilters(): List<ScanFilter> = mutableListOf<ScanFilter>().apply {
                opts.deviceNameExact?.let { add(ScanFilter.Builder().setDeviceName(it).build()) }
                opts.serviceUuids.forEach {
                    add(
                        ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
                    )
                }
                if (isEmpty() && opts.whitelist.isNotEmpty()) {
                    opts.whitelist.forEach {
                        add(
                            ScanFilter.Builder().setDeviceAddress(it).build()
                        )
                    }
                }
                if (opts.manufacturerId != null && opts.manufacturerData != null) {
                    add(
                        ScanFilter.Builder().setManufacturerData(
                            opts.manufacturerId, opts.manufacturerData, opts.manufacturerMask
                        ).build()
                    )
                }
            }

            cont.invokeOnCancellation {                       // ✅ 只注册一次
                finishOnce(emptyList())
            }

            startTimer.schedule(object : TimerTask() {
                @SuppressLint("NewApi")
                override fun run() {
                    if (finished.get()) return

                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 提升发现概率
                        .setReportDelay(0)                               // 先关批量，先保证能看到
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .apply {
                            // 兼容老/新广播：尽量把能开的都开上（API 版本判断自己加）
                            try {
                                setLegacy(true)
                            } catch (_: Throwable) {
                            }
                            try {
                                setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                            } catch (_: Throwable) {
                            }
                        }
                        .build()

                    cb = object : ScanCallback() {            // ✅ 这时才赋值
                        override fun onScanResult(t: Int, r: ScanResult) = handle(r)
                        override fun onBatchScanResults(rs: MutableList<ScanResult>) {
                            rs.forEach(::handle)
                        }

                        override fun onScanFailed(errorCode: Int) {
                            finishOnce(emptyList())
                        }

                        fun handle(r: ScanResult) {
                            val mac = r.device?.address ?: return
                            val name = r.scanRecord?.deviceName ?: r.device?.name
                            if (opts.whitelist.isNotEmpty() && mac !in opts.whitelist) return
                            opts.deviceNameExact?.let { if (name != it) return }
                            opts.deviceNamePrefix?.let {
                                if (name?.startsWith(
                                        it,
                                        true
                                    ) != true
                                ) return
                            }
                            if (opts.manufacturerId != null) {
                                val bytes =
                                    r.scanRecord?.manufacturerSpecificData?.get(opts.manufacturerId)
                                        ?: return
                                opts.manufacturerData?.let { pat ->
                                    val mask = opts.manufacturerMask
                                    if (bytes.size < pat.size) return
                                    for (i in pat.indices) {
                                        val b = bytes[i]
                                        val m = (mask?.getOrNull(i) ?: 0xFF.toByte())
                                        if ((b.toInt() and m.toInt()) != (pat[i].toInt() and m.toInt())) return
                                    }
                                }
                            }
                            seen[mac] = Adv(mac, name, r.rssi)
                        }
                    }

                    try {
                        scanner.startScan(buildFilters(), settings, cb)
                    } catch (e: Throwable) {
                        finishOnce(emptyList()); return
                    }
                    started = true

                    stopTimer = Timer().apply {
                        schedule(object : TimerTask() {
                            override fun run() {
                                finishOnce(seen.values.toList())
                            }
                        }, durationMs)
                    }
                }
            }, wait)
        }
}
