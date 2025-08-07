package com.sik.comm.core.model

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.protocol.ProtocolType

/**
 * 通用通信配置的抽象基类。
 * 所有协议的配置类都应继承此类，并添加自身特有参数。
 *
 * ⚠️ 该类用于提供统一的设备描述与运行控制能力，
 * 不应与业务模型耦合。
 */
abstract class ProtocolConfig(

    /** 设备唯一标识（如 MAC、IP、串口号等） */
    val deviceId: String,

    /** 当前配置所属协议类型（由子类指定） */
    val protocolType: ProtocolType,

    /** 是否启用 Mock 拦截 */
    val enableMock: Boolean = false
) {

    /**
     * 默认插件（子类可重写）
     */
    protected open val plugins: List<CommPlugin> = emptyList()

    /**
     * 默认拦截器（子类可重写）
     */
    protected open val interceptors: List<CommInterceptor> = emptyList()

    /**
     * 附加插件：用于框架或业务动态注入
     */
    open val additionalPlugins: List<CommPlugin> = emptyList()

    /**
     * 附加拦截器：用于框架或业务动态注入
     */
    open val additionalInterceptors: List<CommInterceptor> = emptyList()

    /**
     * 合并后的插件（供框架访问）
     */
    internal fun getAllPlugins(): List<CommPlugin> =
        plugins + additionalPlugins

    /**
     * 合并后的拦截器（供框架访问）
     */
    internal fun getAllInterceptors(): List<CommInterceptor> =
        interceptors + additionalInterceptors
}

