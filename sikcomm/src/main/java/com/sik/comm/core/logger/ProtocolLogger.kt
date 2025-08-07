package com.sik.comm.core.logger

import com.sik.comm.core.model.CommMessage

/**
 * 协议日志记录器接口。
 *
 * 用于记录协议通信过程中的重要事件，例如：
 * - 设备连接 / 断开
 * - 消息发送 / 接收
 * - 异常 / 超时 / 重连
 *
 * 可实现上传到服务器、打印日志、存本地等策略。
 */
interface ProtocolLogger {

    /**
     * 连接事件。
     *
     * @param deviceId 设备标识（如 MAC 或 IP）
     */
    fun onConnect(deviceId: String)

    /**
     * 断开连接事件。
     *
     * @param deviceId 设备标识
     */
    fun onDisconnect(deviceId: String)

    /**
     * 发送前事件。
     *
     * @param deviceId 设备标识
     * @param message 待发送的消息
     */
    fun onSend(deviceId: String, message: CommMessage)

    /**
     * 接收成功事件。
     *
     * @param deviceId 设备标识
     * @param message 接收到的响应
     */
    fun onReceive(deviceId: String, message: CommMessage)

    /**
     * 异常事件。
     *
     * @param deviceId 设备标识
     * @param error 异常信息
     */
    fun onError(deviceId: String, error: Throwable)
}
