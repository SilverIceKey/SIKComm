package com.sik.comm.codec

import com.sik.comm.core.codec.MessageCodec
import com.sik.comm.core.model.CommMessage

/**
 * 直通编解码器（不做任何头尾/CRC/长度处理）。
 *
 * 适用场景：
 * - 上层 payload 已经是完整帧
 * - 或者仅做最简 TCP/Mock 验证链路
 */
class PassThroughCodec : MessageCodec {

    override fun encode(msg: CommMessage): ByteArray {
        // 直接把 payload 原样下发
        return msg.payload
    }

    override fun decode(data: ByteArray): CommMessage {
        // 解析为一个“未知命令”的消息，payload 即原始数据
        return CommMessage(
            command = msgCommandFromMetaOrUnknown(data),
            payload = data,
            metadata = emptyMap()
        )
    }

    private fun msgCommandFromMetaOrUnknown(data: ByteArray): String = "UNKNOWN"
}
