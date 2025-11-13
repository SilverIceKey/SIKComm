package com.sik.comm

/**
 * 通用接收回调接口。
 *
 * 所有从底层（串口 / CAN）读到的原始字节，都会通过该回调上抛给业务层。
 * 业务层可以在这里做：
 * - 黏包处理
 * - 分帧
 * - CRC 校验
 * - 协议解析
 */
fun interface CommReceiver {

    /**
     * 当底层读取到字节数据时触发。
     *
     * @param data   缓冲区数组（注意：实现可以复用 buffer，不保证每次都是新数组）
     * @param offset 数据起始下标
     * @param length 有效数据长度
     */
    fun onBytesReceived(data: ByteArray, offset: Int, length: Int)
}
