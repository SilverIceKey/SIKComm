package com.sik.comm

import com.sik.comm.internal.native.NativeSerial
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NativeSerial JNI 层基础测试。
 *
 * 注意：本测试不依赖真实串口硬件，通过操作不存在的设备路径验证 JNI 调用是否正常。
 * 需要在 Android Emulator 或真机上运行（`./gradlew :sikcomm:connectedDebugAndroidTest`）。
 */
@RunWith(AndroidJUnit4::class)
class NativeSerialTest {

    @Test
    fun `open nonexistent device returns error`() {
        val fd = NativeSerial.open("/dev/ttyNonExistent", 9600, 8, 1, 0)
        assertTrue("open should fail for nonexistent device", fd <= 0)
    }

    @Test
    fun `close invalid handle does not crash`() {
        // 关闭无效句柄不应该崩溃
        NativeSerial.close(0L)
        NativeSerial.close(-1L)
    }

    @Test
    fun `write on invalid handle returns error`() {
        val data = byteArrayOf(0x01, 0x02)
        val written = NativeSerial.write(-1L, data, 0, data.size, 100)
        assertTrue("write should fail on invalid handle", written < 0)
    }

    @Test
    fun `read on invalid handle returns error`() {
        val buffer = ByteArray(1024)
        val n = NativeSerial.read(-1L, buffer, 0, buffer.size, 100)
        assertTrue("read should fail on invalid handle", n < 0)
    }

    @Test
    fun `open close round trip on invalid device`() {
        val fd = NativeSerial.open("/dev/ttyTest9999", 115200, 8, 1, 0)
        if (fd > 0) {
            // 极少数情况下设备名碰巧存在，关闭它
            NativeSerial.close(fd)
        }
        // 主要验证：无论 open 成功还是失败，都不会崩溃
    }
}
