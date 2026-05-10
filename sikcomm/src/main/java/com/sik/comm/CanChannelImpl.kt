package com.sik.comm

import com.sik.comm.internal.ioloop.FullDuplexLooper
import com.sik.comm.internal.transport.JniCanTransport
import com.sik.comm.internal.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * SocketCAN 通道实现。
 *
 * 基于 [FullDuplexLooper] 实现真全双工：读循环与写操作分离。
 */
internal class CanChannelImpl(
    private val config: CanConfig,
    transportOverride: Transport? = null
) : CommChannel {

    override val id: String get() = config.id

    private val transport: Transport = transportOverride ?: JniCanTransport()
    private val looper = FullDuplexLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs,
        bufferSize = 72
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var receiver: CommReceiver? = null

    private var readJob: Job? = null

    override fun open() {
        if (isOpen()) return
        val fd = transport.open(config)
        require(fd > 0L) {
            "Failed to open CAN interface: ${config.ifName}, handle=$fd"
        }
        handle = fd
        readJob = looper.startReadLoop(scope, fd) { receiver }
    }

    override fun close() {
        readJob?.cancel()
        readJob = null

        if (handle != 0L) {
            transport.close(handle)
            handle = 0L
        }

        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) { "CanChannelImpl#send called when not open (id=$id)" }
        val fd = handle
        check(fd != 0L) { "CAN handle is closed during send (id=$id)" }
        return looper.writeDirect(fd, bytes, 0, bytes.size, timeoutMs ?: config.writeTimeoutMs)
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.receiver = receiver
    }
}
