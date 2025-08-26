// CanSdoDialect.kt
package com.sik.comm.impl_can

/**
 * 可配置的 SDO 命令字表（数值统一用 Int）
 */
data class SdoDialect(
    val READ: Int,         // initiate upload request
    val WRITE_1B: Int,     // expedited download 1 byte
    val WRITE_2B: Int,     // expedited download 2 bytes
    val WRITE_4B: Int,     // expedited download 4 bytes
    val WRITE_ACK: Int,    // download response
    val ERROR: Int         // abort transfer
) {
    companion object {
        /** 标准 CANopen（CiA 301） */
        val CANOPEN = SdoDialect(
            READ      = 0x40,
            WRITE_1B  = 0x2F,
            WRITE_2B  = 0x2B,
            WRITE_4B  = 0x23,
            WRITE_ACK = 0x60,
            ERROR     = 0x80
        )
        /** 示例：某私有方言（按需替换数值） */
        val VENDOR_X = SdoDialect(
            READ      = 0x41,
            WRITE_1B  = 0xAF,
            WRITE_2B  = 0xAB,
            WRITE_4B  = 0xA3,
            WRITE_ACK = 0xE0,
            ERROR     = 0x90
        )
    }
}
