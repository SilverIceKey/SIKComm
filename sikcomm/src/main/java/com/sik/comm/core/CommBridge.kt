package com.sik.comm.core

import com.sik.comm.device.DeviceManager

/**
 * Unified communication entry that schedules tasks.
 */
object CommBridge {
    fun sendTask(deviceId: String, data: ByteArray) {
        // Just mark device as seen in this demo implementation
        DeviceManager.updateDevice(deviceId)
        // Real sending logic should be implemented here
    }
}
