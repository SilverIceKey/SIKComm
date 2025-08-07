package com.sik.comm.core.state

import com.sik.comm.core.model.ProtocolState

/**
 * DeviceState - 单个通信设备的状态快照结构体。
 *
 * 用于 DeviceStateCenter 保存每个设备当前详细状态。
 * 相比简单的 ProtocolState 枚举，这里聚合了多维度状态信息（如连接、任务、异常）。
 *
 * 应用场景：
 * - 状态中心统一状态管理
 * - 插件 / UI 获取完整设备状态快照
 * - 异常重试、日志分析、心跳判断等使用
 */
data class DeviceState(

    /**
     * 当前设备 ID（如 BLE MAC、TCP IP、串口路径等）。
     */
    val deviceId: String,

    /**
     * 协议状态：离线 / 连接中 / 已就绪 / 忙碌 / 错误等。
     */
    val protocolState: ProtocolState = ProtocolState.DISCONNECTED,

    /**
     * 是否已建立底层连接（true 表示物理层连接已建立）。
     */
    val isConnected: Boolean = false,

    /**
     * 当前设备是否忙碌（即有未完成的任务在执行）。
     */
    val isBusy: Boolean = false,

    /**
     * 最后一次活跃时间戳（用于心跳 / 超时判断）。
     */
    val lastActiveTime: Long = System.currentTimeMillis(),

    /**
     * 最近一次异常信息（如连接失败 / 发送超时等）。
     */
    val lastError: Throwable? = null
)
