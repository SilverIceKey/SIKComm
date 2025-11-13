package com.sik.comm

/**
 * 通用通信配置接口。
 *
 * 所有具体的通道配置（串口、CAN）都实现这个接口。
 * 主要包含：
 * - id: 业务侧标识该通道的一个唯一 ID（比如设备 ID）
 * - readTimeoutMs: 读操作的超时时间（JNI 层 poll 的 timeout）
 * - writeTimeoutMs: 写操作的超时时间（JNI 层 poll 的 timeout）
 */
sealed interface CommConfig {

    /**
     * 业务端定义的通道 ID，用来区分不同设备/连接。
     */
    val id: String

    /**
     * 读操作超时时间（毫秒）。
     *
     * 由 Kotlin 层传给 JNI，JNI 内部通过 poll() 使用该 timeout。
     * 当超时返回时，JNI 应该返回 0（无数据），不抛异常。
     */
    val readTimeoutMs: Int

    /**
     * 写操作超时时间（毫秒）。
     *
     * 同样由 Kotlin 层传给 JNI，通过 poll() 控制写操作超时。
     */
    val writeTimeoutMs: Int
}
