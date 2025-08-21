package com.sik.comm.codec

import com.sik.comm.core.codec.MessageCodec
import com.sik.comm.core.model.CommMessage

/**
 * 长度前缀编解码器（2 字节，大端），帧格式：
 *
 * [LEN_H][LEN_L][PAYLOAD]
 *
 * - LEN = payload 的长度（不含自身2字节）
 * - 适合常见的 TCP/串口自定义协议，便于拆包、粘包处理（配合协议实现层缓冲区）
 */
class LengthPrefixedCodec : MessageCodec {

    override fun encode(msg: CommMessage): ByteArray {
        val body = msg.payload
        require(body.size <= 0xFFFF) { "Payload too large: ${body.size}" }
        val lenHigh = ((body.size shr 8) and 0xFF).toByte()
        val lenLow  = (body.size and 0xFF).toByte()
        return byteArrayOf(lenHigh, lenLow) + body
    }

    override fun decode(data: ByteArray): CommMessage {
        require(data.size >= 2) { "Frame too short for length header." }
        val len = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        require(data.size == 2 + len) { "Frame length mismatch. expect=$len, actual=${data.size - 2}" }

        val payload = data.copyOfRange(2, 2 + len)
        return CommMessage(
            command = "LEN_FRAME",
            payload = payload,
            metadata = mapOf("len" to len)
        )
    }
}
