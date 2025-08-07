package com.sik.comm.core.task

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设备任务调度器（DeviceTaskManager）。
 *
 * 用于控制同一设备的操作串行执行，防止以下问题：
 * - BLE 重入导致连接断开
 * - 多个线程同时发起连接或读写
 * - 某些协议（如 Modbus）不允许并发访问
 *
 * 使用方式：
 * ```kotlin
 * DeviceTaskManager.withLock(deviceId) {
 *     // 安全访问设备（协议层发送、状态切换等）
 * }
 * ```
 */
object DeviceTaskManager {

    // 每个设备一个锁（惰性创建）
    private val mutexMap = mutableMapOf<String, Mutex>()

    private fun getMutex(deviceId: String): Mutex {
        return synchronized(mutexMap) {
            mutexMap.getOrPut(deviceId) { Mutex() }
        }
    }

    /**
     * 使用互斥锁封装设备操作，确保串行访问。
     *
     * @param deviceId 设备唯一标识（如 MAC / IP / RS485 编号）
     * @param block 执行的挂起代码块
     */
    suspend fun <T> withLock(deviceId: String, block: suspend () -> T): T {
        val mutex = getMutex(deviceId)
        return mutex.withLock {
            block()
        }
    }

    /**
     * 清除已不再使用的设备锁（可选调用）。
     */
    fun clear(deviceId: String) {
        synchronized(mutexMap) {
            mutexMap.remove(deviceId)
        }
    }

    /**
     * 清空所有锁（一般用于重置或退出应用）。
     */
    fun clearAll() {
        synchronized(mutexMap) {
            mutexMap.clear()
        }
    }
}
