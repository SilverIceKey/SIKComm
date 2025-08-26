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