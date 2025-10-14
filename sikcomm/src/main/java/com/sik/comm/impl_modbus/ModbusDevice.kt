package com.sik.comm.impl_modbus

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.plugin.CommPlugin

/**
 * Modbus 单设备运行态，持有传输层、拦截器与插件。
 */
internal class ModbusDevice(
    val config: ModbusConfig,
    val transport: ModbusTransport,
    val interceptors: List<CommInterceptor>,
    val plugins: List<CommPlugin>
) {
    fun close() {
        runCatching { transport.close() }
    }
}
