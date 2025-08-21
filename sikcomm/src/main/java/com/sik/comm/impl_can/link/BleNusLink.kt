package com.sik.comm.impl_can.link

import android.bluetooth.*
import android.content.Context
import com.sik.comm.impl_can.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*

class BleNusLink(
    private val appCtx: Context,
    override val config: CanLinkConfig.BleNusConfig
) : CanLink, BluetoothGattCallback() {

    override var isOpen: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rx = MutableSharedFlow<CANFrame>(extraBufferCapacity = 256)
    override val frames = rx.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var chTx: BluetoothGattCharacteristic? = null
    private var chRx: BluetoothGattCharacteristic? = null
    private val readerBuf = StringBuilder(256)

    override suspend fun open() {
        if (isOpen) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("No BluetoothAdapter")
        val dev = adapter.getRemoteDevice(config.mac) ?: error("No BLE device")
        gatt = dev.connectGatt(appCtx, false, this)
        // 简化：等待服务发现（生产可用 suspendCancellableCoroutine）
        delay(1200)
        isOpen = chTx != null && chRx != null
        gatt?.requestMtu(config.mtu)

        val idx = when (config.bitrate) {
            10_000 -> 0; 20_000 -> 1; 50_000 -> 2; 100_000 -> 3
            125_000 -> 4; 250_000 -> 5; 500_000 -> 6; 800_000 -> 7
            else -> 8
        }
        runCatching {
            writeAscii(SlcanCodec.Cmd.setBitrateIndex(idx))
            writeAscii(SlcanCodec.Cmd.OPEN)
        }
    }

    override suspend fun send(frame: CANFrame): Boolean {
        val c = chTx ?: return false
        val g = gatt ?: return false
        val payload = SlcanCodec.encode(frame)
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = payload
        return g.writeCharacteristic(c)
    }

    override fun close() {
        isOpen = false
        runCatching { writeAscii(SlcanCodec.Cmd.CLOSE) }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        chRx = null; chTx = null; gatt = null
    }

    /* ---- GATT 回调 ---- */

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val svc = gatt.getService(config.serviceUUID) ?: return
        chTx = svc.getCharacteristic(config.txUUID)
        chRx = svc.getCharacteristic(config.rxUUID)
        if (chRx != null) {
            gatt.setCharacteristicNotification(chRx, true)
            val cccd = chRx!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            cccd?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == config.rxUUID) {
            val bytes = characteristic.value ?: return
            for (b in bytes) {
                val c = b.toInt().toChar()
                if (c == '\r') {
                    val line = readerBuf.toString()
                    readerBuf.setLength(0)
                    SlcanCodec.decode(line)?.let { scope.launch { rx.emit(it) } }
                } else if (c != '\n') readerBuf.append(c)
            }
        }
    }

    private fun writeAscii(cmd: String) {
        val c = chTx ?: return
        val g = gatt ?: return
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = cmd.toByteArray()
        g.writeCharacteristic(c)
    }
}
