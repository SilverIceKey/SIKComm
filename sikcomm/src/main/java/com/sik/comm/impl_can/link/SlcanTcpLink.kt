package com.sik.comm.impl_can.link

import com.sik.comm.impl_can.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.InetSocketAddress
import java.net.Socket

class SlcanTcpLink(
    override val config: CanLinkConfig.SlcanTcpConfig
) : CanLink {
    override var isOpen: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rx = MutableSharedFlow<CANFrame>(extraBufferCapacity = 256)
    override val frames = rx.asSharedFlow()

    private var socket: Socket? = null
    private var readerJob: Job? = null
    private val writeLock = Any()

    override suspend fun open() {
        if (isOpen) return
        val s = Socket()
        s.connect(InetSocketAddress(config.host, config.port), 3000)
        socket = s
        isOpen = true

        // 波特率索引（按需改）
        val idx = when (config.bitrate) {
            10_000 -> 0; 20_000 -> 1; 50_000 -> 2; 100_000 -> 3
            125_000 -> 4; 250_000 -> 5; 500_000 -> 6; 800_000 -> 7
            else -> 8
        }
        runCatching {
            writeAscii(SlcanCodec.Cmd.setBitrateIndex(idx))
            writeAscii(SlcanCodec.Cmd.OPEN)
        }

        readerJob = scope.launch {
            val input = s.getInputStream()
            val buf = ByteArray(2048)
            val line = StringBuilder(128)
            while (isActive && !s.isClosed) {
                val n = input.read(buf)
                if (n <= 0) continue
                for (i in 0 until n) {
                    val c = buf[i].toInt().toChar()
                    if (c == '\r') {
                        val str = line.toString()
                        line.setLength(0)
                        SlcanCodec.decode(str)?.let { rx.emit(it) }
                    } else if (c != '\n') line.append(c)
                }
            }
        }
    }

    override suspend fun send(frame: CANFrame): Boolean {
        if (!isOpen) return false
        val payload = SlcanCodec.encode(frame)
        synchronized(writeLock) {
            val os = socket?.getOutputStream() ?: return false
            os.write(payload); os.flush()
        }
        return true
    }

    override fun close() {
        isOpen = false
        runCatching { writeAscii(SlcanCodec.Cmd.CLOSE) }
        readerJob?.cancel()
        runCatching { socket?.close() }
        socket = null
    }

    private fun writeAscii(cmd: String) {
        synchronized(writeLock) {
            socket?.getOutputStream()?.apply {
                write(cmd.toByteArray()); flush()
            }
        }
    }
}
