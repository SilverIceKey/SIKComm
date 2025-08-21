package com.sik.comm.impl_can

/**
 * SLCAN（Lawicel 风格）编解码：t/T/r/R + \r
 */
object SlcanCodec {

    fun encode(frame: CANFrame): ByteArray {
        val sb = StringBuilder(1 + 8 + 1 + 16 + 1)
        val type = when {
            frame.extended && frame.remote -> 'R'
            frame.extended && !frame.remote -> 'T'
            !frame.extended && frame.remote -> 'r'
            else -> 't'
        }
        sb.append(type)
        if (frame.extended) {
            sb.append(String.format("%08X", frame.id))
        } else {
            sb.append(String.format("%03X", frame.id))
        }
        sb.append(frame.dlc and 0xF)
        if (!frame.remote) {
            for (i in 0 until frame.dlc) sb.append(String.format("%02X", frame.data[i]))
        }
        sb.append('\r')
        return sb.toString().encodeToByteArray()
    }

    /** line: 去掉 \r 之后的一整行 */
    fun decode(line: String): CANFrame? {
        if (line.isEmpty()) return null
        val type = line[0]
        val extended = type == 'T' || type == 'R'
        val remote = type == 'r' || type == 'R'
        val i0 = 1
        return try {
            if (extended) {
                val id = line.substring(i0, i0 + 8).toInt(16)
                val dlc = line.substring(i0 + 8, i0 + 9).toInt(16)
                val data = if (!remote && dlc > 0) hexToBytes(line.substring(i0 + 9, i0 + 9 + dlc * 2)) else ByteArray(0)
                CANFrame(id, dlc, data, extended = true, remote = remote)
            } else {
                val id = line.substring(i0, i0 + 3).toInt(16)
                val dlc = line.substring(i0 + 3, i0 + 4).toInt(16)
                val data = if (!remote && dlc > 0) hexToBytes(line.substring(i0 + 4, i0 + 4 + dlc * 2)) else ByteArray(0)
                CANFrame(id, dlc, data, extended = false, remote = remote)
            }
        } catch (_: Throwable) { null }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            out[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    /** 常见命令（需按你设备固件表调整） */
    object Cmd {
        const val OPEN = "O\r"
        const val CLOSE = "C\r"
        fun setBitrateIndex(idx: Int) = "S${idx}\r" // 0..8 -> 10K..1M（示例）
        const val LISTEN_ONLY_ON = "L\r"
        const val LOOPBACK_ON = "l\r"
    }
}
