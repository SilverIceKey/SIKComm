package com.sik.comm_sample.can

import com.sik.comm.impl_can.SdoDialect
import com.sik.comm.impl_can.SdoRequest
import kotlin.math.max
import kotlin.math.min

/**
 * CAN 指令集（无类型探测版）
 *
 * - 不再依赖设备类型读取；直接按节点拿到一个“通用命令集”
 * - 同时提供 EKeyDock / FiveLock / KeyCabinet / MaterialCabinet 的方法
 * - 你按实际设备只调用相关的方法即可；调用不支持的寄存器会返回 Abort/超时，但不会阻塞分发
 */
object CanCommands {

    /** SDO 协议指令集（修正读响应常量） */
    val sdoDialect: SdoDialect = SdoDialect(
        READ = 0x40,
        READ_1B = 0x4F,  // ✅ 读1B响应
        READ_2B = 0x4B,  // ✅ 读2B响应
        READ_4B = 0x43,  // ✅ 读4B响应
        READ_ERROR = 0x80,
        WRITE_1B = 0x2F,
        WRITE_2B = 0x2B,
        WRITE_4B = 0x23,
        WRITE_ACK = 0x60,
        WRITE_ERROR = 0x80
    )

    // ========= 通用区：所有节点都能用 =========
    object Common {
        /** 版本 (R) → 0x6003/0x00, 4B: HW主,HW子,SW主,SW子 */
        fun getDeviceVersion(nodeId: Int): SdoRequest.Read =
            SdoRequest.Read(nodeId, 0x6003, 0x00)

        /** 大多数设备复用的状态寄存器 (R) → 0x6010/0x00, 2B */
        fun getStatus(nodeId: Int): SdoRequest.Read =
            SdoRequest.Read(nodeId, 0x6010, 0x00)
    }

    /**
     * 统一返回“通用命令集”（不做类型判断）
     */
    fun forDevice(nodeId: Int): GenericCommands = GenericCommands(nodeId)

    /** 通用命令集：把各家寄存器方法都放这（按需调用） */
    class GenericCommands(val nodeId: Int) {

        // ---- EKeyDock（左右位）/ 以及很多板子兼容的 0x6011 语义 ----

        /** 控制/状态 (R/W) 0x6011/0x00, 2B：写仅置相关位，其余写0；读回含工作位 */
        fun readControlReg(): SdoRequest.Read =
            SdoRequest.Read(nodeId, 0x6011, 0x00)

        /** 设置左右卡扣（bit0=左卡扣，bit4=右卡扣） */
        fun setLatch(left: Boolean? = null, right: Boolean? = null): SdoRequest.Write {
            var v = 0
            if (left != null) v = v or ((if (left) 1 else 0) shl 0)
            if (right != null) v = v or ((if (right) 1 else 0) shl 4)
            return SdoRequest.Write(nodeId, 0x6011, 0x00, shortLE(0b0001_0001, v), 2)
        }

        /** 设置左右充电（bit1=左充电，bit5=右充电） */
        fun setCharge(leftOn: Boolean? = null, rightOn: Boolean? = null): SdoRequest.Write {
            var v = 0
            if (leftOn != null) v = v or ((if (leftOn) 1 else 0) shl 1)
            if (rightOn != null) v = v or ((if (rightOn) 1 else 0) shl 5)
            return SdoRequest.Write(nodeId, 0x6011, 0x00, shortLE(0b0010_0010, v), 2)
        }

        /** 单侧卡扣语法糖：keySlotId: 0左/1右；status: 0解锁/1锁住 */
        fun controlLatch(keySlotId: Int, status: Int): SdoRequest.Write {
            require(keySlotId in 0..1) { "keySlotId must be 0(left)/1(right)" }
            require(status == 0 || status == 1) { "status must be 0/1" }
            val bit = if (keySlotId == 0) 0 else 4
            return SdoRequest.Write(
                nodeId,
                0x6011,
                0x00,
                shortLE(1 shl bit, (status and 1) shl bit),
                2
            )
        }

