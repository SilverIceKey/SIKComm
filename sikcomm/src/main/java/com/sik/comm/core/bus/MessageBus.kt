package com.sik.comm.core.bus

import com.sik.comm.core.model.CommMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 全局消息总线：支持异步上报/广播消息
 */
object MessageBus {

    // Replay=0 表示不缓存历史，Buffer=64 可调
    private val _events = MutableSharedFlow<Pair<String, CommMessage>>(extraBufferCapacity = 64)
    val events: SharedFlow<Pair<String, CommMessage>> = _events

    /** 协议实现层调用：上报一个收到的消息 */
    suspend fun emit(deviceId: String, msg: CommMessage) {
        _events.emit(deviceId to msg)
    }
}
