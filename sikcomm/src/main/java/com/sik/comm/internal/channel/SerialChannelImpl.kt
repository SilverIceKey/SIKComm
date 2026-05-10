package com.sik.comm.internal.channel

import com.sik.comm.CommException
import com.sik.comm.CommReceiver
import com.sik.comm.SerialConfig
import com.sik.comm.internal.ioloop.HalfDuplexIoLooper
import com.sik.comm.internal.transport.JniSerialTransport
import com.sik.comm.internal.transport.Transport
import kotlinx.coroutines.Job

/**
 * 串口通道实现（RS232 / RS485 / USB-Serial 原生）。
 */
internal class SerialChannelImpl(
    private val config: SerialConfig,
    transportOverride: Transport? = null
) : BaseCommChannel(config.id) {

    private val transport: Transport = transportOverride ?: JniSerialTransport()
    private val looper = HalfDuplexIoLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs
    )

    private var ioJob: Job? = null

    override fun doOpen(): Long = transport.open(config)

    override fun onOpened(handle: Long) {
        ioJob = looper.start(scope, handle) { currentReceiver }
    }

    override fun doClose(handle: Long) {
        ioJob?.cancel()
        ioJob = null
        looper.shutdown()
        transport.close(handle)
    }

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        val handle = requireHandle()
        return looper.send(bytes, timeoutMs ?: config.writeTimeoutMs)
    }
}
