package com.sik.comm.impl_modbus

import com.sik.comm.core.model.CommMessage
import com.sik.comm.impl_modbus.ModbusMessageConverter.buildRtuFrame
import com.sik.comm.impl_modbus.ModbusMessageConverter.crc16
import com.sik.comm.impl_modbus.ModbusMessageConverter.hex16
import com.sik.comm.impl_modbus.ModbusMetaKeys.ADDRESS
import com.sik.comm.impl_modbus.ModbusMetaKeys.CRC
import com.sik.comm.impl_modbus.ModbusMetaKeys.FUNCTION_CODE
import com.sik.comm.impl_modbus.ModbusMetaKeys.QUANTITY
import com.sik.comm.impl_modbus.ModbusMetaKeys.RAW_FRAME
import com.sik.comm.impl_modbus.ModbusMetaKeys.RAW_PAYLOAD
import com.sik.comm.impl_modbus.ModbusMetaKeys.SILENCE_GAP
import com.sik.comm.impl_modbus.ModbusMetaKeys.TIMEOUT
import com.sik.comm.impl_modbus.ModbusMetaKeys.UNIT_ID

/**
 * 参考 SDO 模型的“请求/响应”风格，做一个轻量 Modbus 模型，便于和 CommMessage 互转。
 * 注意：此处实现 Modbus RTU 帧:  [unitId][PDU...][CRC_lo][CRC_hi]
 *  - PDU = [functionCode][data]
 *  - CRC 为标准 Modbus CRC16 (poly 0xA001, 初值 0xFFFF, 低字节在前)
 */
sealed class ModbusFrame {
    abstract val unitId: Int
    abstract val function: Int

    /** 原始 PDU（不含 unitId / CRC） */
    abstract val pdu: ByteArray

    data class Request(
        override val unitId: Int,
        override val function: Int,
        val address: Int? = null,     // 读/写寄存器/线圈时的起始地址
        val quantity: Int? = null,    // 读数量/写数量
        override val pdu: ByteArray,  // 纯 PDU: [fc][data]
        val timeoutMs: Int? = 3000,
        val silenceGapMs: Int? = 30
    ) : ModbusFrame()

    data class Response(
        override val unitId: Int,
        override val function: Int,
        /** 正常响应时的负载。异常响应时为空，exceptionCode 非空 */
        override val pdu: ByteArray,
        val exceptionCode: Int? = null
    ) : ModbusFrame()
}

/**
 * Modbus <-> CommMessage 转换器
 * - toComm:   ModbusFrame.Request -> CommMessage（payload 即 RTU 完整帧）
 * - fromComm: CommMessage -> ModbusFrame.Response（自动识别异常帧：fc|0x80）
 *
 * 约定：
 *  - command 使用 "MODBUS_FC_xx"（便于链路层/日志按功能码归类）
 *  - metadata 使用 ModbusMetaKeys 统一键
 */
object ModbusMessageConverter {

    // ----------------- helpers -----------------

    /** [unitId][PDU...][CRC_lo][CRC_hi] */
    fun buildRtuFrame(unitId: Int, pdu: ByteArray): ByteArray {
        val body = ByteArray(1 + pdu.size)
        body[0] = unitId.toByte()
        System.arraycopy(pdu, 0, body, 1, pdu.size)
        val crc = crc16(body)
        val out = ByteArray(body.size + 2)
        System.arraycopy(body, 0, out, 0, body.size)
        out[out.lastIndex - 1] = (crc and 0xFF).toByte()       // lo
        out[out.lastIndex] = ((crc ushr 8) and 0xFF).toByte()  // hi
        return out
    }

    /** 标准 Modbus CRC16 (poly 0xA001, init 0xFFFF)，返回 16-bit 无符号值 */
    fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                val lsb = crc and 0x0001
                crc = crc ushr 1
                if (lsb == 1) crc = crc xor 0xA001
            }
        }
        return crc and 0xFFFF
    }

    fun hex16(hi: Int, lo: Int) =
        "0x" + ((hi shl 8) or lo).toString(16).uppercase().padStart(4, '0')
}



/** Request -> CommMessage （RTU 帧落到 payload，关键信息入 metadata） */
fun ModbusFrame.Request.toComm(): CommMessage {
    require(unitId in 0..0xFF) { "unitId out of range" }
    require(function in 0..0xFF) { "functionCode out of range" }
    require(pdu.isNotEmpty() && pdu[0].toInt() and 0xFF == function) {
        "PDU must start with functionCode"
    }

    val adu = ModbusMessageConverter.buildRtuFrame(unitId, pdu) // 完整 RTU 帧
    val crc =
        ((adu[adu.size - 1].toInt() and 0xFF) shl 8) or (adu[adu.size - 2].toInt() and 0xFF)

    val meta = buildMap<String, Any> {
        put(UNIT_ID, unitId)
        put(FUNCTION_CODE, function)
        address?.let { put(ADDRESS, it) }
        quantity?.let { put(QUANTITY, it) }
        timeoutMs?.let { put(TIMEOUT, it) }
        silenceGapMs?.let { put(SILENCE_GAP, it) }
        put(RAW_PAYLOAD, pdu) // 仅 PDU
        put(RAW_FRAME, adu)       // 完整帧
        put(CRC, crc)
    }

    val command = "MODBUS_FC_${function.toString(16).uppercase().padStart(2, '0')}"
    return CommMessage(
        command = command,
        payload = adu,
        metadata = meta
    )
}

/**
 * CommMessage -> Response
 * - 自动检验并解析 RTU 帧；若功能码最高位被置位（fc|0x80），提取异常码到 metadata / model
 * - 你也可以先在链路层做 CRC 校验；这里冗余再验一次，出错直接抛
 */
fun CommMessage.fromComm(): ModbusFrame.Response {
    val frame = payload
    require(frame.size >= 4) { "Frame too short" }
    val unitId = frame[0].toInt() and 0xFF
    val fc = frame[1].toInt() and 0xFF

    // 校验 CRC
    val body = frame.copyOfRange(0, frame.size - 2)
    val crcLo = frame[frame.size - 2].toInt() and 0xFF
    val crcHi = frame[frame.size - 1].toInt() and 0xFF
    val expect = ModbusMessageConverter.crc16(body)
    val expectLo = expect and 0xFF
    val expectHi = (expect ushr 8) and 0xFF
    require(crcLo == expectLo && crcHi == expectHi) {
        "CRC mismatch: got ${ModbusMessageConverter.hex16(crcHi, crcLo)} expect ${hex16(expectHi, expectLo)}"
    }

    val isException = (fc and 0x80) != 0
    return if (isException) {
        val ex = if (frame.size >= 5) frame[2].toInt() and 0xFF else 0xFF
        ModbusFrame.Response(
            unitId = unitId,
            function = fc and 0x7F,
            pdu = byteArrayOf((fc and 0x7F).toByte(), ex.toByte()), // 最简保留
            exceptionCode = ex
        )
    } else {
        // 正常响应：PDU = [fc][data...]
        val pdu = body.copyOfRange(1, body.size)  // 注意：body[0]=unitId，body[1]=fc
        // 还原成含功能码的 PDU
        val fullPdu = ByteArray(pdu.size + 1).also {
            it[0] = fc.toByte()
            System.arraycopy(pdu, 0, it, 1, pdu.size)
        }
        ModbusFrame.Response(
            unitId = unitId,
            function = fc,
            pdu = fullPdu,
            exceptionCode = null
        )
    }
}
