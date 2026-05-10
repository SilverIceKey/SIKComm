package com.sik.comm

/**
 * SIKComm 统一异常基类。
 *
 * 所有通道操作（open / send / close）抛出的异常都继承此类，
 * 方便业务层做精细化错误处理。
 *
 * @since 2.1.0
 */
sealed class CommException(message: String) : RuntimeException(message) {

    /**
     * 通道未打开时调用操作。
     */
    class NotOpen(id: String) : CommException("Channel $id is not open")

    /**
     * 打开通道失败。
     */
    class OpenFailed(id: String, val code: Long) :
        CommException("Failed to open channel $id, code=$code")

    /**
     * 写入超时。
     */
    class WriteTimeout(id: String) : CommException("Write timeout on channel $id")

    /**
     * 底层传输错误。
     */
    class TransportError(id: String, val code: Int) :
        CommException("Transport error code=$code on channel $id")
}
