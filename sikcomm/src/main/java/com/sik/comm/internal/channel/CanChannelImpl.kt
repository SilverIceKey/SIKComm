package com.sik.comm.internal.channel

import com.sik.comm.CanConfig
import com.sik.comm.CommReceiver
import com.sik.comm.internal.ioloop.FullDuplexLooper
import com.sik.comm.internal.transport.JniCanTransport
import com.sik.comm.internal.transport.Transport
import kotlinx.coroutines.Job

/**
 * SocketCAN 通道实现。
 */
internal class CanChannelImpl(
    private val config: CanConfig,
    transportOverride: Transport? = null
) : BaseCommChannel(config.id) {

    private val transport: Transport = transportOverride ?: JniCanTransport()
    private val looper = FullDuplexLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs,
        bufferSize = 72
    )

    private var readJob: Job? = null

    override fun doOpen(): Long = transport.open(config)

    override fun onOpened(handle: Long) {
        readJob = looper.startReadLoop(scope, handle) { currentReceiver }
    }

    override fun doClose(handle: Long) {
        readJob?.cancel()
        readJob = null
        transport.close(handle)
    }

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        val handle = requireHandle()
        return looper.writeDirect(handle, bytes, 0, bytes.size, timeoutMs ?: config.writeTimeoutMs)
    }
}
