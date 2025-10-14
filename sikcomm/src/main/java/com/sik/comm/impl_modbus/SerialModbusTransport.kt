package com.sik.comm.impl_modbus

import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * 基于串口（RS485）的 Modbus 传输层实现。
 */
internal class SerialModbusTransport(
    private val config: ModbusConfig
) : ModbusTransport {

    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null

    override fun open() {
        if (isOpen()) return
        val portFile = File(config.portName)
        require(portFile.exists()) { "Serial port ${config.portName} not found" }
        try {
            input = FileInputStream(portFile)
            output = FileOutputStream(portFile)
        } catch (t: Throwable) {
            runCatching { input?.close() }
            runCatching { output?.close() }
            input = null
            output = null
            throw t
        }
    }

    override fun isOpen(): Boolean {
        return input != null && output != null
    }

    override fun write(data: ByteArray) {
        val out = output ?: error("Serial port not opened.")
        out.write(data)
        out.flush()
    }

    override fun readFrame(timeoutMs: Int, expectedSize: Int?, silenceGapMs: Int): ByteArray {
        val inp = input ?: error("Serial port not opened.")
        val buffer = ByteArrayOutputStream()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var lastRead = SystemClock.elapsedRealtime()
        val temp = ByteArray(256)

        while (true) {
            if (SystemClock.elapsedRealtime() > deadline) {
                if (buffer.size() == 0) {
                    throw TimeoutException("Modbus serial read timeout: ${timeoutMs}ms")
                }
                break
            }

            val available = try {
                inp.available()
            } catch (io: IOException) {
                throw io
            }

            if (available > 0) {
                val toRead = available.coerceAtMost(temp.size)
                val read = inp.read(temp, 0, toRead)
                if (read > 0) {
                    buffer.write(temp, 0, read)
                    lastRead = SystemClock.elapsedRealtime()
                    if (expectedSize != null && buffer.size() >= expectedSize) {
                        break
                    }
                } else if (read < 0) {
                    break
                }
            } else {
                if (buffer.size() > 0) {
                    val gap = SystemClock.elapsedRealtime() - lastRead
                    if (gap >= silenceGapMs) {
                        break
                    }
                }
                try {
                    Thread.sleep(2)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        val data = buffer.toByteArray()
        if (data.isEmpty()) {
            throw TimeoutException("Modbus serial read timeout: ${timeoutMs}ms")
        }
        return data
    }

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        input = null
        output = null
    }
}
