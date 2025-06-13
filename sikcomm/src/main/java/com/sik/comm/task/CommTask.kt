package com.sik.comm.task

/**
 * Represents a unit of communication work. Implementations specify
 * the target transport and data to send.
 */
sealed interface CommTask {
    val deviceId: String
}

/** Task executed over a serial port */
data class SerialTask(
    override val deviceId: String,
    val portPath: String,
    val payload: ByteArray
) : CommTask

/** Task executed over BLE */
data class BleTask(
    override val deviceId: String,
    val mac: String,
    val payload: ByteArray
) : CommTask
