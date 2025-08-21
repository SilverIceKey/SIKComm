package com.sik.comm.impl_can.link

import com.sik.comm.impl_can.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 预留 SocketCAN（需 JNI；Android 通常无 PF_CAN）
 */
class SocketCanLink(
    override val config: CanLinkConfig.SocketCanConfig
) : CanLink {

    override var isOpen: Boolean = false
        private set

    private val rx = MutableSharedFlow<CANFrame>(extraBufferCapacity = 256)
    override val frames = rx.asSharedFlow()

    override suspend fun open() {
        error("SocketCAN not implemented on this platform")
    }

    override suspend fun send(frame: CANFrame): Boolean = false

    override fun close() { isOpen = false }
}
