package com.sik.comm.internal.transport

import com.sik.comm.SerialConfig
import org.junit.Assert.*
import org.junit.Test

class MockTransportTest {

    @Test
    fun `open returns positive handle`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)
        assertTrue("handle should be > 0", handle > 0)
        assertTrue("handle should be valid", transport.isHandleValid(handle))
    }

    @Test
    fun `open failure when configured`() {
        val transport = MockTransport()
        transport.nextOpenFail = true
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)
        assertTrue("handle should be <= 0 on failure", handle <= 0)
    }

    @Test
    fun `write records data and returns length`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val data = byteArrayOf(0x01, 0x02, 0x03)
        val written = transport.write(handle, data, 0, data.size, 100)

        assertEquals(data.size, written)
        val records = transport.writeRecords()
        assertEquals(1, records.size)
        assertArrayEquals(data, records[0].data)
    }

    @Test
    fun `read returns injected data`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val expected = byteArrayOf(0x0A, 0x0B)
        transport.injectRead(handle, expected)

        val buffer = ByteArray(1024)
        val n = transport.read(handle, buffer, 100)

        assertEquals(expected.size, n)
        assertArrayEquals(expected, buffer.copyOfRange(0, n))
    }

    @Test
    fun `read returns 0 when no data`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val buffer = ByteArray(1024)
        val n = transport.read(handle, buffer, 100)

        assertEquals(0, n)
    }

    @Test
    fun `write triggers auto response`() {
        val transport = MockTransport()
        transport.autoResponse = byteArrayOf(0xAA.toByte(), 0xBB.toByte())

        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        transport.write(handle, byteArrayOf(0x01), 0, 1, 100)

        val buffer = ByteArray(1024)
        val n = transport.read(handle, buffer, 100)
        assertEquals(2, n)
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), buffer.copyOfRange(0, n))
    }

    @Test
    fun `read error when configured`() {
        val transport = MockTransport()
        transport.nextReadError = -99

        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)

        val buffer = ByteArray(1024)
        val n = transport.read(handle, buffer, 100)

        assertEquals(-99, n)
    }

    @Test
    fun `close invalidates handle`() {
        val transport = MockTransport()
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val handle = transport.open(config)
        assertTrue(transport.isHandleValid(handle))

        transport.close(handle)
        assertFalse(transport.isHandleValid(handle))
    }
}
