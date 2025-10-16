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

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.protocol.ProtocolType

/**
 * 插件执行上下文。
 *
 * 提供设备在插件中的运行环境，让插件可以根据设备信息、状态或通信内容作出行为响应。
 *
 * 每次插件生命周期方法触发时，都会传入 PluginScope：
 * - 表示当前事件来自哪个设备
 * - 当前设备使用的是哪种协议类型
 * - 当前设备的配置详情
 * - 若涉及通信，还会传入消息本体
 *
 * 插件可用于：
 * - 状态监听（连接中 / 就绪 / 断开）
 * - 自动心跳
 * - 自动重连
 * - 日志上报
 * - 忙碌保护（避免连接期间被其他业务中断）
 */
data class PluginScope(

    /**
     * 当前通信设备的唯一标识。
     * 与 InterceptorScope 一致，如 BLE 为 MAC 地址。
     */
    val deviceId: String,

    /**
     * 当前设备所使用的通信协议类型。
     */
    val protocolType: ProtocolType,

    /**
     * 当前设备的配置对象，继承自 ProtocolConfig。
     */
    val config: ProtocolConfig,

    /**
     * 设备当前状态。
     * 用于判断 READY / BUSY / ERROR 等状态。
     */
    val state: ProtocolState,

    /**
     * 如果事件与通信消息有关（如 send/receive），会带上此消息。
     * 否则为 null。
     */
    val message: CommMessage? = null
)
