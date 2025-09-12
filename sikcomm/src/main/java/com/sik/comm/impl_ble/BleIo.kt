package com.sik.comm.impl_ble

/**
 * CAN-like minimal blocking I/O abstraction.
 * No protocol framing/crypto at this layer; upper layers own it.
 */
interface BleIo : AutoCloseable {
    fun open(mac: String, route: GattRoute)
    fun isOpen(): Boolean
    fun writeFrame(frame: ByteArray)
    fun readFrame(timeoutMs: Long): ByteArray?
    override fun close()
}