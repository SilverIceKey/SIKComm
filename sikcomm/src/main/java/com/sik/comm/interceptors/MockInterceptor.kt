package com.sik.comm.interceptors

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.interceptor.InterceptorChain
import com.sik.comm.core.logger.ProtocolLogger
import com.sik.comm.core.logger.DefaultProtocolLogger
import com.sik.comm.core.model.CommMessage
import kotlinx.coroutines.delay

/**
 * MockInterceptor
 *
 * 功能：
 * - 根据一组 MockRule 条件地拦截链路，直接返回“模拟响应”
 * - 命中规则即终止链路（不再调用 chain.proceed）
 * - 未命中规则则放行到后续拦截器/真实发送
 *
 * 典型用法：
 * - 无设备联调：为关键命令配置固定响应
 * - 压力/容错测试：配置一定概率失败或超时
 * - A/B 仿真：不同 command 返回不同伪数据
 */
class MockInterceptor(
    private val rules: List<MockRule>,
    private val logger: ProtocolLogger = DefaultProtocolLogger
) : CommInterceptor {

    override suspend fun intercept(chain: InterceptorChain, original: CommMessage): CommMessage {
        // 尝试匹配规则
        for (rule in rules) {
            if (rule.predicate(original)) {
                // 可选延迟，模拟真实设备耗时
                if (rule.delayMillis > 0) delay(rule.delayMillis)

                val mock = rule.responder(original)
                // 记录一次“拦截返回”
                logger.onReceive(chain.scope().deviceId, mock)
                return mock // <-- 直接返回，终止链路
            }
        }
        // 未命中规则：继续链路
        return chain.proceed(original)
    }
}
