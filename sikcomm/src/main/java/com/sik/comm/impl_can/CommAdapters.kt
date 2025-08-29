package com.sik.comm.impl_can

import com.sik.comm.core.model.CommMessage
import java.util.Locale

// 命令别名（统一大小写）
private val READ_REQ_ALIASES   = setOf("SDO_READ", "READ", "READ_REQ")
private val WRITE_REQ_ALIASES  = setOf("SDO_WRITE", "WRITE", "WRITE_REQ")
private val READ_RSP_ALIASES   = setOf("READ_RSP", "SDO_READ_RSP", "SDO_READ_DONE")
private val WRITE_ACK_ALIASES  = setOf("WRITE_ACK", "SDO_WRITE_ACK", "SDO_WRITE_DONE")
private val ERROR_ALIASES      = setOf("ERROR", "SDO_ERROR", "ABORT")

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
            READ_REQ_ALIASES.contains(cmd)  -> true
            WRITE_REQ_ALIASES.contains(cmd) -> false
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
            MetaKeys.ABORT to abortCode // ✅ 统一用字典键
        )
    )
}

/**
 * CommMessage -> SdoResponse
 * 规则：
 * - 优先按 command 判定（READ_RSP / WRITE_ACK / ERROR）
 * - 必要元数据：NODE_ID / INDEX / SUBINDEX / CAN_ID
 * - ERROR 的 abortCode：优先读 metadata[ABORT]；否则若 payload>=4，用小端解析 UInt32
 * - 无法判定时做宽容兜底（有 size+非空payload -> ReadData；空payload -> WriteAck；否则 -> Error）
 */
fun CommMessage.toSdoResponse(): SdoResponse {
    val meta = metadata
    val cmd  = command.orEmpty().uppercase(Locale.ROOT)

    val nodeId  = meta.intOrThrow(MetaKeys.NODE_ID, "nodeId missing in metadata")
    val index   = meta.intOrThrow(MetaKeys.INDEX,   "index missing in metadata")
    val subIdx  = meta.intOrDefault(MetaKeys.SUBINDEX, 0)
    val canId   = meta.intOrThrow(MetaKeys.CAN_ID,  "canId missing in metadata")

    return when {
        READ_RSP_ALIASES.contains(cmd) -> {
            SdoResponse.ReadData(
                nodeId   = nodeId,
                index    = index,
                subIndex = subIdx,
                canId    = canId,
                payload  = payload
            )
        }
        WRITE_ACK_ALIASES.contains(cmd) -> {
            SdoResponse.WriteAck(
                nodeId   = nodeId,
                index    = index,
                subIndex = subIdx,
                canId    = canId
            )
        }
        ERROR_ALIASES.contains(cmd) -> {
            val abort = meta.longOrNull(MetaKeys.ABORT)
                ?: if (payload.size >= 4) payload.readUInt32LE(0) else 0L
            SdoResponse.Error(
                nodeId    = nodeId,
                index     = index,
                subIndex  = subIdx,
                canId     = canId,
                abortCode = abort,
                raw       = payload
            )
        }
        else -> { // 宽容兜底
            val sizeMeta = meta.intOrNull(MetaKeys.SIZE)
            when {
                sizeMeta != null && payload.isNotEmpty() ->
                    SdoResponse.ReadData(nodeId, index, subIdx, canId, payload)
                payload.isEmpty() ->
                    SdoResponse.WriteAck(nodeId, index, subIdx, canId)
                else -> {
                    val abort = meta.longOrNull(MetaKeys.ABORT) ?: if (payload.size >= 4) payload.readUInt32LE(0) else 0L
                    SdoResponse.Error(nodeId, index, subIdx, canId, abort, payload)
                }
            }
        }
    }
}

// ===== Map/ByteArray 小工具（仅本文件可见）=====

private fun Map<String, Any>.intOrNull(key: String): Int? =
    (this[key] as? Number)?.toInt()

private fun Map<String, Any>.longOrNull(key: String): Long? =
    (this[key] as? Number)?.toLong()

private fun Map<String, Any>.intOrDefault(key: String, def: Int): Int =
    intOrNull(key) ?: def

private fun Map<String, Any>.intOrThrow(key: String, msg: String): Int =
    intOrNull(key) ?: error(msg)

/** 小端解析无符号 32 位到 Long（保持正数语义） */
private fun ByteArray.readUInt32LE(offset: Int = 0): Long {
    require(size >= offset + 4) { "need >=4 bytes from offset=$offset, actual=$size" }
    return ((this[offset].toLong() and 0xFF)) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}
