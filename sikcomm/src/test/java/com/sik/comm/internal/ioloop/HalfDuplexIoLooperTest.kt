package com.sik.comm.internal.ioloop

import com.sik.comm.CommReceiver
import com.sik.comm.internal.transport.MockTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class HalfDuplexIoLooperTest {

    @Test
    fun `looper receives data from transport`() = runBlocking {
        val transport = MockTransport()
        val looper = HalfDuplexIoLooper(transport, readTimeoutMs = 100)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val config = com.sik.comm.SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val received = mutableListOf<ByteArray>()
        val receiver = CommReceiver { data, offset, length ->
            received.add(data.copyOfRange(offset, offset + length))
        }

        val job = looper.start(scope, handle) { receiver }

        transport.injectRead(handle, byteArrayOf(0x01, 0x02))
        transport.injectRead(handle, byteArrayOf(0x03))

        delay(300)

        assertEquals(2, received.size)
        assertArrayEquals(byteArrayOf(0x01, 0x02), received[0])
        assertArrayEquals(byteArrayOf(0x03), received[1])

        job.cancel()
        scope.cancel()
    }

    @Test
    fun `looper sends data through transport`() = runBlocking {
        val transport = MockTransport()
        val looper = HalfDuplexIoLooper(transport, readTimeoutMs = 100)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val config = com.sik.comm.SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val job = looper.start(scope, handle) { null }

        val written = looper.send(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), 100)
        assertEquals(2, written)

        val records = transport.writeRecords()
        assertEquals(1, records.size)
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), records[0].data)

        job.cancel()
        scope.cancel()
    }

    @Test
    fun `looper continues on filtered read error`() = runBlocking {
        val transport = MockTransport()
        val looper = HalfDuplexIoLooper(
            transport = transport,
            readTimeoutMs = 100,
            readErrorFilter = { it == -1 }
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val config = com.sik.comm.SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val received = mutableListOf<ByteArray>()
        val receiver = CommReceiver { data, offset, length ->
            received.add(data.copyOfRange(offset, offset + length))
        }

        val job = looper.start(scope, handle) { receiver }

        // 先注入 -1 错误，再注入正常数据
        transport.nextReadError = -1
        transport.injectRead(handle, byteArrayOf(0x99.toByte()))

        delay(300)

        assertEquals(1, received.size)
        assertArrayEquals(byteArrayOf(0x99.toByte()), received[0])

        job.cancel()
        scope.cancel()
    }

    @Test
    fun `looper breaks on unfiltered read error`() = runBlocking {
        val transport = MockTransport()
        val looper = HalfDuplexIoLooper(transport, readTimeoutMs = 100)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val config = com.sik.comm.SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val job = looper.start(scope, handle) { null }

        transport.nextReadError = -99

        // 等待循环退出
        val result = withTimeoutOrNull(1000) {
            job.join()
            true
        }
        assertTrue("job should complete after unfiltered error", result == true)

        scope.cancel()
    }

    @Test
    fun `shutdown cancels queued writes`() = runBlocking {
        val transport = MockTransport()
        val looper = HalfDuplexIoLooper(transport, readTimeoutMs = 100)

        // 不启动 ioLoop，直接 send 会投递到队列然后永久挂起
        val sendJob = async {
            try {
                looper.send(byteArrayOf(0x01), 100)
                "success"
            } catch (_: IllegalStateException) {
                "cancelled"
            }
        }

        // 给 sendJob 时间把 WriteJob 投递到队列
        delay(50)

        // shutdown 取消队列中的 job
        looper.shutdown()

        // sendJob 应该收到异常并在超时内返回
        val result = withTimeoutOrNull(1000) { sendJob.await() }
        assertEquals("cancelled", result)
    }
}
