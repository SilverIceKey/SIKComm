package com.sik.comm

import com.sik.comm.internal.ioloop.HalfDuplexIoLooper
import com.sik.comm.internal.transport.JniSerialTransport
import com.sik.comm.internal.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 串口通道实现（RS232 / RS485 / USB-Serial 原生）。
 *
 * 基于 [HalfDuplexIoLooper] 实现读优先的半双工调度。
 */
internal class SerialChannelImpl(
    private val config: SerialConfig,
    transportOverride: Transport? = null
) : CommChannel {

    override val id: String get() = config.id

    private val transport: Transport = transportOverride ?: JniSerialTransport()
    private val looper = HalfDuplexIoLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var receiver: CommReceiver? = null

    private var ioJob: Job? = null

    override fun open() {
        if (isOpen()) return
        val fd = transport.open(config)
        require(fd > 0L) {
            "Failed to open serial port: ${config.devicePath}, handle=$fd"
        }
        handle = fd
        ioJob = looper.start(scope, fd) { receiver }
    }

    override fun close() {
        ioJob?.cancel()
        ioJob = null

        looper.shutdown()

        if (handle != 0L) {
            transport.close(handle)
            handle = 0L
        }

        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) { "SerialChannelImpl#send called when not open (id=$id)" }
        return looper.send(bytes, timeoutMs ?: config.writeTimeoutMs)
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.receiver = receiver
    }
}
