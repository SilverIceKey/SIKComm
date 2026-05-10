package com.sik.comm.internal.state

/**
 * 内部统一异常体系。
 *
 * 对外方法签名保持不变（send 仍返回 Int），但内部统一使用此异常，
 * 方便调用方在需要时 catch 做精细化处理。
 */
internal sealed class CommException(message: String) : RuntimeException(message) {

    class NotOpen(id: String) : CommException("Channel $id is not open")

    class WriteTimeout(id: String) : CommException("Write timeout on channel $id")

    class TransportError(id: String, val code: Int) :
        CommException("Transport error code=$code on channel $id")
}
