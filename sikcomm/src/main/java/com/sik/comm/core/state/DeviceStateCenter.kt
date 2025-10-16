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

package com.sik.comm.core.state

import com.sik.comm.core.model.ProtocolState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 设备状态中心。
 *
 * 管理所有设备的通信状态（ProtocolState），并提供状态变更通知能力。
 * - 可用于 UI 层订阅某设备状态
 * - 插件可根据状态执行不同逻辑（如连接完成启动心跳）
 * - ProtocolManager 可通过此类统一变更状态
 */
object DeviceStateCenter {

    // 内部保存每个设备状态流（线程安全）
    private val stateMap = ConcurrentHashMap<String, MutableStateFlow<ProtocolState>>()

    /**
     * 设置指定设备的状态。
     * 如果状态发生变化，会自动触发观察者更新。
     *
     * @param deviceId 设备唯一标识（如 MAC、IP）
     * @param newState 新的状态值
     */
    fun updateState(deviceId: String, newState: ProtocolState) {
        val flow = stateMap.getOrPut(deviceId) {
            MutableStateFlow(ProtocolState.DISCONNECTED)
        }
        if (flow.value != newState) {
            flow.value = newState
        }
    }

    /**
     * 获取某个设备的状态流（StateFlow）。
     * 可在 UI 层或业务层中订阅此流，获取状态变更。
     *
     * @param deviceId 设备唯一标识
     * @return StateFlow<ProtocolState>
     */
    fun observeState(deviceId: String): StateFlow<ProtocolState> {
        return stateMap.getOrPut(deviceId) {
            MutableStateFlow(ProtocolState.DISCONNECTED)
        }.asStateFlow()
    }

    /**
     * 获取某个设备的当前状态。
     * 若设备尚未注册，默认返回 DISCONNECTED。
     */
    fun getState(deviceId: String): ProtocolState {
        return stateMap[deviceId]?.value ?: ProtocolState.DISCONNECTED
    }
}
