package com.sik.comm.core.model

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.protocol.ProtocolType

/**
 * 通用通信配置的抽象基类。
 *
 * 所有协议的配置类都应继承此类（如 BleConfig、TcpConfig、Rs485Config 等），
 * 并添加自身特有的参数。
 *
 * ⚠️ 注意：
 * - 该类不应与业务模型耦合，仅用于描述设备和协议运行时所需的基本信息。
 * - 不应包含 UI 或数据库字段。
 */
abstract class ProtocolConfig(

    /** 设备唯一标识（如 MAC、IP、串口号、CAN 通道号等） */
    val deviceId: String,

    /** 当前配置所属协议类型（由子类指定） */
    val protocolType: ProtocolType,

    /** 是否启用 Mock 拦截（常用于调试或测试阶段） */
    val enableMock: Boolean = false
) {

    init {
        System.loadLibrary("sikcomm")
    }

    /** 默认插件（由子类定义，常用于心跳、状态上报） */
    protected open val plugins: List<CommPlugin> = emptyList()

    /** 默认拦截器（由子类定义，常用于 CRC、日志打印） */
    protected open val interceptors: List<CommInterceptor> = emptyList()

    /** 附加插件（可由业务或框架动态注入） */
    open val additionalPlugins: List<CommPlugin> = emptyList()

    /** 附加拦截器（可由业务或框架动态注入） */
    open val additionalInterceptors: List<CommInterceptor> = emptyList()

    /** 获取合并后的插件列表（框架内部使用） */
    internal fun getAllPlugins(): List<CommPlugin> = plugins + additionalPlugins

    /** 获取合并后的拦截器列表（框架内部使用） */
    internal fun getAllInterceptors(): List<CommInterceptor> = interceptors + additionalInterceptors
}
