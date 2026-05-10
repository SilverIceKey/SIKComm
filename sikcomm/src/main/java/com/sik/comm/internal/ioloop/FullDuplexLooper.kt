package com.sik.comm.internal.ioloop

import com.sik.comm.CommReceiver
import com.sik.comm.internal.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全双工 IO 循环器。
 *
 * 适用于 SocketCAN 等物理全双工、读写可真正并发的通道。
 *
 * 策略：
 * - 独立 readLoop 协程持续阻塞读
 * - send() 直接在 IO 线程调用 transport.write，不经过队列
 */
internal class FullDuplexLooper(
    private val transport: Transport,
    private val readTimeoutMs: Int,
    private val bufferSize: Int = 72  // CAN FD 最大 64 字节 payload + 余量
) {

    /**
     * 启动读循环。
     */
    fun startReadLoop(scope: CoroutineScope, handle: Long, receiverProvider: () -> CommReceiver?): Job {
        return scope.launch {
            val buffer = ByteArray(bufferSize)

            while (isActive) {
                val n = transport.read(handle, buffer, readTimeoutMs)
                when {
                    n > 0 -> receiverProvider()?.onBytesReceived(buffer, 0, n)
                    n < 0 -> break  // 发生错误，退出循环
                    // n == 0: 超时，继续下一轮
                }
            }
        }
    }

    /**
     * 直接写（全双工，不阻塞读循环）。
     */
    suspend fun writeDirect(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        return withContext(Dispatchers.IO) {
            transport.write(handle, data, offset, length, timeoutMs)
        }
    }
}
