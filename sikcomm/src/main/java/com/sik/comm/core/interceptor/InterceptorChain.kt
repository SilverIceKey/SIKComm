package com.sik.comm.core.interceptor

import com.sik.comm.core.model.CommMessage

/**
 * 拦截器链（InterceptorChain）。
 *
 * 串联多个 CommInterceptor，用于构建链式拦截机制。
 * 每个拦截器决定是否继续链路，也可以直接返回响应。
 *
 * 使用方式：
 * ```kotlin
 * val chain = InterceptorChain(scope, interceptors) {
 *     actualSend(deviceId, it) // 最终执行的发送逻辑
 * }
 * val response = chain.proceed(originalMessage)
 * ```
 */
class InterceptorChain(
    private val scope: InterceptorScope,
    private val interceptors: List<CommInterceptor>,
    private val terminal: suspend (CommMessage) -> CommMessage
) {

    private var index = 0

    /**
     * 启动链式处理。
     *
     * @param message 当前处理的消息
     * @return 最终响应结果
     */
    suspend fun proceed(message: CommMessage): CommMessage {
        // 所有拦截器处理完，进入最终处理（发送逻辑）
        if (index >= interceptors.size) {
            return terminal.invoke(message)
        }

        val current = interceptors[index++]
        return current.intercept(this, message)
    }

    /**
     * 获取当前上下文环境信息（如设备 ID、时间戳等）。
     */
    fun scope(): InterceptorScope = scope
}
