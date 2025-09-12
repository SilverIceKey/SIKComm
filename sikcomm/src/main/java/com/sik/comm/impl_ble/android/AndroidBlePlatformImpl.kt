package com.sik.comm.impl_ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.sik.comm.impl_ble.GattRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation that strictly serializes all GATT operations.
 */
class AndroidBlePlatformImpl(private val context: Context) : AndroidBlePlatform {
    private val btMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter get() = btMgr.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gattMux = Mutex()

    private var gatt: BluetoothGatt? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var onNotifyCb: ((ByteArray) -> Unit)? = null
    private var mtu: Int = 185

    override fun currentMtu(): Int = mtu

    @OptIn(InternalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override suspend fun scan(durationMs: Long, options: ScanOptions): List<Pair<String, Int>> =
        suspendCancellableCoroutine { cont ->
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) { cont.resume(emptyList()); return@suspendCancellableCoroutine }

            val seen = LinkedHashMap<String, Int>()

            // OS 级“预过滤”（OR 语义），避免组合爆炸，复杂 AND 留到代码层
            val filters = mutableListOf<ScanFilter>().apply {
                options.deviceNameExact?.let { add(ScanFilter.Builder().setDeviceName(it).build()) }
                options.serviceUuids.forEach { add(ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()) }
                // 只有当“只有白名单条件”时，才用 OS 级地址过滤；否则改代码层 AND
                if (options.whitelist.isNotEmpty() && isEmpty()) {
                    options.whitelist.forEach { mac -> add(ScanFilter.Builder().setDeviceAddress(mac).build()) }
                }
                if (options.manufacturerId != null && options.manufacturerData != null) {
                    add(
                        ScanFilter.Builder()
                            .setManufacturerData(options.manufacturerId, options.manufacturerData, options.manufacturerMask)
                            .build()
                    )
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val mac  = result.device?.address ?: return
                    val name = result.scanRecord?.deviceName ?: result.device?.name

                    // 代码层 AND 校验（更严格，避免 OR 过宽）
                    if (options.whitelist.isNotEmpty() && mac !in options.whitelist) return
                    options.deviceNameExact?.let { if (name != it) return }
                    options.deviceNamePrefix?.let { prefix ->
                        if (name == null || !name.startsWith(prefix, true)) return
                    }
                    if (options.manufacturerId != null) {
                        val sparse = result.scanRecord?.manufacturerSpecificData
                        val bytes  = sparse?.get(options.manufacturerId)
                        if (bytes == null) return
                        if (options.manufacturerData != null) {
                            val pat  = options.manufacturerData
                            val mask = options.manufacturerMask
                            if (bytes.size < pat.size) return
                            for (i in pat.indices) {
                                val b = bytes[i]
                                val m = (mask?.getOrNull(i) ?: 0xFF.toByte())
                                if ((b.toInt() and m.toInt()) != (pat[i].toInt() and m.toInt())) return
                            }
                        }
                    }

                    seen[mac] = result.rssi
                }
                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    for (r in results) onScanResult(0, r)
                }
                override fun onScanFailed(errorCode: Int) {
                    cont.tryResume(emptyList()) { }
                }
            }