        /** 左/右 RFID (R) 4B 小端（常见地址） */
        fun getLeftRfid(): SdoRequest.Read = SdoRequest.Read(nodeId, 0x6020, 0x00)
        fun getRightRfid(): SdoRequest.Read = SdoRequest.Read(nodeId, 0x6024, 0x00)

        // ---- FiveLock / KeyCabinet（常见 1..5 位同构写法，寄存器通常与 0x6011 兼容） ----

        /** 一次写入 5 位控制（低5位有效），适配 5路/柜体同构 */
        fun setLatchBits_1to5(bits01to05: Int): SdoRequest.Write {
            val v = bits01to05 and 0b1_1111
            return SdoRequest.Write(nodeId, 0x6011, 0x00, shortLE(0b1_1111, v), 2)
        }

        /** 单位控制（1..5） */
        fun controlOne_1to5(slotIndex1to5: Int, locked: Boolean): SdoRequest.Write {
            require(slotIndex1to5 in 1..5) { "slotIndex must be 1..5" }
            val v = (if (locked) 1 else 0) shl (slotIndex1to5 - 1)
            return SdoRequest.Write(nodeId, 0x6011, 0x00, shortLE(1 shl (slotIndex1to5 - 1), v), 2)
        }

        /** 1..5 位 RFID 常见映射：0x6020..0x6024 */
        fun getSlotRfid_1to5(slotIndex1to5: Int): SdoRequest.Read {
            require(slotIndex1to5 in 1..5) { "slotIndex must be 1..5" }
            return SdoRequest.Read(nodeId, 0x6020 + (slotIndex1to5 - 1), 0x00)
        }

        // ---- MaterialCabinet（RGB/温湿度扩展） ----

        /** RGB 状态灯 (R/W) 0x6016/0x00, 4B: B[0..7],G[8..15],R[16..23], 模式[24..26], 时间[27..29], 单位[30], 锁定[31] */
        fun getRgb(): SdoRequest.Read = SdoRequest.Read(nodeId, 0x6016, 0x00)

        fun setRgb(
            r: Int, g: Int, b: Int,      // 0..255
            mode: Int,                    // 0关/1常亮/2闪烁/3呼吸/4流水
            timeStep: Int,                // 0..7（实际=+1）
            secondsUnit: Boolean,         // false=100ms, true=1000ms
            lockControl: Boolean          // 是否锁定主控权
        ): SdoRequest.Write {
            val rb = clamp8(b)
            val gg = clamp8(g)
            val rr = clamp8(r)
            val mm = clamp3(mode)
            val tt = clamp3(timeStep)
            var v = 0
            v = v or (rb and 0xFF)
            v = v or ((gg and 0xFF) shl 8)
            v = v or ((rr and 0xFF) shl 16)
            v = v or ((mm and 0x07) shl 24)
            v = v or ((tt and 0x07) shl 27)
            v = v or ((if (secondsUnit) 1 else 0) shl 30)
            v = v or ((if (lockControl) 1 else 0) shl 31)
            return SdoRequest.Write(nodeId, 0x6016, 0x00, intLE(v), 4)
        }

        /** 温湿度（常见扩展） */
        fun getTemperature(): SdoRequest.Read = SdoRequest.Read(nodeId, 0x6017, 0x00)
        fun getHumidity(): SdoRequest.Read = SdoRequest.Read(nodeId, 0x6018, 0x00)

        // ---- 通用状态 ----
        fun getStatus(): SdoRequest.Read = Common.getStatus(nodeId)
        fun getVersion(): SdoRequest.Read = Common.getDeviceVersion(nodeId)
    }

    // ========= Byte 打包工具（LE） =========
    private fun shortLE(control: Int, target: Int): ByteArray =
        byteArrayOf((target and 0xFF).toByte(), (control and 0xFF).toByte())

    private fun intLE(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte()
    )

    private fun clamp8(x: Int) = min(255, max(0, x))
    private fun clamp3(x: Int) = min(7, max(0, x))
}
