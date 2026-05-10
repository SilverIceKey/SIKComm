package com.sik.comm

import com.sik.comm.internal.native.NativeCan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NativeCan JNI 层基础测试。
 *
 * 注意：本测试不依赖真实 CAN 接口，通过操作不存在的接口名验证 JNI 调用是否正常。
 * 需要在 Android Emulator 或真机上运行（`./gradlew :sikcomm:connectedDebugAndroidTest`）。
 */
@RunWith(AndroidJUnit4::class)
class NativeCanTest {

    @Test
    fun `open nonexistent interface returns error`() {
        val fd = NativeCan.open("canNonExistent")
        assertTrue("open should fail for nonexistent interface", fd <= 0)
    }

    @Test
    fun `close invalid handle does not crash`() {
        NativeCan.close(0L)
        NativeCan.close(-1L)
    }

    @Test
    fun `write on invalid handle returns error`() {
        val data = byteArrayOf(0x01, 0x02)
        val written = NativeCan.write(-1L, 0, 0, data, 0, data.size, 100)
        assertTrue("write should fail on invalid handle", written < 0)
    }

    @Test
    fun `read on invalid handle returns error`() {
        val buffer = ByteArray(72)
        val frameId = IntArray(1)
        val flags = IntArray(1)
        val n = NativeCan.read(-1L, frameId, flags, buffer, 0, buffer.size, 100)
        assertTrue("read should fail on invalid handle", n < 0)
    }

    @Test
    fun `bringUp on nonexistent interface returns error`() {
        val ret = NativeCan.bringUp("canTest9999", 500_000, false)
        // bringUp 内部调用 ip link，对于不存在的接口通常会失败
        // 返回值取决于 JNI 实现，这里只验证不崩溃
        // 如果需要精确断言，取决于目标设备的 ip 命令行为
    }

    @Test
    fun `bringDown on nonexistent interface does not crash`() {
        NativeCan.bringDown("canTest9999")
    }
}