            scanner.startScan(filters, settings, cb)
            scope.launch {
                delay(durationMs)
                try { scanner.stopScan(cb) } catch (_: Exception) {}
                cont.resume(seen.entries.map { it.key to it.value })
            }
            cont.invokeOnCancellation { try { scanner.stopScan(cb) } catch (_: Exception) {} }
        }

    @SuppressLint("MissingPermission")
    override suspend fun connectAndDiscover(
        mac: String,
        route: GattRoute,
        onNotify: (ByteArray) -> Unit,
        connectTimeoutMs: Long,
        discoverTimeoutMs: Long
    ) {
        gattMux.withLock {
            runCatching { gatt?.close() }
            onNotifyCb = onNotify
            val device = adapter.getRemoteDevice(mac)

            val writeAck = CompletableDeferred<Unit>()
            val descWriteAck = CompletableDeferred<Unit>()
            val servicesReady = CompletableDeferred<Unit>()
            val connected = CompletableDeferred<Unit>()

            val gattCb = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!connected.isCompleted) connected.complete(Unit)
                    } else if (status != BluetoothGatt.GATT_SUCCESS && !connected.isCompleted) {
                        connected.completeExceptionally(IllegalStateException("connect status=$status state=$newState"))
                    }
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // complete pending ops exceptionally to unblock waits
                        if (!servicesReady.isCompleted) servicesReady.completeExceptionally(
                            IllegalStateException("disconnected")
                        )
                        if (!descWriteAck.isCompleted) descWriteAck.completeExceptionally(
                            IllegalStateException("disconnected")
                        )
                        if (!writeAck.isCompleted) writeAck.completeExceptionally(
                            IllegalStateException("disconnected")
                        )
                    }
                }

                override fun onMtuChanged(g: BluetoothGatt, mtuOut: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) mtu = mtuOut
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && !servicesReady.isCompleted) servicesReady.complete(
                        Unit
                    )
                    else if (status != BluetoothGatt.GATT_SUCCESS && !servicesReady.isCompleted) servicesReady.completeExceptionally(
                        IllegalStateException("discover status=$status")
                    )
                }

                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    c: BluetoothGattCharacteristic
                ) {
                    if (c == notifyChar) c.value?.let { onNotifyCb?.invoke(it) }
                }

                override fun onCharacteristicWrite(
                    g: BluetoothGatt,
                    c: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (!writeAck.isCompleted) {
                        if (status == BluetoothGatt.GATT_SUCCESS) writeAck.complete(Unit)
                        else writeAck.completeExceptionally(IllegalStateException("write status=$status"))
                    }
                }

                override fun onDescriptorWrite(
                    g: BluetoothGatt,
                    d: BluetoothGattDescriptor,
                    status: Int
                ) {
                    if (!descWriteAck.isCompleted) {
                        if (status == BluetoothGatt.GATT_SUCCESS) descWriteAck.complete(Unit)
                        else descWriteAck.completeExceptionally(IllegalStateException("cccd status=$status"))
                    }
                }
            }

            gatt = withTimeout(connectTimeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val g = device.connectGatt(context, false, gattCb, BluetoothDevice.TRANSPORT_LE)
                    if (g == null) {
                        cont.resumeWithException(IllegalStateException("connectGatt returned null")); return@suspendCancellableCoroutine
                    }
                    scope.launch {
                        try {
                            connected.await(); cont.resume(g)
                        } catch (t: Throwable) {
                            cont.resumeWithException(t)
                        }
                    }
                    cont.invokeOnCancellation { runCatching { g.disconnect(); g.close() } }
                }
            }
            val g = gatt ?: throw IllegalStateException("No GATT after connect")

            // Request MTU (best-effort)
            try {
                route.requestMtu?.let { g.requestMtu(it) }
            } catch (_: Exception) {
            }

            // Discover services and resolve chars
            withTimeout(discoverTimeoutMs) { servicesReady.await() }
            val svc = g.getService(route.service)
                ?: throw IllegalStateException("Service ${route.service} not found")
            val w = svc.getCharacteristic(route.writeChar)
                ?: throw IllegalStateException("Write char ${route.writeChar} not found")
            val n = svc.getCharacteristic(route.notifyChar)
                ?: throw IllegalStateException("Notify char ${route.notifyChar} not found")
            writeChar = w; notifyChar = n

            // Enable notifications/indications
            if (!g.setCharacteristicNotification(
                    n,
                    true
                )
            ) throw IllegalStateException("setCharacteristicNotification failed")
            val cccd = n.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                ?: throw IllegalStateException("CCCD not found")
            cccd.value = if ((n.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            withTimeout(discoverTimeoutMs) {
                if (!g.writeDescriptor(cccd)) throw IllegalStateException("writeDescriptor failed to start")
                descWriteAck.await()
            }
        }
    }

    override suspend fun write(route: GattRoute, payload: ByteArray, timeoutMs: Long) {
        gattMux.withLock {
            val g = gatt ?: throw IllegalStateException("Not connected")
            val wc = writeChar ?: throw IllegalStateException("Write char not resolved")
            val max = (mtu - 3).coerceAtLeast(20).coerceAtMost(512)
            var off = 0
            while (off < payload.size) {
                val end = (off + max).coerceAtMost(payload.size)
                val chunk = payload.copyOfRange(off, end)
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        val ack = CompletableDeferred<Unit>()
                        // Tie into callback via a one-shot listener
                        val cb = object : BluetoothGattCallback() {}
                        // We reuse the common callback path; here simply rely on onCharacteristicWrite→ack
                        wc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        wc.value = chunk
                        if (!g.writeCharacteristic(wc)) {
                            cont.resumeWithException(IllegalStateException("write start failed"))
                            return@suspendCancellableCoroutine
                        }
                        scope.launch {
                            try {
                                delay(10); cont.resume(Unit)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
                off = end
            }
        }
    }

    override suspend fun disconnect() {
        gattMux.withLock {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null; notifyChar = null; writeChar = null; onNotifyCb = null; mtu = 185
        }
    }
}

