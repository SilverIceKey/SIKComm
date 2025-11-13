package com.sik.comm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 串口通道实现（适用于 RS232 / RS485 / USB-Serial）。
 *
 * 特性：
 * - 使用单独一个 IO 协程负责所有 JNI 调用（read / write）
 * - 严格保证“要么发，要么收”：同一时刻只有 read 或 write 在执行，不会并发
 * - IO 循环策略：**读优先**
 *   1. 每轮先调用一次 JNI read()（内部 poll + read，阻塞直到有数据或超时）
 *   2. 若本轮没有读到数据，再尝试处理一个写请求（从 writeQueue 非阻塞取一条）
 *   3. 写完回到下一轮，继续先读后写
 *
 * 这样可以保证：
 * - 485 半双工场景不会在收包过程中插入 write 导致包中断
 * - 写队列再长，中间也会夹杂 read，不会饿死接收
 * - Kotlin while 循环不会空转，真正阻塞在 JNI 的 poll 上
 */
internal class SerialChannelImpl(
    private val config: SerialConfig
) : CommChannel {

    override val id: String
        get() = config.id

    /**
     * 专用 IO 协程作用域。
     * 所有 JNI 调用（read/write）都限定在该作用域内。
     */
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * JNI 层返回的句柄（通常是 fd），0 表示尚未打开或已关闭。
     */
    @Volatile
    private var handle: Long = 0L

    /**
     * IO 循环对应的 Job。
     */
    private var ioJob: Job? = null

    /**
     * 当前设置的接收回调。
     * 通过 setReceiver() 设置 / 替换。
     */
    @Volatile
    private var receiver: CommReceiver? = null

    /**
     * 写请求队列。
     *
     * - 所有 send() 调用都会投递一个 WriteJob 到这里
     * - IO 协程在每轮 read 之后，非阻塞尝试从队列取出一条写请求处理
     * - 使用 Channel.UNLIMITED，避免业务高频 send 时直接挂起
     */
    private val writeQueue: Channel<WriteJob> =
        Channel(capacity = Channel.UNLIMITED)

    override fun open() {
        if (isOpen()) {
            // 幂等：已经打开就直接返回
            return
        }

        // 调 JNI 打开串口并配置 termios
        val fd = NativeSerial.open(
            config.devicePath,
            config.baudRate,
            config.dataBits,
            config.stopBits,
            config.parity
        )

        require(fd > 0L) {
            "Failed to open serial port: ${config.devicePath}, handle=$fd"
        }

        handle = fd

        // 启动 IO 循环
        startIoLoop()
    }

    override fun close() {
        // 停止 IO 循环
        ioJob?.cancel()
        ioJob = null

        // 关闭底层 fd
        val fd = handle
        if (fd != 0L) {
            NativeSerial.close(fd)
            handle = 0L
        }

        // 不再复用该通道时，可以直接取消整个 scope
        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) {
            "SerialChannelImpl#send called when channel is not open (id=$id)"
        }

        val t = timeoutMs ?: config.writeTimeoutMs

        // 这里可以选择 copy 或直接用原数组：
        // - 如果业务层会复用/修改原数组，建议 copy
        // - 如果确认不会修改，可直接用 bytes
        val job = WriteJob(
            data = bytes.copyOf(),
            timeoutMs = t
        )

        // 投递写请求给 IO 协程
        writeQueue.send(job)

        // 挂起等待写结果
        return job.result.await()
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.receiver = receiver
    }

    /**
     * 启动 IO 循环：
     *
     * while (active && open) {
     *   1. 先尝试 read()
     *   2. 若本轮没有读到数据，再尝试处理一条 writeQueue 里的写请求
     * }
     *
     * read() / write() 内部由 JNI 使用 poll() 控制阻塞与超时，
     * 因此 while 循环不会空转。
     */
    private fun startIoLoop() {
        ioJob = scope.launch {
            val buffer = ByteArray(4096)

            while (isActive && isOpen()) {
                // -------- 1. 先读 --------
                val fdForRead = handle
                if (fdForRead == 0L) {
                    // 已关闭，退出循环
                    break
                }

                val n = NativeSerial.read(
                    fdForRead,
                    buffer,
                    0,
                    buffer.size,
                    config.readTimeoutMs
                )

                when {
                    n > 0 -> {
                        // 读到了数据，优先处理接收
                        receiver?.onBytesReceived(buffer, 0, n)
                        // 读取到数据后，继续下一轮循环，继续 read 优先
                        continue
                    }

                    n < 0 -> {
                        // 发生错误，直接退出循环（也可以在这里打标记）
                        break
                    }

                    // n == 0 -> 读超时，无数据，转去看看写队列
                }

                // -------- 2. 再尝试处理一条写请求 --------
                val writeJob = writeQueue.tryReceive().getOrNull()
                if (writeJob != null) {
                    val fdForWrite = handle
                    if (fdForWrite == 0L) {
                        // 已关闭，不再写，通知调用方失败
                        writeJob.result.completeExceptionally(
                            IllegalStateException("Serial handle is closed during write (id=$id)")
                        )
                        continue
                    }

                    val written = NativeSerial.write(
                        fdForWrite,
                        writeJob.data,
                        0,
                        writeJob.data.size,
                        writeJob.timeoutMs
                    )

                    writeJob.result.complete(written)

                    // 写完后继续 loop，下一轮又会先尝试 read()
                    continue
                }

                // -------- 3. 当前既没读到数据，也没有写任务 --------
                // 直接进入下一轮循环，继续 read() + writeQueue 检查
            }
        }
    }

    /**
     * 表示一次写操作请求。
     *
     * @param data      要写入的字节数据
     * @param timeoutMs 写操作超时时间（毫秒）
     * @param result    写入完成之后的结果（>=0: 写入字节数；<0:错误码或异常）
     */
    private data class WriteJob(
        val data: ByteArray,
        val timeoutMs: Int,
        val result: CompletableDeferred<Int> = CompletableDeferred()
    )
}
