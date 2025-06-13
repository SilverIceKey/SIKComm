package com.sik.comm.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple BLE manager for scanning and connection state tracking.
 * It does not hold any Activity or Fragment context.
 */
class BleManager(applicationContext: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    fun startScan() {
        val list = adapter?.bondedDevices?.toList() ?: emptyList()
        _scannedDevices.value = list
    }
}
