package com.sik.comm.impl_can

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 基于 Android 上 SocketCAN (PF_CAN) 的 I/O 实现。
 *
 * 特性：
 * - 在 open() 之前可选直接用系统命令把 can 接口拉起（down -> type can bitrate/sample-point -> up）
 * - 可设置 read 超时、原生 CAN 过滤器（内核过滤）
 * - 依赖 NativeCan JNI 完成 PF_CAN 套接字的 open/bind/read/write
 *
 * @param ifName         接口名（如 "can0"）
 * @param bitrate        波特率（bit/s），只用于“拉起接口”的系统命令；若已由系统配置，可保留默认
 * @param samplePoint    采样点（0.0~1.0，可为空；为空则不传该参数）
 * @param upOnOpen       是否在 open() 前尝试用系统命令把接口拉起
 * @param useSu          是否通过 su -c 走 root（调试/工程机）；量产建议系统层拉起
 * @param readTimeoutMs  读超时（JNI SO_RCVTIMEO）
 * @param filters        CAN 内核过滤器 (id, mask) 数组；为空表示不过滤（全收）
 */
class SocketCanIo(
    private val ifName: String,
    private val bitrate: Int = 1_000_000,
    private val samplePoint: Double? = 0.875,
    private val upOnOpen: Boolean = true,
    private val useSu: Boolean = true,
    private val readTimeoutMs: Int = 500,
    private val filters: List<Pair<Int, Int>> = emptyList()
) : CanIo {

    private val native = NativeCan()
    private var fd: Int = -1

    override fun open(interfaceName: String) {
        val name = if (ifName.isNotBlank()) ifName else interfaceName

        // 1) 可选：open 前先把接口拉起（开发/调试方便；量产建议放到系统 init.rc）
        if (upOnOpen) {
            bringUpInterface(name, bitrate, samplePoint, useSu)
        }

        // 2) 打开 PF_CAN 套接字并绑定
        fd = native.openSocket(name)
        require(fd >= 0) { "openSocket($name) failed: $fd" }
        // 关闭回环 & 不接收自发包（双重确保不是读到自己）
        native.setRawLoopback(fd, false)
        native.setRecvOwnMsgs(fd, false)
        // 3) 配置读超时
        native.setReadTimeoutMs(fd, readTimeoutMs)

        // 4) 配置内核过滤器（可选）
        if (filters.isNotEmpty()) {
            val ids = filters.map { it.first and 0x7FF }.toIntArray()
            val masks = filters.map { it.second and 0x7FF }.toIntArray()
            val r = native.setFilters(fd, ids, masks)
            require(r >= 0) { "setFilters failed: $r" }
        }
    }

    override fun isOpen(): Boolean = fd >= 0

    override fun write(frame: CanFrame) {
        val n = native.writeFrame(fd, frame.canId, frame.data, frame.dlc)
        require(n >= 0) { "write failed: $n" }
    }

    override fun read(): CanFrame? {
        val id = IntArray(1)
        val data = ByteArray(8)
        val dlc = native.readFrame(fd, id, data) // 超时返回负数（-EAGAIN）
        return if (dlc >= 0) CanFrame(id[0], dlc.coerceIn(0, 8), data) else null
    }

    override fun close() {
        if (fd >= 0) native.closeSocket(fd)
        fd = -1
    }

    private val SU_LOG_TAG = "SocketCanIo"

    // 小工具：后台线程持续吃流，防止阻塞
    private fun gobbleAsync(`in`: InputStream, tag: String, sink: StringBuilder): Thread {
        val t = Thread {
            try {
                BufferedReader(InputStreamReader(`in`)).use { br ->
                    var line: String?
                    while (true) {
                        line = br.readLine() ?: break
                        sink.append(line).append('\n')
                        Log.d(tag, line!!)
                    }
                }
            } catch (ignore: Exception) {
                // 进程被杀/流关闭会到这里，正常
            }
        }
        t.isDaemon = true
        t.start()
        return t
    }

    /**
     * 用系统命令把 can 接口拉起：down -> type can bitrate [sample-point] -> up
     * - 必须在后台线程调用；如在主线程会直接返回并警告。
     * - 全程并发消费 stdout/stderr，避免 waitFor 死锁。
     * - 带超时；超时会 destroyForcibly。
     */
    @SuppressLint("NewApi")
    private fun bringUpInterface(
        name: String,
        bitrate: Int,
        samplePoint: Double?,
        useSu: Boolean
    ) {
        // 避免主线程阻塞：主线程直接告警并返回（也可以抛异常看你偏好）
        if (Looper.getMainLooper() === Looper.myLooper()) {
            Log.w(
                SU_LOG_TAG,
                "bringUpInterface called on MAIN thread; skip to avoid ANR"
            )
            return
        }

        val setupCmd = buildString {
            append("ip link set ").append(name).append(" down; ")
            append("ip link set ").append(name).append(" type can bitrate ").append(bitrate)
            if (samplePoint != null && samplePoint > 0.0) {
                append(" sample-point ").append(samplePoint)
            }
            // 链路层禁用 loopback（和套接字层一起双保险）
            append(" loopback off")
            append("; ip link set ").append(name).append(" up")
        }

        val pb = if (useSu) {
            // 兼容 toybox su：su [UID] [COMMAND...]
            ProcessBuilder("su", "0", "sh", "-c", setupCmd)
        } else {
            ProcessBuilder("sh", "-c", setupCmd)
        }.redirectErrorStream(true)

        var proc: Process? = null
        val outBuf = StringBuilder()
        try {
            proc = pb.start()

            // 并发吃 stdout（redirectErrorStream=true 已合流）
            val tOut = gobbleAsync(proc.inputStream, SU_LOG_TAG, outBuf)

            // 等待最多 3 秒；期间输出被持续消费，不会死锁
            val finished = proc.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                // 超时：强杀并记录
                proc.destroyForcibly()
                Log.w(
                    SU_LOG_TAG,
                    "bringUpInterface TIMEOUT (3s). cmd=$setupCmd, out=${outBuf.take(4000)}"
                )
                return
            }

            val code = proc.exitValue()
            if (code != 0) {
                Log.w(
                    SU_LOG_TAG,
                    "bringUpInterface FAILED: exit=$code, cmd=$setupCmd, out=${outBuf.take(4000)}"
                )
            } else {
                Log.i(
                    SU_LOG_TAG,
                    "bringUpInterface OK: cmd=$setupCmd, out=${outBuf.take(1000)}"
                )
            }

            // 等 gobbler 自然退出一下（不阻塞太久）
            tOut.join(50)
        } catch (e: Exception) {
            Log.w(
                SU_LOG_TAG,
                "bringUpInterface exception: ${e.message}, cmd=$setupCmd"
            )
            try {
                proc?.destroyForcibly()
            } catch (_: Throwable) {
            }
        }
    }
}
