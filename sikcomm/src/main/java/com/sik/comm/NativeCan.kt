package com.sik.comm

/**
 * SocketCAN JNI 封装。
 *
 * 依然是阻塞式函数，内部通过 poll() 等待 CAN socket 就绪。
 */
internal object NativeCan {

    init {
        System.loadLibrary("sikcomm")
    }

    /**
     * 可选：启动 CAN 接口。
     *
     * JNI 内部可以用 `ip link set IF up type can bitrate ...` 或 netlink 实现。
     * 也可以什么都不干，直接返回 0，完全由系统/上层负责。
     *
     * @return 0 表示成功，非 0 表示失败（具体值自己定义）
     */
    @JvmStatic
    external fun bringUp(
        ifName: String,
        bitrate: Int,
        fdMode: Boolean
    ): Int

    /**
     * 可选：关闭 CAN 接口。
     */
    @JvmStatic
    external fun bringDown(ifName: String): Int

    /**
     * 打开 CAN socket 并绑定到指定接口。
     *
     * @return >0: 句柄（fd）；<=0: 错误
     */
    @JvmStatic
    external fun open(ifName: String): Long

    /**
     * 写 CAN 帧。
     *
     * @param handle    打开的 CAN socket 句柄
     * @param frameId   CAN ID
     * @param flags     标志位: 如 扩展帧/RTR/FD 等（具体 bit 定义由你约定）
     * @param data      数据内容
     * @param offset    起始位置
     * @param length    数据长度
     * @param timeoutMs poll 超时（毫秒）
     * @return          >=0: 已写入的字节数；0: 超时；<0: 错误
     */
    @JvmStatic
    external fun write(
        handle: Long,
        frameId: Int,
        flags: Int,
        data: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): Int

    /**
     * 读 CAN 帧。
     *
     * @param handle      打开的 CAN socket 句柄
     * @param outFrameId  输出 CAN ID 的数组，长度至少为 1
     * @param outFlags    输出 flags 的数组，长度至少为 1
     * @param data        存放 payload 的缓冲区
     * @param offset      起始下标
     * @param maxLen      最大读取长度
     * @param timeoutMs   poll 超时（毫秒）
     * @return            >0: 实际数据长度；0: 超时；<0: 错误
     */
    @JvmStatic
    external fun read(
        handle: Long,
        outFrameId: IntArray,
        outFlags: IntArray,
        data: ByteArray,
        offset: Int,
        maxLen: Int,
        timeoutMs: Int
    ): Int

    /**
     * 关闭 CAN socket。
     */
    @JvmStatic
    external fun close(handle: Long)
}
