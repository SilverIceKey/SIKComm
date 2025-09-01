package com.sik.comm_sample.can

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.impl_can.CanConfig

/**
 * 自定义can配置
 */
class CustomCanConfig : CanConfig(
    deviceId = "can0@1",
    interfaceName = "can0",
    defaultNodeId = 1,
    sdo = CanCommands.sdoDialect
) {
    companion object {
        val instance by lazy { CustomCanConfig() }
    }

    private val canSendDelayInterceptor = CanSendDelayInterceptor()
    override val additionalInterceptors: List<CommInterceptor>
        get() = super.additionalInterceptors + canSendDelayInterceptor
}