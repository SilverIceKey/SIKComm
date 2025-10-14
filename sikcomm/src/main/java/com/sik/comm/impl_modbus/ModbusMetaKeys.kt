package com.sik.comm.impl_modbus

/**
 * Modbus 协议使用到的 metadata 统一键值。
 */
object ModbusMetaKeys {
    const val UNIT_ID = "unitId"
    const val FUNCTION_CODE = "functionCode"
    const val ADDRESS = "address"
    const val QUANTITY = "quantity"
    const val TIMEOUT = "timeoutMs"
    const val EXPECTED_LENGTH = "expectedLength"
    const val SILENCE_GAP = "silenceGapMs"
    const val RAW_FRAME = "rawFrame"
    const val RAW_PAYLOAD = "rawPayload"
    const val CRC = "crc"
    const val EXCEPTION_CODE = "exceptionCode"
}
