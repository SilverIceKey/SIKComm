package com.sik.comm.impl_ble

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stable channel on top of BleConnection: serial write + notification buffering.
 * It exposes a blocking-ish API for simplicity to integrate with existing stacks.
 */
class BleStableChannel(
    private val conn: BleConnection,
    inboxCapacity: Int = 256
) : BleIo {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inbox = Channel<ByteArray>(capacity = inboxCapacity)
    private val writeMux = Mutex()

    @Volatile private var opened = false
    @Volatile private var mac: String = ""
    @Volatile private var route: GattRoute? = null

    override fun open(mac: String, route: GattRoute) {
        this.mac = mac
        this.route = route
        runBlocking {
            conn.onNotify = { bytes -> if (bytes.isNotEmpty()) inbox.trySend(bytes.copyOf()) }
            conn.ensureReady(mac, route)
            opened = true
        }
    }

    override fun isOpen(): Boolean = opened

    override fun writeFrame(frame: ByteArray) {
        val r = route ?: error("BleStableChannel not opened")
        runBlocking {
            writeMux.withLock { conn.write(r, frame) }
        }
    }

    override fun readFrame(timeoutMs: Long): ByteArray? = runBlocking {
        withTimeoutOrNull(timeoutMs) { inbox.receive() }
    }

    override fun close() {
        opened = false
        scope.cancel()
        runBlocking { conn.disconnect() }
    }
}