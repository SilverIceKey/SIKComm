package com.sik.comm

/**
 * 串口 JNI 封装。
 *
 * 注意：
 * - 所有方法都是阻塞式调用，对应 JNI 层内部使用 poll() 等待 fd 就绪。
 * - 这些方法应该只在 IO 线程（例如 Dispatchers.IO）中调用。
 */
internal object NativeSerial {

    init {
        // 加载对应的 so 库，名字按你实际编译出来的为准。
        System.loadLibrary("sikcomm")
    }

    /**
     * 打开串口并配置参数。
     *
     * @param path      串口设备路径，例如 "/dev/ttyS1"
     * @param baudRate  波特率
     * @param dataBits  数据位
     * @param stopBits  停止位
     * @param parity    校验位
     * @return          一个长整型句柄（例如 fd 或者指针），0 或负数代表失败（具体约定由你在 JNI 定）
     */
    @JvmStatic
    external fun open(
        path: String,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: Int
    ): Long

    /**
     * 使用 poll + write 写入数据。
     *
     * @param handle    open() 返回的句柄
     * @param data      要写入的字节数组
     * @param offset    起始下标
     * @param length    写入长度
     * @param timeoutMs poll 的超时时间（毫秒）
     * @return          >=0: 实际写入字节数；0: 超时；<0: 错误（具体由 JNI 约定）
     */
    @JvmStatic
    external fun write(
        handle: Long,
        data: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): Int

    /**
     * 使用 poll + read 读取数据。
     *
     * @param handle    open() 返回的句柄
     * @param buffer    读入数据的缓冲区
     * @param offset    写入缓冲区的起始下标
     * @param length    最大读取长度
     * @param timeoutMs poll 的超时时间（毫秒）
     * @return          >0: 实际读取字节数；0: 超时无数据；<0: 错误
     */
    @JvmStatic
    external fun read(
        handle: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): Int

    /**
     * 关闭串口。
     *
     * @param handle open() 返回的句柄
     */
    @JvmStatic
    external fun close(handle: Long)
}
