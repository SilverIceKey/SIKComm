package com.sik.comm.impl_modbus.easy

import com.sik.comm.impl_modbus.ModbusFrame

class ModbusException(val code: Int) : RuntimeException("Modbus exception: 0x${code.toString(16)}")

/** 读 0x01/0x02: 位域 -> BooleanArray */
fun parseCoilsOrDiscreteInputs(resp: ModbusFrame.Response, expectedQty: Int): BooleanArray {
    if (resp.exceptionCode != null) throw ModbusException(resp.exceptionCode)
    // PDU: [fc][byteCount][data...]
    val pdu = resp.pdu
    require(pdu.size >= 2) { "Invalid PDU" }
    val byteCount = pdu[1].toInt() and 0xFF
    require(pdu.size == 2 + byteCount) { "Invalid length" }
    val out = BooleanArray(expectedQty)
    var idx = 0
    for (i in 0 until byteCount) {
        val b = pdu[2 + i].toInt() and 0xFF
        for (bit in 0 until 8) {
            if (idx >= expectedQty) break
            out[idx++] = (b and (1 shl bit)) != 0
        }
    }
    return out
}

/** 读 0x03/0x04: 16-bit 整数数组（Big Endian） */
fun parseRegisters(resp: ModbusFrame.Response, expectedQty: Int): ShortArray {
    if (resp.exceptionCode != null) throw ModbusException(resp.exceptionCode)
    val pdu = resp.pdu
    require(pdu.size >= 2) { "Invalid PDU" }
    val byteCount = pdu[1].toInt() and 0xFF
    require(byteCount == expectedQty * 2) { "Unexpected byteCount=$byteCount" }
    require(pdu.size == 2 + byteCount) { "Invalid length" }
    val out = ShortArray(expectedQty)
    for (i in 0 until expectedQty) {
        val hi = pdu[2 + i * 2].toInt() and 0xFF
        val lo = pdu[3 + i * 2].toInt() and 0xFF
        out[i] = (((hi shl 8) or lo).toShort())
    }
    return out
}
