package com.sik.comm.impl_modbus.easy

import com.sik.comm.core.model.CommMessage
import com.sik.comm.impl_modbus.ModbusFrame
import com.sik.comm.impl_modbus.fromComm
import com.sik.comm.impl_modbus.toComm
import com.sik.comm.impl_modbus.ModbusProtocol

class ModbusClient(
    private val protocol: ModbusProtocol,
    private val deviceId: String,
    private val defaultUnitId: Int = 1
) {

    suspend fun readHoldingRegisters(addr: Int, qty: Int, unitId: Int = defaultUnitId): ShortArray {
        val req = buildReadRequest(unitId, 0x03, addr, qty)
        val rsp = sendAndParse(req)
        return parseRegisters(rsp, qty)
    }

    suspend fun readInputRegisters(addr: Int, qty: Int, unitId: Int = defaultUnitId): ShortArray {
        val req = buildReadRequest(unitId, 0x04, addr, qty)
        val rsp = sendAndParse(req)
        return parseRegisters(rsp, qty)
    }

    suspend fun readCoils(addr: Int, qty: Int, unitId: Int = defaultUnitId): BooleanArray {
        val req = buildReadRequest(unitId, 0x01, addr, qty)
        val rsp = sendAndParse(req)
        return parseCoilsOrDiscreteInputs(rsp, qty)
    }

    suspend fun readDiscreteInputs(addr: Int, qty: Int, unitId: Int = defaultUnitId): BooleanArray {
        val req = buildReadRequest(unitId, 0x02, addr, qty)
        val rsp = sendAndParse(req)
        return parseCoilsOrDiscreteInputs(rsp, qty)
    }

    suspend fun writeSingleRegister(addr: Int, value: Int, unitId: Int = defaultUnitId) {
        val req = buildWriteSingleRequest(unitId, 0x06, addr, value)
        sendAndParse(req) // 正常返回即成功（异常会抛）
    }

    suspend fun writeMultipleRegisters(addr: Int, regs: ShortArray, unitId: Int = defaultUnitId) {
        val req = buildWriteMultipleRegisters(unitId, addr, regs)
        sendAndParse(req)
    }

    private suspend fun sendAndParse(req: ModbusFrame.Request): ModbusFrame.Response {
        val comm = req.toComm() // 复用现有转换器:contentReference[oaicite:10]{index=10}
        val rsp: CommMessage = protocol.send(deviceId, comm) // 复用现有协议发送:contentReference[oaicite:11]{index=11}
        return rsp.fromComm() // CRC/异常校验已在这里做了:contentReference[oaicite:12]{index=12}
    }
}
