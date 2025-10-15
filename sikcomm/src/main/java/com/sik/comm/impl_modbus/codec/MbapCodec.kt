package com.sik.comm.impl_modbus.codec

import com.sik.comm.core.codec.MessageCodec
import com.sik.comm.core.model.CommMessage

/**
 * Modbus TCP (MBAP) 编解码：
 *   [TID_H][TID_L][PID_H=0][PID_L=0][LEN_H][LEN_L][UID][PDU]
 * - 无 CRC；LEN = UID(1) + PDU(N)
 */
class MbapCodec : MessageCodec {

    private fun be16(x: Int) = byteArrayOf(((x ushr 8) and 0xFF).toByte(), (x and 0xFF).toByte())

    override fun encode(msg: CommMessage): ByteArray {
        val payload = msg.payload // 这里要求已是 [UID][PDU...]
        require(payload.size >= 2) { "MBAP encode requires [UID][PDU...]" }
        val tid = (System.nanoTime().toInt() and 0xFFFF)
        val len = payload.size
        return be16(tid) + byteArrayOf(0x00, 0x00) + be16(len) + payload
    }

    override fun decode(data: ByteArray): CommMessage {
        require(data.size >= 7) { "MBAP frame too short" }
        val len = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        require(data.size == 6 + len) { "MBAP len mismatch" }
        val payload = data.copyOfRange(7, data.size) // 去掉 UID
        val uid = data[6].toInt() and 0xFF
        return CommMessage(
            command = "MBAP",
            payload = byteArrayOf(uid.toByte()) + payload,
            metadata = mapOf("uid" to uid, "len" to len)
        )
    }
}
