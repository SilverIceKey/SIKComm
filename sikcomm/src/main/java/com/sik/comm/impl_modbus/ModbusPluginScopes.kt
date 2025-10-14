package com.sik.comm.impl_modbus

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.plugin.PluginScope

/**
 * 便捷构造 Modbus 插件使用的 Scope。
 */
internal object ModbusPluginScopes {
    fun scope(config: ModbusConfig, state: ProtocolState, message: CommMessage? = null): PluginScope {
        return PluginScope(
            deviceId = config.deviceId,
            protocolType = config.protocolType,
            config = config,
            state = state,
            message = message
        )
    }
}
