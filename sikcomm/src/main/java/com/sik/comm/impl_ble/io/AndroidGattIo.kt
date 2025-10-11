package com.sik.comm.impl_ble.io

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import com.sik.comm.impl_ble.GattRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Android GATT I/O：严格逐片 ACK，通知入队 read()。
 * 仅使用 suspend/Channel，不创建内部长期作用域。
 */
class AndroidGattIo(private val context: Context) : BleIo {

    private var gatt: BluetoothGatt? = null
    private val notifyQ = Channel<ByteArray>(capacity = Channel.BUFFERED)

    private var mtu: Int = 23
    private var pendingWriteAck: CompletableDeferred<Unit>? = null
    private var pendingDescAck: CompletableDeferred<Unit>? = null

    @SuppressLint("MissingPermission")
    override suspend fun open(mac: String) {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val dev = mgr.adapter.getRemoteDevice(mac)
        val connected = CompletableDeferred<Unit>()
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt = g
                    if (!connected.isCompleted) connected.complete(Unit)
                } else if (status != BluetoothGatt.GATT_SUCCESS && !connected.isCompleted) {
                    connected.completeExceptionally(IllegalStateException("connect status=$status state=$newState"))
                }
            }
            override fun onMtuChanged(g: BluetoothGatt, mtuOut: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) mtu = mtuOut
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                c.value?.let { notifyQ.trySend(it.copyOf()) }
            }
            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                pendingWriteAck?.let { d ->
                    if (status == BluetoothGatt.GATT_SUCCESS) d.complete(Unit)
                    else d.completeExceptionally(IllegalStateException("write status=$status"))
                }
                pendingWriteAck = null
            }
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                pendingDescAck?.let { a ->
                    if (status == BluetoothGatt.GATT_SUCCESS) a.complete(Unit)
                    else a.completeExceptionally(IllegalStateException("cccd status=$status"))
                }
                pendingDescAck = null
            }
        }
        val g = dev.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
            ?: throw IllegalStateException("connectGatt returned null")
        withTimeout(15_000) { connected.await() }
    }

    override suspend fun isOpen(): Boolean = gatt != null

    @SuppressLint("MissingPermission")
    override suspend fun discover() {
        val g = gatt ?: error("No GATT")
        if (!g.discoverServices()) error("discover start failed")
        // 轮询等待 services 准备好（避免引入额外回调总线）
        var cnt = 0
        while (g.services.isNullOrEmpty()) {
            Thread.sleep(50)
            cnt++
            if (cnt > 300) error("discover timeout")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun subscribe(route: GattRoute) {
        val g = gatt ?: error("No GATT")
        val svc = g.getService(route.service) ?: error("Service ${route.service} not found")
        val chr = svc.getCharacteristic(route.notifyChar) ?: error("Notify ${route.notifyChar} not found")
        if (!g.setCharacteristicNotification(chr, true)) error("setCharacteristicNotification failed")
        val cccd = chr.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            ?: error("CCCD not found")
        cccd.value = if ((chr.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val ack = CompletableDeferred<Unit>()
        pendingDescAck = ack
        if (!g.writeDescriptor(cccd)) error("writeDescriptor start failed")
        withTimeout(3_000) { ack.await() }
    }

    @SuppressLint("MissingPermission")
    override suspend fun requestMtu(expect: Int?): Int {
        val g = gatt ?: error("No GATT")
        if (expect == null) return mtu
        if (!g.requestMtu(expect)) return mtu
        // 简单等待 MTU 更新事件传播
        var spins = 0
        while (spins < 20) {
            Thread.sleep(20)
            spins++
        }
        return mtu
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(route: GattRoute, payload: ByteArray, withResponse: Boolean) {
        val g = gatt ?: error("No GATT")
        val svc = g.getService(route.service) ?: error("Service ${route.service} not found")
        val wc = svc.getCharacteristic(route.writeChar) ?: error("Write ${route.writeChar} not found")
        wc.writeType = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        wc.value = payload
        val ack = if (withResponse) CompletableDeferred<Unit>() else null
        pendingWriteAck = ack
        if (!g.writeCharacteristic(wc)) error("write start failed")
        ack?.let { withTimeout(5_000) { it.await() } }
    }

    override suspend fun read(timeoutMs: Long): ByteArray? {
        return withTimeout(timeoutMs) { notifyQ.receive() }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        notifyQ.close()
        pendingWriteAck?.cancel()
        pendingDescAck?.cancel()
    }
}