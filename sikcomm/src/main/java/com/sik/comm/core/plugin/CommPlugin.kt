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

package com.sik.comm.core.plugin

/**
 * 通信插件接口。
 *
 * 插件可挂载在设备生命周期中各个阶段，对连接状态、通信行为进行感知或干预。
 * 可应用于：
 * - 心跳保持（定时发送）
 * - 重连机制（断开时尝试重连）
 * - 状态上报（连接状态变更时打点）
 * - 日志追踪（收发前后打印）
 *
 * 每个设备配置（ProtocolConfig）可注册多个插件。
 */
interface CommPlugin {

    /**
     * 插件初始化时触发。
     * 可用于启动定时器、注册监听器等。
     */
    fun onAttach(scope: PluginScope) {}

    /**
     * 插件销毁时触发（如设备断开或卸载插件）。
     * 可用于释放资源、移除监听等。
     */
    fun onDetach(scope: PluginScope) {}

    /**
     * 状态变更时触发。
     * 例如连接中 → 就绪 → 断开。
     */
    fun onStateChanged(scope: PluginScope) {}

    /**
     * 消息即将发送时触发。
     * 可用于做埋点、日志、消息节流。
     */
    fun onBeforeSend(scope: PluginScope) {}

    /**
     * 收到响应消息时触发。
     * 可用于分析响应、判断失败重试等。
     */
    fun onReceive(scope: PluginScope) {}

    /**
     * 异常或错误发生时触发。
     * 如发送失败、连接异常等。
     */
    fun onError(scope: PluginScope, throwable: Throwable) {}
}
