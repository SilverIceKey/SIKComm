package com.sik.comm.device

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks device online state and last communication time.
 */
object DeviceManager {
    private val lastSeen = ConcurrentHashMap<String, Long>()

    fun updateDevice(deviceId: String) {
        lastSeen[deviceId] = System.currentTimeMillis()
    }

    fun isOnline(deviceId: String, timeoutMs: Long = 10_000): Boolean {
        val last = lastSeen[deviceId] ?: return false
        return System.currentTimeMillis() - last < timeoutMs
    }
}
