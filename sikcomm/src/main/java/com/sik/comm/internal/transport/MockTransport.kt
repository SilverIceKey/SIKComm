package com.sik.comm.internal.transport

import com.sik.comm.CommConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 模拟 Transport，用于无硬件环境下的单元测试和集成测试。
 *
 * 能力：
 * - 模拟设备 open / close
 * - 模拟发送后延迟响应
 * - 模拟主动推送（如扫码枪定时发数据）
 * - 模拟错误场景（超时、断开、半包等）
 * - 记录所有读写操作，方便断言
 */
internal class MockTransport : Transport {

    private val handleGen = AtomicLong(1)
    private val handles = ConcurrentHashMap<Long, MockHandle>()
    private val records = CopyOnWriteArrayList<Record>()

    // ---------- 测试控制 ----------

    /** 下一次 open() 是否失败 */
    @Volatile
    var nextOpenFail: Boolean = false

    /** 下一次 read() 返回的错误码 */
    @Volatile
    var nextReadError: Int? = null

    /** 下一次 write() 返回的错误码 */
    @Volatile
    var nextWriteError: Int? = null

    /** 固定 read 响应队列（FIFO） */
    private val readQueue = ArrayDeque<ByteArray>()

    /** 收到 write 后自动触发的响应（模拟请求-应答） */
    @Volatile
    var autoResponse: ByteArray? = null

    /** read 延迟（毫秒） */
    @Volatile
    var readDelayMs: Long = 0

    /** write 延迟（毫秒） */
    @Volatile
    var writeDelayMs: Long = 0

    // ---------- Transport 实现 ----------

    override fun open(config: CommConfig): Long {
        if (nextOpenFail) {
            nextOpenFail = false
            return -1L
        }
        val h = handleGen.getAndIncrement()
        handles[h] = MockHandle(config)
        return h
    }

    override fun close(handle: Long) {
        handles.remove(handle)
    }

    override fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        if (readDelayMs > 0) Thread.sleep(readDelayMs)

        nextReadError?.let {
            nextReadError = null
            return it
        }

        val data = synchronized(readQueue) {
            readQueue.removeFirstOrNull()
        } ?: return 0

        val len = minOf(data.size, buffer.size)
        System.arraycopy(data, 0, buffer, 0, len)
        return len
    }

    override fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        if (writeDelayMs > 0) Thread.sleep(writeDelayMs)

        nextWriteError?.let {
            nextWriteError = null
            return it
        }

        val slice = data.copyOfRange(offset, offset + length)
        records.add(Record.Write(handle, slice))

        autoResponse?.let { resp ->
            synchronized(readQueue) {
                readQueue.addLast(resp)
            }
        }

        return length
    }

    // ---------- 测试辅助方法 ----------

    /** 向指定 handle 注入待读取数据 */
    fun injectRead(handle: Long, data: ByteArray) {
        synchronized(readQueue) {
            readQueue.addLast(data)
        }
    }

    /** 获取所有写入记录 */
    fun writeRecords(): List<Record.Write> = records.filterIsInstance<Record.Write>()

    /** 清空记录 */
    fun clearRecords() {
        records.clear()
    }

    /** 断开指定 handle（后续 read/write 返回错误） */
    fun disconnect(handle: Long) {
        handles.remove(handle)
    }

    /** 判断 handle 是否仍然有效 */
    fun isHandleValid(handle: Long): Boolean = handles.containsKey(handle)

    // ---------- 内部数据 ----------

    private data class MockHandle(val config: CommConfig)

    sealed class Record {
        data class Write(val handle: Long, val data: ByteArray) : Record()
    }
}
