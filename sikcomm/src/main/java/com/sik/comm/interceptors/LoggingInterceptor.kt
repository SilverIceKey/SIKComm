package com.sik.comm.interceptors

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.interceptor.InterceptorChain
import com.sik.comm.core.logger.ProtocolLogger
import com.sik.comm.core.logger.DefaultProtocolLogger
import com.sik.comm.core.model.CommMessage

/**
 * LoggingInterceptor
 *
 * 功能：
 * - 在发送前/接收后打印或上报日志（通过 ProtocolLogger）
 * - 不修改消息、不终止链路（纯观测）
 *
 * 使用：
 * - 可作为全局默认拦截器注册到 CommInjection
 * - 或在某个 ProtocolConfig 的 interceptors 中单独启用
 *
 * 注意：
 * - 若 payload 很大，建议在 logger 实现中做截断处理
 * - 若涉及敏感数据，可在 logger 内做脱敏
 */
class LoggingInterceptor(
    private val logger: ProtocolLogger = DefaultProtocolLogger
) : CommInterceptor {

    override suspend fun intercept(chain: InterceptorChain, original: CommMessage): CommMessage {
        val scope = chain.scope()
        // 发送前日志
        logger.onSend(scope.deviceId, original)

        // 继续传递给后续拦截器 / 终端发送
        val response = chain.proceed(original)

        // 接收后日志
        logger.onReceive(scope.deviceId, response)
        return response
    }
}
