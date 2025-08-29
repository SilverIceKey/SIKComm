// CanSdoDialect.kt
package com.sik.comm.impl_can

/**
 * 可配置的 SDO 命令字表（数值统一用 Int）
 */
data class SdoDialect(
    val READ: Int,         // initiate upload request
    val READ_1B: Int,     // expedited download 1 byte
    val READ_2B: Int,     // expedited download 2 bytes
    val READ_4B: Int,     // expedited download 4 bytes
    val READ_ERROR: Int,
    val WRITE_1B: Int,     // expedited download 1 byte
    val WRITE_2B: Int,     // expedited download 2 bytes
    val WRITE_4B: Int,     // expedited download 4 bytes
    val WRITE_ACK: Int,    // download response
    val WRITE_ERROR: Int         // abort transfer
) {
    companion object {
        /** 标准 CANopen（CiA 301） */
        val CANOPEN = SdoDialect(
            READ = 0x40,
            READ_1B = 0x2F,
            READ_2B = 0x2B,
            READ_4B = 0x23,
            READ_ERROR = 0x80,
            WRITE_1B = 0x2F,
            WRITE_2B = 0x2B,
            WRITE_4B = 0x23,
            WRITE_ACK = 0x60,
            WRITE_ERROR = 0x80
        )
    }
}
