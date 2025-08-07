package com.sik.comm.core.model

/**
 * 通信消息统一结构。
 * 作为所有协议层发送和接收的标准数据容器。
 *
 * 可以通过拦截器、编解码器等扩展 payload 的封装方式。
 */
data class CommMessage(

    /** 命令类型，通常为设备的功能指令（如 GET_TOKEN、SET_MODE） */
    val command: String,

    /** 原始数据内容，二进制格式（如 CRC 校验后数据） */
    val payload: ByteArray,

    /** 附加元数据（可选），例如超时时间、标识 ID、额外参数等 */
    val metadata: Map<String, Any> = emptyMap()
)
