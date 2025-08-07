package com.sik.comm.core.interceptor

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolType

/**
 * 拦截器上下文作用域。
 *
 * 每次拦截器执行时，都会生成一个 InterceptorScope 实例，提供以下上下文信息：
 *
 * - 当前正在通信的设备 ID（deviceId）
 * - 所属协议类型（如 BLE、TCP、485 等）
 * - 该设备绑定的配置类（ProtocolConfig 子类实例）
 * - 当前要处理的通信消息（CommMessage）
 *
 * 拦截器可以根据 scope 中的信息进行：
 * - 差异化加密或校验
 * - 过滤某些设备
 * - Mock 针对不同设备返回不同模拟数据
 * - 日志打印时附带设备上下文
 *
 * 示例用法（在 CommInterceptor 中）：
 *
 * override suspend fun intercept(scope: InterceptorScope, chain: InterceptorChain): CommMessage {
 *     if (scope.deviceId.startsWith("BLE")) {
 *         log("BLE设备发送: ${scope.message.command}")
 *     }
 *     return chain.proceed(scope.message)
 * }
 */
data class InterceptorScope(

    /**
     * 当前通信设备的唯一标识。
     * - 对于 BLE：通常为 MAC 地址
     * - 对于 TCP：可为 IP:PORT
     * - 对于串口：可为串口路径或编号
     */
    val deviceId: String,

    /**
     * 当前设备所使用的协议类型（BLE / TCP / RS485 / MOCK ...）。
     */
    val protocolType: ProtocolType,

    /**
     * 当前设备的完整配置对象。
     * - 可用于读取 protocol-specific 配置（如 UUID、端口号）
     * - 类型为 ProtocolConfig 抽象类的子类（如 BleConfig / TcpConfig）
     */
    val config: ProtocolConfig,

    /**
     * 当前处理的通信消息。
     * 拦截器可以读取或修改 message，并传给下一个拦截器或终端处理器。
     */
    val message: CommMessage
)
