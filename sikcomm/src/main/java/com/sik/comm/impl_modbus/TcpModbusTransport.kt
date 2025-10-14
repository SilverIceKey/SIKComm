package com.sik.comm.impl_modbus

import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * 基于 TCP 的 Modbus 传输层。
 */
internal class TcpModbusTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int
) : ModbusTransport {

    private var socket: Socket? = null

    override fun open() {
        if (isOpen()) return
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.tcpNoDelay = true
            socket = s
        } catch (t: Throwable) {
            runCatching { s.close() }
            throw t
        }
    }

    override fun isOpen(): Boolean {
        val s = socket
        return s != null && s.isConnected && !s.isClosed
    }

    override fun write(data: ByteArray) {
        val s = socket ?: error("TCP socket not opened.")
        try {
            val out = s.getOutputStream()
            out.write(data)
            out.flush()
        } catch (io: IOException) {
            throw io
        }
    }

    override fun readFrame(timeoutMs: Int, expectedSize: Int?, silenceGapMs: Int): ByteArray {
        val s = socket ?: error("TCP socket not opened.")
        val input = s.getInputStream()
        val buffer = ByteArrayOutputStream()
        val temp = ByteArray(256)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var lastRead = SystemClock.elapsedRealtime()
        s.soTimeout = silenceGapMs.coerceAtLeast(1)

        while (true) {
            if (SystemClock.elapsedRealtime() > deadline) {
                if (buffer.size() == 0) {
                    throw TimeoutException("Modbus TCP read timeout: ${timeoutMs}ms")
                }
                break
            }

            try {
                val read = input.read(temp, 0, temp.size)
                if (read > 0) {
                    buffer.write(temp, 0, read)
                    lastRead = SystemClock.elapsedRealtime()
                    if (expectedSize != null && buffer.size() >= expectedSize) {
                        break
                    }
                } else if (read < 0) {
                    break
                }
            } catch (e: SocketTimeoutException) {
                if (buffer.size() > 0) {
                    break
                }
            }

            if (buffer.size() > 0) {
                val gap = SystemClock.elapsedRealtime() - lastRead
                if (gap >= silenceGapMs) {
                    break
                }
            }
        }

        val data = buffer.toByteArray()
        if (data.isEmpty()) {
            throw TimeoutException("Modbus TCP read timeout: ${timeoutMs}ms")
        }
        return data
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }
}
