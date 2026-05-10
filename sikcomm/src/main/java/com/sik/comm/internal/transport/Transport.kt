package com.sik.comm.internal.transport

import com.sik.comm.CommConfig

/**
 * 统一底层传输接口。
 *
 * 职责：屏蔽 Serial / CAN / USB-Serial / USB-HID 的底层差异，
 * 向上层提供一致的 open / read / write / close 语义。
 *
 * 错误语义（已在本层统一翻译）：
 * - read:  >0 实际读取字节数；0 超时无数据；<0 传输错误
 * - write: >=0 实际写入字节数；<0 传输错误
 */
internal interface Transport {

    /**
     * 打开底层连接。
     *
     * @param config 通道配置
     * @return       >0: 句柄；<=0: 失败（具体错误由各实现内部打印日志）
     */
    fun open(config: CommConfig): Long

    /**
     * 关闭底层连接。
     */
    fun close(handle: Long)

    /**
     * 读取数据到 buffer。
     *
     * @param handle    open() 返回的句柄
     * @param buffer    读入缓冲区（实现可复用 buffer，不保证每次都是新数据）
     * @param timeoutMs 超时时间（毫秒）
     * @return          >0: 实际读取字节数；0: 超时；<0: 错误
     */
    fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int

    /**
     * 写入数据。
     *
     * @param handle    open() 返回的句柄
     * @param data      待写入数据
     * @param offset    数据起始下标
     * @param length    写入长度
     * @param timeoutMs 超时时间（毫秒）
     * @return          >=0: 实际写入字节数；<0: 错误
     */
    fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int
}
