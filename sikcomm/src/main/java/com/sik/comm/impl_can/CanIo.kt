package com.sik.comm.impl_can

import java.io.Closeable

/**
 * 底层 I/O 抽象。
 *
 * 你可以在 SocketCAN/厂商 SDK 上实现该接口：
 * - open(): 根据接口名打开通道（如 "can0"）
 * - write(): 发送一帧
 * - read(): 读取一帧（阻塞或带超时），无数据时返回 null
 */
interface CanIo : Closeable {
    fun open(interfaceName: String)
    fun isOpen(): Boolean
    fun write(frame: CanFrame)
    fun read(): CanFrame?  // 应实现为阻塞式读取，或内部阻塞 + 返回帧
    override fun close()
}