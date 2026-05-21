package com.sik.comm.internal.ioloop

import com.sik.comm.CommReceiver
import com.sik.comm.internal.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 半双工读优先 IO 循环器。
 *
 * 适用于 Serial / USB-Serial / USB-HID 等需要「读优先、串行收发」的通道。
 *
 * 循环策略：
 * 1. 每轮先调用 transport.read()（阻塞直到有数据或超时）
 * 2. 若本轮没有读到数据，再尝试处理一条写请求
 * 3. 写完后回到下一轮，继续先读后写
 *
 * @param transport            底层传输
 * @param readTimeoutMs        读超时
 * @param bufferSize           读缓冲区大小
 * @param readErrorFilter      读错误过滤：返回 true 表示该错误码可忽略（继续循环），false 表示退出循环
 * @param onReadSuccess        读成功后的回调（可用于日志、计数等）
 */
internal class HalfDuplexIoLooper(
    private val transport: Transport,
    private val readTimeoutMs: Int,
    private val bufferSize: Int = 4096,
    private val readErrorFilter: (errorCode: Int) -> Boolean = { false },
    private val onReadSuccess: ((bytesRead: Int) -> Unit)? = null
) {

    private val writeQueue = Channel<WriteJob>(Channel.UNLIMITED)

    /**
     * 启动 IO 循环。
     *
     * @param scope            协程作用域
     * @param handle           已打开的 transport 句柄
     * @param receiverProvider 接收回调提供者（支持动态替换）
     * @return                 IO 循环 Job
     */
    fun start(scope: CoroutineScope, handle: Long, receiverProvider: () -> CommReceiver?): Job {
        return scope.launch {
            val buffer = ByteArray(bufferSize)

            while (isActive) {
                val n = transport.read(handle, buffer, readTimeoutMs)
                when {
                    n > 0 -> {
                        onReadSuccess?.invoke(n)
                        receiverProvider()?.onBytesReceived(buffer, 0, n)
                        continue
                    }

                    n < 0 -> {
                        if (!readErrorFilter(n)) {
                            break
                        }
                        // 错误被过滤，继续去处理写队列
                    }

                    // n == 0: 读超时，无数据，转去处理写队列
                }

                // 尝试处理一条写请求
                val job = writeQueue.tryReceive().getOrNull()
                if (job != null) {
                    val written = transport.write(handle, job.data, 0, job.data.size, job.timeoutMs)
                    if (written >= 0) {
                        job.result.complete(written)
                    } else {
                        job.result.completeExceptionally(
                            IllegalStateException("Write failed with error=$written")
                        )
                    }
                }
            }
        }
    }

    /**
     * 异步发送数据。挂起直到写入完成或异常。
     */
    suspend fun send(bytes: ByteArray, timeoutMs: Int): Int {
        val job = WriteJob(
            data = bytes.copyOf(),
            timeoutMs = timeoutMs
        )
        writeQueue.send(job)
        return job.result.await()
    }

    /**
     * 清空写队列，取消所有挂起的写入请求，并关闭 Channel 防止后续数据堆积。
     */
    fun shutdown() {
        writeQueue.close()
        while (true) {
            val job = writeQueue.tryReceive().getOrNull() ?: break
            job.result.completeExceptionally(
                IllegalStateException("Channel closed before write completed")
            )
        }
    }

    private data class WriteJob(
        val data: ByteArray,
        val timeoutMs: Int,
        val result: CompletableDeferred<Int> = CompletableDeferred()
    )
}
