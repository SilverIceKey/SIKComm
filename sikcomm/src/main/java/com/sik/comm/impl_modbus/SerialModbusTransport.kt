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

        val buf = ByteArrayOutputStream()
        val start = SystemClock.elapsedRealtime()
        val hardDeadline = start + timeoutMs

        // 当有“新字节”时，滑动这个空闲截止时间
        var byteIdleDeadline = start + silenceGapMs.coerceAtLeast(1)

        // 读块大小稍微大一点，减少 JNI/系统调用开销
        val temp = ByteArray(4096)

        while (true) {
            val now = SystemClock.elapsedRealtime()

            // 1) 先看整体截止：到点了
            if (now >= hardDeadline) {
                // 有数据就“流式返回”当前块；完全没数据才算真正超时
                if (buf.size() == 0) {
                    throw TimeoutException("Modbus serial read timeout: ${timeoutMs}ms")
                } else {
                    break
                }
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
                    buf.write(temp, 0, read)
                    // 有进展：滑动字节空闲截止
                    byteIdleDeadline = SystemClock.elapsedRealtime() + silenceGapMs.coerceAtLeast(1)

                    // 如果调用方告诉了期望长度，达到就直接返回
                    if (expectedSize != null && buf.size() >= expectedSize) {
                        break
                    }

                    // 继续读，尽可能把当前缓冲的字节吃干净（不要立刻 sleep）
                    continue
                } else if (read < 0) {
                    // 流关闭：把已有数据返回；没有就算超时
                    if (buf.size() == 0) {
                        throw TimeoutException("Serial input closed while waiting for data")
                    } else {
                        break
                    }
                }
            }

            // 2) 没有新字节：看看是否达到“字节空闲截止”
            if (buf.size() > 0 && SystemClock.elapsedRealtime() >= byteIdleDeadline) {
                // 认为一个“帧块”结束（或当前可交付的一坨数据）→ 返回
                break
            }

            // 3) 没数据还没到任何截止 → 轻量休眠，别空转
            try {
                Thread.sleep(1)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        val data = buf.toByteArray()
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
