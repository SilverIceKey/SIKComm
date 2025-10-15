package com.sik.comm.core.protocol

import com.sik.comm.core.model.CommMessage

/**
 * “裸”I/O，直接对接传输层：写/读原始帧。
 * - writeRaw: 直接把 sent.payload 写出去
 * - readRaw : 按策略需要读取一帧
 */
interface LinkIO {
    suspend fun writeRaw(msg: CommMessage)
    suspend fun readRaw(timeoutMs: Int, expectedSize: Int?, silenceGapMs: Int): CommMessage
}