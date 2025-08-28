package com.sik.comm.impl_can

import com.sik.comm.core.model.CommMessage
import java.util.Locale

/**
 * CommMessage -> SdoRequest
 * - 更宽容：智能识别读/写；写缺 SIZE 时尝试用 payload.size 推断；payload 超长自动裁剪
 */
fun CommMessage.toSdoRequest(
    fallbacks: () -> Triple<Int?, Int, Int> // (defaultNodeId, txBaseId, rxBaseId) — 这里只用到 defaultNodeId
): SdoRequest {
    val meta = metadata
    val (defaultNodeId, _, _) = fallbacks()

    val nodeId = (meta[MetaKeys.NODE_ID] as? Number)?.toInt()
        ?: defaultNodeId
        ?: error("nodeId missing")

    val index = (meta[MetaKeys.INDEX] as? Number)?.toInt()
        ?: error("index missing")
    val sub   = (meta[MetaKeys.SUBINDEX] as? Number)?.toInt() ?: 0
    val canId = (meta[MetaKeys.CAN_ID] as? Number)?.toInt()
    val timeout = (meta[MetaKeys.TIMEOUT] as? Number)?.toLong() ?: 3000L

    // —— 读/写判定（更智能）——
    val cmd = command.orEmpty().uppercase(Locale.ROOT)
    val isRead = (meta[MetaKeys.IS_READ] as? Boolean)
        ?: when {
            cmd == "SDO_READ" || cmd == "READ" || cmd == "READ_REQ" -> true
            cmd == "SDO_WRITE" || cmd == "WRITE" || cmd == "WRITE_REQ" -> false
            // 没带 SIZE，默认按“读”
            MetaKeys.SIZE !in meta -> true
            else -> false
        }

    if (isRead) {
        return SdoRequest.Read(nodeId, index, sub, canId, timeout)
    }

    // —— 写请求：推断/校验 size，并裁剪 payload —— //
    var size = (meta[MetaKeys.SIZE] as? Number)?.toInt()
    if (size == null) {
        size = when (payload.size) {
            1, 2, 4 -> payload.size
            else -> error("size missing for write (payload.size=${payload.size} not 1/2/4)")
        }
    }
    require(size == 1 || size == 2 || size == 4) { "invalid write size=$size; must be 1/2/4" }
    require(payload.isNotEmpty()) { "empty payload for write" }

    val effPayload =
        if (payload.size >= size) payload.copyOf(size) // 自动裁剪
        else error("payload length ${payload.size} < size $size")

    return SdoRequest.Write(nodeId, index, sub, effPayload, size, canId, timeout)
}

/**
 * SdoRequest -> CommMessage
 * - 读请求 payload 为空；写请求携带真实 payload 并带 SIZE
 */
fun SdoRequest.toCommMessage(): CommMessage = when (this) {
    is SdoRequest.Read -> CommMessage(
        command = SdoOp.READ_REQ.name,
        payload = ByteArray(0),
        metadata = buildMap {
            put(MetaKeys.NODE_ID, this@toCommMessage.nodeId)
            put(MetaKeys.INDEX, this@toCommMessage.index)
            put(MetaKeys.SUBINDEX, this@toCommMessage.subIndex)
            canIdOverride?.let { put(MetaKeys.CAN_ID, it) }
            put(MetaKeys.TIMEOUT, this@toCommMessage.timeoutMs)
            put(MetaKeys.IS_READ, true)
        }
    )
    is SdoRequest.Write -> CommMessage(
        command = SdoOp.WRITE_REQ.name,
        payload = payload,
        metadata = buildMap {
            put(MetaKeys.NODE_ID, this@toCommMessage.nodeId)
            put(MetaKeys.INDEX, this@toCommMessage.index)
            put(MetaKeys.SUBINDEX, this@toCommMessage.subIndex)
            put(MetaKeys.SIZE, this@toCommMessage.size)
            canIdOverride?.let { put(MetaKeys.CAN_ID, it) }
            put(MetaKeys.TIMEOUT, this@toCommMessage.timeoutMs)
            put(MetaKeys.IS_READ, false)
        }
    )
}

/**
 * SdoResponse -> CommMessage
 */
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
            "abort" to abortCode // 若你有 MetaKeys.ABORT 可替换
        )
    )
}
