package com.sik.comm.impl_modbus

import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * 基于串口（RS485）的 Modbus 传输层实现（优先使用 JNI + su 提权）。
 */
internal class SerialModbusTransport(
    private val config: ModbusConfig
) : ModbusTransport {

    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null
    private var nativePort: SerialPortNative? = null

    override fun open() {
        if (isOpen()) return
        val portFile = File(config.portName)
        require(portFile.exists()) { "Serial port ${config.portName} not found" }

        // 1) JNI + su 提权优先
        runCatching {
            val sp = SerialPortNative()
            val ok = sp.openAndInit(
                path = config.portName,
                baudRate = config.baudRate,
                dataBits = 8, parity = 0, stopBits = 1,
                flags = 0,       // 最高位可作为 NONBLOCK 标志，如果你想用的话
                trySu = true
            )
            if (ok) {
                nativePort = sp
                input = sp.inputStream
                output = sp.outputStream
                return
            }
        }.onFailure { /* 忽略，走回退 */ }

        // 2) 回退纯 Java（可能因权限失败；保留以兼容无 su 的环境）
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

    override fun isOpen(): Boolean = input != null && output != null

    override fun write(data: ByteArray) {
        val out = output ?: error("Serial port not opened.")
        out.write(data)
        out.flush()
    }

    override fun readFrame(timeoutMs: Int, expectedSize: Int?, silenceGapMs: Int): ByteArray {
        val inp = input ?: error("Serial port not opened.")

        val buf = ByteArrayOutputStream()
        val start = SystemClock.elapsedRealtime()
        val hardDeadline = start + timeoutMs
        var byteIdleDeadline = start + silenceGapMs.coerceAtLeast(1)
        val temp = ByteArray(4096)

        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (now >= hardDeadline) {
                if (buf.size() == 0) throw TimeoutException("Modbus serial read timeout: ${timeoutMs}ms")
                else break
            }

            val available = try { inp.available() } catch (io: IOException) { throw io }
            if (available > 0) {
                val toRead = available.coerceAtMost(temp.size)
                val read = inp.read(temp, 0, toRead)
                if (read > 0) {
                    buf.write(temp, 0, read)
                    byteIdleDeadline = SystemClock.elapsedRealtime() + silenceGapMs.coerceAtLeast(1)
                    if (expectedSize != null && buf.size() >= expectedSize) break
                    continue
                } else if (read < 0) {
                    if (buf.size() == 0) throw TimeoutException("Serial input closed while waiting for data")
                    else break
                }
            }

            if (buf.size() > 0 && SystemClock.elapsedRealtime() >= byteIdleDeadline) break
            try { Thread.sleep(1) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt(); break
            }
        }

        val data = buf.toByteArray()
        if (data.isEmpty()) throw TimeoutException("Modbus serial read timeout: ${timeoutMs}ms")
        return data
    }

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        input = null
        output = null
        runCatching { nativePort?.close() }
        nativePort = null
    }
}
