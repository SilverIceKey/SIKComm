package com.sik.comm.impl_can

import com.sik.comm.core.model.CommMessage

fun CommMessage.toSdoRequest(
    fallbacks: () -> Triple<Int?, Int, Int> // (defaultNodeId, txBaseId, rxBaseId) from config
): SdoRequest {
    val meta = metadata
    val (defaultNodeId, txBaseId, _) = fallbacks()
    val nodeId = (meta[MetaKeys.NODE_ID] as? Number)?.toInt()
        ?: defaultNodeId
        ?: error("nodeId missing")

    val index = (meta[MetaKeys.INDEX] as? Number)?.toInt()
        ?: error("index missing")
    val sub   = (meta[MetaKeys.SUBINDEX] as? Number)?.toInt() ?: 0
    val canId = (meta[MetaKeys.CAN_ID] as? Number)?.toInt() // 可选
    val timeout = (meta[MetaKeys.TIMEOUT] as? Number)?.toLong() ?: 3000L

    val isRead = (meta[MetaKeys.IS_READ] as? Boolean) ?: command.equals("SDO_READ", true)
    return if (isRead) {
        SdoRequest.Read(nodeId, index, sub, canId, timeout)
    } else {
        val size = (meta[MetaKeys.SIZE] as? Number)?.toInt()
            ?: error("size missing for write")
        require(size == 1 || size == 2 || size == 4)
        require(payload.size >= size)
        SdoRequest.Write(nodeId, index, sub, payload, size, canId, timeout)
    }
}

/**
 * 将强类型 SdoRequest 转为通用 CommMessage。
 *
 * 用途：
 * - 插件 onBeforeSend/onReceive 统一收/发消息对象
 * - 方便拦截器链处理请求日志、Mock等
 */
fun SdoRequest.toCommMessage(): CommMessage = when (this) {
    is SdoRequest.Read -> CommMessage(
        command = SdoOp.READ_REQ.name,
        payload = ByteArray(0), // 读请求没有负载
        metadata = buildMap {
            put(MetaKeys.NODE_ID, nodeId)
            put(MetaKeys.INDEX, index)
            put(MetaKeys.SUBINDEX, subIndex)
            canIdOverride?.let { put(MetaKeys.CAN_ID, it) }
            put(MetaKeys.TIMEOUT, timeoutMs)
            put(MetaKeys.IS_READ, true)
        }
    )

    is SdoRequest.Write -> CommMessage(
        command = SdoOp.WRITE_REQ.name,
        payload = payload, // 写请求携带真实数据
        metadata = buildMap {
            put(MetaKeys.NODE_ID, nodeId)
            put(MetaKeys.INDEX, index)
            put(MetaKeys.SUBINDEX, subIndex)
            put(MetaKeys.SIZE, size)
            canIdOverride?.let { put(MetaKeys.CAN_ID, it) }
            put(MetaKeys.TIMEOUT, timeoutMs)
            put(MetaKeys.IS_READ, false)
        }
    )
}

fun SdoResponse.toCommMessage(): CommMessage = when (this) {
    is SdoResponse.ReadData -> CommMessage(
        command = SdoOp.READ_RSP.name,
        payload = payload,
        metadata = mapOf(
            MetaKeys.NODE_ID to nodeId,
            MetaKeys.INDEX to index,
            MetaKeys.SUBINDEX to subIndex,
            MetaKeys.CAN_ID to canId,
            MetaKeys.SIZE to payload.size
        )
    )
    is SdoResponse.WriteAck -> CommMessage(
        command = SdoOp.WRITE_ACK.name,
        payload = ByteArray(0),
        metadata = mapOf(
            MetaKeys.NODE_ID to nodeId,
            MetaKeys.INDEX to index,
            MetaKeys.SUBINDEX to subIndex,
            MetaKeys.CAN_ID to canId
        )
    )
    is SdoResponse.Error -> CommMessage(
        command = SdoOp.ERROR.name,
        payload = raw,
        metadata = mapOf(
            MetaKeys.NODE_ID to nodeId,
            MetaKeys.INDEX to index,
            MetaKeys.SUBINDEX to subIndex,
            MetaKeys.CAN_ID to canId,
            "abort" to abortCode
        )
    )
}