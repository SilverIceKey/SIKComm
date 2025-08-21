package com.sik.comm.impl_can.link

import com.sik.comm.impl_can.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MockCanLink(
    override val config: CanLinkConfig.MockConfig
) : CanLink {
    override var isOpen: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rx = MutableSharedFlow<CANFrame>(extraBufferCapacity = 64)
    override val frames = rx.asSharedFlow()

    override suspend fun open() { isOpen = true }

    override suspend fun send(frame: CANFrame): Boolean {
        if (!isOpen) return false
        if (config.echo) rx.tryEmit(frame)
        return true
    }

    override fun close() {
        isOpen = false
        scope.cancel()
    }
}
