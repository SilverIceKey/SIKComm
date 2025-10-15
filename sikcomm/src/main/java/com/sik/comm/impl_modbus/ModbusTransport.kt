package com.sik.comm.impl_modbus

import java.io.Closeable

/**
 * Modbus 传输层抽象，屏蔽 RS485 与 TCP 的差异。
 */
interface ModbusTransport : Closeable {

    /** 打开底层连接。 */
    fun open()

    /** 当前传输层是否已经就绪。 */
    fun isOpen(): Boolean

    /** 写入原始帧数据。 */
    fun write(data: ByteArray)

    /**
     * 读取一帧响应。
     *
     * @param timeoutMs    本次等待的最大时长
     * @param expectedSize 若已知长度，则达到该长度即可返回
     * @param silenceGapMs 判定帧结束的静默时长
     */
    fun readFrame(timeoutMs: Int, expectedSize: Int?, silenceGapMs: Int): ByteArray
}
