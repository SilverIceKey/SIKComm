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

package com.sik.comm.core.protocol

/**
 * 通信协议类型枚举。
 * 每种类型在 ProtocolManager 中应注册对应实现。
 */
enum class ProtocolType {

    // 基础通信协议
    BLE,            // 蓝牙低功耗（Bluetooth Low Energy）
    TCP,            // TCP/IP 协议
    RS485,          // 串口通信（ModBus RTU 等）

    // 工业场景常见
    CAN,            // CAN 总线通信（常用于车载、工业设备）
    SERIAL,         // 通用串口（RS232/TTL）

    // 虚拟/辅助
    MOCK,           // 模拟通信，用于测试开发
    CUSTOM          // 自定义扩展类型（可在实现类中区分子类型）
}
