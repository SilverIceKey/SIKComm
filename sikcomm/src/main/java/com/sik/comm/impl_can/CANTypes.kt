@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.sik.comm.impl_can

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/**
 * 表示一帧 CAN 数据（标准/扩展/远程）
 */
data class CANFrame(
    val id: Int,
    val dlc: Int,
    val data: ByteArray = ByteArray(0),
    val extended: Boolean = false,
    val remote: Boolean = false
) {
    init {
        require(id >= 0)
        require(dlc in 0..8)
        require(data.size == dlc) { "data.size(${data.size}) != dlc($dlc)" }
    }
}

/** Link 的统一配置 */
sealed class CanLinkConfig {
    data class SocketCanConfig(val ifname: String) : CanLinkConfig()
    data class SlcanUsbConfig(
        val vendorId: Int? = null,
        val productId: Int? = null,
        val deviceNameContains: String? = null,
        val bitrate: Int = 1_000_000
    ) : CanLinkConfig()
    data class SlcanTcpConfig(
        val host: String,
        val port: Int,
        val bitrate: Int = 1_000_000
    ) : CanLinkConfig()
    data class BleNusConfig(
        val mac: String,
        val serviceUUID: java.util.UUID,
        val txUUID: java.util.UUID, // 写
        val rxUUID: java.util.UUID, // 读(Notify)
        val mtu: Int = 180,
        val bitrate: Int = 1_000_000
    ) : CanLinkConfig()
    data class MockConfig(val echo: Boolean = true) : CanLinkConfig()
}

/**
 * 底层 Link 抽象
 */
interface CanLink : Closeable {
    val config: CanLinkConfig
    val isOpen: Boolean
    val frames: Flow<CANFrame>

    suspend fun open()
    suspend fun send(frame: CANFrame): Boolean

    suspend fun setBitrate(bps: Int) {}
    suspend fun setLoopback(loop: Boolean) {}
    suspend fun setListenOnly(listenOnly: Boolean) {}
}
