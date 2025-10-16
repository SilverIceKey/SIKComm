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

package com.sik.comm.core.model

/**
 * 通信协议连接状态枚举。
 *
 * 用于标记设备的当前通信状态，贯穿整个通信生命周期：
 * - 可用于 UI 显示、日志追踪、插件触发
 * - 由设备状态中心（DeviceStateCenter）管理
 * - 每个设备拥有独立的状态
 */
enum class ProtocolState {

    /**
     * 尚未连接。
     * 初始化默认状态，或设备已手动断开连接。
     */
    DISCONNECTED,

    /**
     * 正在建立连接中。
     * 例如：TCP 正在 connect，BLE 正在 scan + connect。
     */
    CONNECTING,

    /**
     * 已连接，但尚未准备好收发。
     * 例如 BLE 已连接，但未订阅 Notify。
     */
    CONNECTED,

    /**
     * 设备已就绪，可稳定进行数据收发。
     */
    READY,

    /**
     * 设备正在处理任务（发命令 / 等待响应）。
     * 在 BUSY 期间不建议主动断开连接。
     */
    BUSY,

    /**
     * 连接失败或通信异常。
     * 可触发插件进行重连 / 上报等操作。
     */
    ERROR
}
