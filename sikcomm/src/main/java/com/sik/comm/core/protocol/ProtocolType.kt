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
