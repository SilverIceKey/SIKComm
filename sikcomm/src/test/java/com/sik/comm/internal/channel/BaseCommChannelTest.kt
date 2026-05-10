package com.sik.comm.internal.channel

import com.sik.comm.CommException
import com.sik.comm.CommReceiver
import com.sik.comm.SerialConfig
import com.sik.comm.internal.transport.MockTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BaseCommChannelTest {

    @Test
    fun `open success triggers callback`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        var callbackResult: Pair<Boolean, Throwable?>? = null
        channel.setOpenCallback { success, error ->
            callbackResult = success to error
        }

        channel.open()
        assertTrue(channel.isOpen())
        assertEquals(true to null, callbackResult)
    }

    @Test
    fun `open failure triggers callback with error`() {
        val transport = MockTransport()
        transport.nextOpenFail = true
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        var callbackResult: Pair<Boolean, Throwable?>? = null
        channel.setOpenCallback { success, error ->
            callbackResult = success to error
        }

        try {
            channel.open()
            fail("should throw")
        } catch (_: CommException.OpenFailed) {
            // expected
        }

        assertNotNull(callbackResult)
        assertEquals(false, callbackResult?.first)
        assertTrue(callbackResult?.second is CommException.OpenFailed)
    }

    @Test
    fun `callback can be replaced`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        val results1 = mutableListOf<Boolean>()
        val results2 = mutableListOf<Boolean>()

        channel.setOpenCallback { success, _ -> results1.add(success) }
        channel.setOpenCallback { success, _ -> results2.add(success) }

        channel.open()
        assertTrue(results1.isEmpty())
        assertEquals(listOf(true), results2)
    }

    @Test
    fun `callback can be cleared`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        var called = false
        channel.setOpenCallback { _, _ -> called = true }
        channel.setOpenCallback(null)

        channel.open()
        assertFalse(called)
    }

    @Test
    fun `idempotent open only triggers callback once`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        var count = 0
        channel.setOpenCallback { _, _ -> count++ }

        channel.open()
        channel.open()
        channel.open()

        assertEquals(1, count)
        assertTrue(channel.isOpen())
    }

    @Test
    fun `close does not trigger callback`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        var count = 0
        channel.setOpenCallback { _, _ -> count++ }

        channel.open()
        assertEquals(1, count)

        channel.close()
        assertEquals(1, count)  // close 不触发回调
    }

    @Test
    fun `send after close throws NotOpen`() = runBlocking {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val channel = SerialChannelImpl(config, transport)

        channel.open()
        channel.close()

        try {
            channel.send(byteArrayOf(0x01))
            fail("should throw")
        } catch (e: CommException.NotOpen) {
            assertEquals("Channel test is not open", e.message)
        }
    }
}
