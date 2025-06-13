package com.sik.comm.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple BLE manager for scanning and connection state tracking.
 * It does not hold any Activity or Fragment context.
 */
class BleManager(applicationContext: Context) {
    private val context = applicationContext.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val current = _scannedDevices.value
            if (current.none { it.address == device.address }) {
                _scannedDevices.value = current + device
            }
        }
    }

    fun startScan() {
        scanner?.startScan(callback)
    }

    fun stopScan() {
        scanner?.stopScan(callback)
    }

    /**
     * Placeholder send implementation. In real code this would handle
     * connecting and writing to the BLE characteristic identified by
     * [mac].
     */
    fun send(mac: String, data: ByteArray): Boolean {
        // No real BLE operations implemented yet
        return adapter != null && mac.isNotEmpty() && data.isNotEmpty()
    }
}
