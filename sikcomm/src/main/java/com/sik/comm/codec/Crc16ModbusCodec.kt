package com.sik.comm.codec

import com.sik.comm.core.codec.MessageCodec
import com.sik.comm.core.model.CommMessage

/**
 * CRC16/Modbus 编解码器，帧格式：
 *
 * [PAYLOAD...][CRC_L][CRC_H]
 *
 * - CRC 使用 Modbus 多项式 0xA001（即 0x8005 反射），初值 0xFFFF
 * - 输出顺序低字节在前（Little Endian），符合 Modbus RTU 习惯
 *
 * 适用：RS485（Modbus RTU）或你自定义帧也想用该校验方式。
 * 注意：若你的协议还有“地址/功能码/长度”等头字段，请在 encode 时先拼好 payload，再交给本 Codec。
 */
class Crc16ModbusCodec : MessageCodec {

    override fun encode(msg: CommMessage): ByteArray {
        val body = msg.payload
        val crc = calcCrc16Modbus(body)
        val crcL = (crc and 0xFF).toByte()
        val crcH = ((crc shr 8) and 0xFF).toByte()
        return body + byteArrayOf(crcL, crcH)
    }

    override fun decode(data: ByteArray): CommMessage {
        require(data.size >= 3) { "Frame too short for CRC16." }
        val payloadLen = data.size - 2
        val payload = data.copyOfRange(0, payloadLen)

        val crcL = data[payloadLen].toInt() and 0xFF
        val crcH = data[payloadLen + 1].toInt() and 0xFF
        val crcInFrame = (crcH shl 8) or crcL

        val crcCalc = calcCrc16Modbus(payload)
        require(crcInFrame == crcCalc) {
            "CRC mismatch. inFrame=0x${crcInFrame.toString(16)}, calc=0x${crcCalc.toString(16)}"
        }

        return CommMessage(
            command = "CRC16_MODBUS",
            payload = payload,
            metadata = mapOf("crc" to crcCalc)
        )
    }

    /**
     * 计算 CRC16/Modbus（多项式 0xA001，初值 0xFFFF）
     */
    private fun calcCrc16Modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    (crc ushr 1)
                }
            }
        }
        return crc and 0xFFFF
    }
}
