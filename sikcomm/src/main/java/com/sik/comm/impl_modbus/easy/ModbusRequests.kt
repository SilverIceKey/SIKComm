package com.sik.comm.impl_modbus.easy

import com.sik.comm.core.model.CommMessage
import com.sik.comm.impl_modbus.ModbusFrame
import com.sik.comm.impl_modbus.toComm

/** 工具：大端写入 16bit */
private fun be16(x: Int) = byteArrayOf(((x ushr 8) and 0xFF).toByte(), (x and 0xFF).toByte())

/** 构 PDU: [fc][data] */
private fun pdu(fc: Int, data: ByteArray) = byteArrayOf(fc.toByte()) + data

/** 读线圈 0x01 / 读离散量 0x02 / 读保持寄存器 0x03 / 读输入寄存器 0x04 */
fun buildReadRequest(
    unitId: Int,
    function: Int,      // 0x01/0x02/0x03/0x04
    address: Int,
    quantity: Int,
    timeoutMs: Int? = null,
    silenceGapMs: Int? = null
): ModbusFrame.Request {
    require(function in listOf(0x01, 0x02, 0x03, 0x04)) { "Unsupported FC: $function" }
    val data = be16(address) + be16(quantity)
    return ModbusFrame.Request(
        unitId = unitId,
        function = function,
        address = address,
        quantity = quantity,
        pdu = pdu(function, data),
        timeoutMs = timeoutMs,
        silenceGapMs = silenceGapMs
    )
}

/** 写单线圈 0x05 / 写单寄存器 0x06 */
fun buildWriteSingleRequest(
    unitId: Int,
    function: Int,      // 0x05/0x06
    address: Int,
    value: Int,
    timeoutMs: Int? = null,
    silenceGapMs: Int? = null
): ModbusFrame.Request {
    require(function == 0x05 || function == 0x06)
    val data = be16(address) + be16(value)
    return ModbusFrame.Request(
        unitId = unitId,
        function = function,
        address = address,
        quantity = 1,
        pdu = pdu(function, data),
        timeoutMs = timeoutMs,
        silenceGapMs = silenceGapMs
    )
}

/** 写多线圈 0x0F */
fun buildWriteMultipleCoils(
    unitId: Int,
    address: Int,
    values: BooleanArray,
    timeoutMs: Int? = null,
    silenceGapMs: Int? = null
): ModbusFrame.Request {
    val qty = values.size
    val bytes = ByteArray((qty + 7) / 8)
    values.forEachIndexed { i, b ->
        if (b) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
    }
    val byteCount = bytes.size
    val data = be16(address) + be16(qty) + byteCount.toByte() + bytes
    return ModbusFrame.Request(
        unitId, 0x0F, address, qty, pdu(0x0F, data), timeoutMs, silenceGapMs
    )
}

/** 写多寄存器 0x10 */
fun buildWriteMultipleRegisters(
    unitId: Int,
    address: Int,
    regs: ShortArray,
    timeoutMs: Int? = null,
    silenceGapMs: Int? = null
): ModbusFrame.Request {
    val qty = regs.size
    val payload = ByteArray(qty * 2)
    regs.forEachIndexed { i, v ->
        payload[i * 2] = ((v.toInt() ushr 8) and 0xFF).toByte()
        payload[i * 2 + 1] = (v.toInt() and 0xFF).toByte()
    }
    val data = be16(address) + be16(qty) + (qty * 2).toByte() + payload
    return ModbusFrame.Request(
        unitId, 0x10, address, qty, pdu(0x10, data), timeoutMs, silenceGapMs
    )
}

/** 便捷：转 CommMessage（含 RTU 完整帧/CRC 等元数据） */
fun ModbusFrame.Request.toCommMessage(): CommMessage =
    this.toComm() // 复用现有实现:contentReference[oaicite:6]{index=6}
