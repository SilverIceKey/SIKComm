/*
 * Copyright 2025 折千
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
