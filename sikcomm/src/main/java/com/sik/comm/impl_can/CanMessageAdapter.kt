package com.sik.comm.impl_can

import com.sik.comm.core.model.CommMessage

/**
 * 约定的 metadata key（统一命名空间，避免撞车）
 */
object CanMeta {
    const val COB_ID    = "can.cob_id"     // Int (0..0x1FFFFFFF)
    const val EXTENDED  = "can.extended"   // Boolean
    const val REMOTE    = "can.remote"     // Boolean (RTR)
    const val DLC       = "can.dlc"        // Int 0..8（远程帧必须提供）
}

/** CommMessage -> CANFrame（缺字段就抛异常，便于尽早发现问题） */
fun CommMessage.toCANFrameOrThrow(): CANFrame {
    val cobId    = (metadata[CanMeta.COB_ID]   as? Int)
        ?: error("CommMessage.metadata missing ${CanMeta.COB_ID}")
    val extended = (metadata[CanMeta.EXTENDED] as? Boolean) ?: false
    val remote   = (metadata[CanMeta.REMOTE]   as? Boolean) ?: false
    val dlc      = if (remote) {
        (metadata[CanMeta.DLC] as? Int)
            ?: error("REMOTE frame requires ${CanMeta.DLC}")
    } else {
        payload.size
    }
    require(dlc in 0..8) { "Invalid DLC=$dlc" }
    if (!remote) require(payload.size == dlc) { "payload.size=${payload.size} != dlc=$dlc" }

    return CANFrame(
        id = cobId,
        dlc = dlc,
        data = if (remote) ByteArray(0) else payload,
        extended = extended,
        remote = remote
    )
}

/** CANFrame -> CommMessage（默认 command="CAN_FRAME"，可覆盖并追加元数据） */
fun CANFrame.toCommMessage(
    command: String = "CAN_FRAME",
    extraMeta: Map<String, Any> = emptyMap()
): CommMessage {
    val base = mapOf(
        CanMeta.COB_ID to id,
        CanMeta.EXTENDED to extended,
        CanMeta.REMOTE to remote,
        CanMeta.DLC to dlc
    )
    return CommMessage(
        command = command,
        payload = if (remote) ByteArray(0) else data,
        metadata = base + extraMeta
    )
}
