package com.sik.comm.impl_ble.io

import com.sik.comm.impl_ble.GattRoute

/** BLE I/O 抽象（阻塞式 API，经由 suspend 暴露） */
interface BleIo : AutoCloseable {
    suspend fun open(mac: String)
    suspend fun isOpen(): Boolean
    suspend fun discover(): Unit
    suspend fun subscribe(route: GattRoute): Unit
    suspend fun requestMtu(expect: Int?): Int
    suspend fun write(route: GattRoute, payload: ByteArray, withResponse: Boolean)
    suspend fun read(timeoutMs: Long): ByteArray?        // 从通知队列读一帧
    override fun close()
}