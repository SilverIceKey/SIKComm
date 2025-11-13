package com.sik.comm

/**
 * 统一的通信通道接口。
 *
 * 无论是串口还是 SocketCAN，都通过该接口对外暴露。
 * 核心能力：
 * - 管理连接（open / close / isOpen）
 * - 串行发送数据（send）
 * - 多次回调接收数据（setReceiver）
 */
interface CommChannel {

    /**
     * 通道 ID，对应配置里的 id。
     */
    val id: String

    /**
     * 打开底层连接（幂等）。
     *
     * - 调用多次，只有第一次真正打开，后续直接返回。
     * - 内部会启动读循环（loop），循环里通过 JNI 调用阻塞式 poll + read。
     */
    fun open()

    /**
     * 关闭底层连接。
     *
     * - 停止读循环
     * - 释放 JNI 层的句柄（例如 fd）
     */
    fun close()

    /**
     * 当前通道是否已经打开。
     */
    fun isOpen(): Boolean

    /**
     * 发送一段原始字节（串行化）。
     *
     * 对串口：
     * - 内部会保证“要么发，要么收”，在执行 send 期间不会同时读。
     *
     * 对 CAN：
     * - 物理上是全双工，但实现上仍然通过一个 IO 线程顺序执行写操作，
     *   避免读写竞态，保证逻辑简单。
     *
     * @param bytes     需要发送的字节数组
     * @param timeoutMs 写操作超时时间（毫秒），如果为 null 则使用配置中的 writeTimeoutMs
     * @return          实际写入的字节数（>= 0）
     */
    suspend fun send(bytes: ByteArray, timeoutMs: Int? = null): Int

    /**
     * 设置或替换接收回调。
     *
     * - 若设置为 null，则读循环仍然存在，但会丢弃收到的数据。
     * - 推荐在调用 open() 之前就设置好 receiver，方便启动后立即处理数据。
     */
    fun setReceiver(receiver: CommReceiver?)
}
