package com.sik.comm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SocketCAN 通道实现。
 *
 * 特性：
 * - 真全双工：读写分离
 *   - 独立 readLoop 协程阻塞在 JNI read()（内部 poll + recv）
 *   - send() 中直接在 Dispatchers.IO 上调用 JNI write()
 * - CommChannel 接口保持与串口一致，上层不用关心区别
 *
 * 当前实现只把 CAN payload 当作普通字节流上抛。
 * 如果你需要使用 frameId / flags，可在此基础上扩接口或单独封装。
 */
internal class CanChannelImpl(
    private val config: CanConfig
) : CommChannel {

    override val id: String
        get() = config.id

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var handle: Long = 0L

    private var readJob: Job? = null

    @Volatile
    private var receiver: CommReceiver? = null

    override fun open() {
        if (isOpen()) return

        // 可选：由 JNI 负责 bringUp，如果 bitrate 未配置则跳过
        val bitrate = config.bitrate
        if (bitrate != null) {
            // fdMode 目前 JNI 里是 NO-OP，你可以后续扩展
            NativeCan.bringUp(config.ifName, bitrate, config.fdMode)
        }

        val fd = NativeCan.open(config.ifName)
        require(fd > 0L) {
            "Failed to open CAN interface: ${config.ifName}, handle=$fd"
        }

        handle = fd

        // 启动读循环
        startReadLoop()
    }

    override fun close() {
        readJob?.cancel()
        readJob = null

        val fd = handle
        if (fd != 0L) {
            NativeCan.close(fd)
            handle = 0L
        }

        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    /**
     * 发送 CAN payload。
     *
     * 当前实现未区分 frameId / flags：
     * - 暂时用 frameId = 0 / flags = 0
     * - 如果你有具体协议，可以在 CommChannel 之上再包一层，或者扩展 send()
     */
    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) {
            "CanChannelImpl#send called when channel is not open (id=$id)"
        }

        val t = timeoutMs ?: config.writeTimeoutMs
        val fd = handle
        if (fd == 0L) {
            throw IllegalStateException("CAN handle is closed during send (id=$id)")
        }

        // 真全双工：发送直接在 IO 线程执行，不阻塞读循环
        return withContext(Dispatchers.IO) {
            NativeCan.write(
                fd,
                /*frameId=*/0,      // TODO: 按实际协议填充
                /*flags=*/0,        // TODO: 按实际需求设置扩展帧/RTR/FD 等
                bytes,
                0,
                bytes.size,
                t
            )
        }
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.receiver = receiver
    }

    /**
     * 启动 CAN 读循环：
     * - 一直阻塞在 JNI read()，内部使用 poll 等待数据或超时
     * - 读到数据就通过 CommReceiver 回调扔给上层
     */
    private fun startReadLoop() {
        readJob = scope.launch {
            val buffer = ByteArray(72) // 经典 CAN 8 字节，CAN FD 最多 64，72 足够
            val frameIdArr = IntArray(1)
            val flagsArr = IntArray(1)

            while (isActive && isOpen()) {
                val fd = handle
                if (fd == 0L) break

                val n = NativeCan.read(
                    fd,
                    frameIdArr,
                    flagsArr,
                    buffer,
                    0,
                    buffer.size,
                    config.readTimeoutMs
                )

                when {
                    n > 0 -> {
                        // 这里只把 payload 字节上抛
                        // frameId / flags 可通过单独接口或自定义 receiver 扩展
                        receiver?.onBytesReceived(buffer, 0, n)
                    }

                    n < 0 -> {
                        // 发生错误，直接退出循环（你也可以改成重试 / 标记错误状态）
                        break
                    }

                    // n == 0 -> 读超时，无数据，继续下一轮
                }
            }
        }
    }
}
