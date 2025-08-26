package com.sik.comm.impl_can

/**
 * 轻量级 CAN 帧结构，适配标准帧（11-bit）。
 *
 * @param canId 标准帧 ID（0x000..0x7FF）
 * @param dlc   数据长度（0..8，SDO 固定 8）
 * @param data  数据区，未使用的字节用 0 填充
 */
data class CanFrame(
    val canId: Int,
    val dlc: Int = 8,
    val data: ByteArray
) {
    init {
        require(canId in 0x000..0x7FF) { "CAN ID must be standard (11 bit)." }
        require(dlc in 0..8) { "DLC must be 0..8." }
        require(data.size == dlc || data.size == 8) { "Data must be size 8 or dlc exact." }
    }
}
