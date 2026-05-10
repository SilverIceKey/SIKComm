package com.sik.comm.internal.pipeline

import com.sik.comm.CommReceiver
import com.sik.comm.QrAssemblePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QrAssembleStageTest {

    private fun createScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `merges multiple chunks within window`() = runBlocking {
        val scope = createScope()
        val policy = QrAssemblePolicy(mergeWindowMs = 200L, resetTimeoutSeconds = 3, dropCrLf = true)
        val stage = QrAssembleStage(policy, scope)

        val received = mutableListOf<String>()
        val downstream = CommReceiver { data, offset, length ->
            received.add(String(data.copyOfRange(offset, offset + length), Charsets.UTF_8))
        }

        stage.process("hello".toByteArray(), 0, 5, downstream)
        stage.process("world".toByteArray(), 0, 5, downstream)

        delay(300)  // 超过 200ms 窗口

        assertEquals(1, received.size)
        assertEquals("helloworld", received[0])

        scope.cancel()
    }

    @Test
    fun `drops cr and lf when configured`() = runBlocking {
        val scope = createScope()
        val policy = QrAssemblePolicy(mergeWindowMs = 200L, resetTimeoutSeconds = 3, dropCrLf = true)
        val stage = QrAssembleStage(policy, scope)

        val received = mutableListOf<String>()
        val downstream = CommReceiver { data, offset, length ->
            received.add(String(data.copyOfRange(offset, offset + length), Charsets.UTF_8))
        }

        val chunk = byteArrayOf('a'.code.toByte(), 0x0D, 'b'.code.toByte(), 0x0A, 'c'.code.toByte())
        stage.process(chunk, 0, chunk.size, downstream)

        delay(300)

        assertEquals(1, received.size)
        assertEquals("abc", received[0])

        scope.cancel()
    }

    @Test
    fun `keeps cr and lf when not configured to drop`() = runBlocking {
        val scope = createScope()
        val policy = QrAssemblePolicy(mergeWindowMs = 200L, resetTimeoutSeconds = 3, dropCrLf = false)
        val stage = QrAssembleStage(policy, scope)

        val received = mutableListOf<String>()
        val downstream = CommReceiver { data, offset, length ->
            received.add(String(data.copyOfRange(offset, offset + length), Charsets.UTF_8))
        }

        val chunk = byteArrayOf('a'.code.toByte(), 0x0D, 'b'.code.toByte())
        stage.process(chunk, 0, chunk.size, downstream)

        delay(300)

        assertEquals(1, received.size)
        assertEquals("a\rb", received[0])

        scope.cancel()
    }

    @Test
    fun `resets after timeout`() = runBlocking {
        val scope = createScope()
        val policy = QrAssemblePolicy(mergeWindowMs = 200L, resetTimeoutSeconds = 1, dropCrLf = true)
        val stage = QrAssembleStage(policy, scope)

        val received = mutableListOf<String>()
        val downstream = CommReceiver { data, offset, length ->
            received.add(String(data.copyOfRange(offset, offset + length), Charsets.UTF_8))
        }

        // 第一组数据
        stage.process("first".toByteArray(), 0, 5, downstream)
        delay(300)
        assertEquals(1, received.size)
        assertEquals("first", received[0])

        // 等超过 1s，旧数据应该被清空
        delay(1200)

        // 新数据不应该和旧数据拼接
        stage.process("second".toByteArray(), 0, 6, downstream)
        delay(300)

        assertEquals(2, received.size)
        assertEquals("second", received[1])

        scope.cancel()
    }

    @Test
    fun `empty chunk does nothing`() = runBlocking {
        val scope = createScope()
        val policy = QrAssemblePolicy(mergeWindowMs = 200L, resetTimeoutSeconds = 3, dropCrLf = true)
        val stage = QrAssembleStage(policy, scope)

        val received = mutableListOf<String>()
        val downstream = CommReceiver { data, offset, length ->
            received.add(String(data.copyOfRange(offset, offset + length), Charsets.UTF_8))
        }

        stage.process(byteArrayOf(), 0, 0, downstream)
        delay(300)

        assertTrue(received.isEmpty())
        scope.cancel()
    }

    @Test
    fun `all crlf chunk is dropped`() = runBlocking {
        val scope = createScope()
        val policy = QrAssemblePolicy(mergeWindowMs = 200L, resetTimeoutSeconds = 3, dropCrLf = true)
        val stage = QrAssembleStage(policy, scope)

        val received = mutableListOf<String>()
        val downstream = CommReceiver { data, offset, length ->
            received.add(String(data.copyOfRange(offset, offset + length), Charsets.UTF_8))
        }

        val chunk = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A)
        stage.process(chunk, 0, chunk.size, downstream)
        delay(300)

        assertTrue(received.isEmpty())
        scope.cancel()
    }
}
