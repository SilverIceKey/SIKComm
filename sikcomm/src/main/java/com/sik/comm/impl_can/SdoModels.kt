package com.sik.comm.impl_can

sealed class SdoRequest {
    abstract val nodeId: Int
    abstract val index: Int
    abstract val subIndex: Int
    abstract val canIdOverride: Int?
    abstract val timeoutMs: Long

    data class Read(
        override val nodeId: Int,
        override val index: Int,
        override val subIndex: Int = 0,
        override val canIdOverride: Int? = null,
        override val timeoutMs: Long = 3000
    ) : SdoRequest()

    data class Write(
        override val nodeId: Int,
        override val index: Int,
        override val subIndex: Int = 0,
        val payload: ByteArray,
        /** 1/2/4，仅限 expedited */
        val size: Int,
        override val canIdOverride: Int? = null,
        override val timeoutMs: Long = 3000
    ) : SdoRequest()
}

enum class SdoOp { READ_REQ, READ_RSP, WRITE_REQ, WRITE_ACK, ERROR }

sealed class SdoResponse(val op: SdoOp) {
    data class ReadData(
        val nodeId: Int,
        val index: Int,
        val subIndex: Int,
        val canId: Int,
        val payload: ByteArray  // 长度=1/2/4（expedited），或设备自定义
    ) : SdoResponse(SdoOp.READ_RSP)

    data class WriteAck(
        val nodeId: Int,
        val index: Int,
        val subIndex: Int,
        val canId: Int
    ) : SdoResponse(SdoOp.WRITE_ACK)

    data class Error(
        val nodeId: Int,
        val index: Int,
        val subIndex: Int,
        val canId: Int,
        val abortCode: Long,
        val raw: ByteArray
    ) : SdoResponse(SdoOp.ERROR)
}