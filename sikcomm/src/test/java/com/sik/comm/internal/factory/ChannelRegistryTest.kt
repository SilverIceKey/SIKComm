package com.sik.comm.internal.factory

import com.sik.comm.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelRegistryTest {

    @Before
    fun setUp() {
        ChannelRegistry.clear()
    }

    @Test
    fun `register and resolve factory`() {
        var created = false
        val factory = ChannelFactory { config ->
            created = true
            object : CommChannel {
                override val id: String = (config as SerialConfig).id
                override fun open() {}
                override fun close() {}
                override fun isOpen(): Boolean = false
                override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int = 0
                override fun setReceiver(receiver: CommReceiver?) {}
            }
        }

        ChannelRegistry.register(SerialConfig::class, factory)

        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val resolved = ChannelRegistry.resolve(config)
        assertNotNull(resolved)

        resolved!!.create(config)
        assertTrue(created)
    }

    @Test
    fun `resolve unknown config returns null`() {
        val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)
        val resolved = ChannelRegistry.resolve(config)
        assertNull(resolved)
    }

    @Test
    fun ` SikComm init registers built-in factories`() {
        // SikComm 的 init 块会在类加载时注册工厂
        // 这里只需要引用一下 SikComm 触发 init
        SikComm.toString()

        val serial = SerialConfig(id = "s", devicePath = "/dev/ttyS0", baudRate = 9600)
        val can = CanConfig(id = "c", ifName = "can0")
        val usbSerial = UsbSerialConfig(id = "u", context = org.mockito.Mockito.mock(android.content.Context::class.java))
        val usbHid = UsbHidConfig(id = "h", context = org.mockito.Mockito.mock(android.content.Context::class.java))

        assertNotNull(ChannelRegistry.resolve(serial))
        assertNotNull(ChannelRegistry.resolve(can))
        assertNotNull(ChannelRegistry.resolve(usbSerial))
        assertNotNull(ChannelRegistry.resolve(usbHid))
    }
}
