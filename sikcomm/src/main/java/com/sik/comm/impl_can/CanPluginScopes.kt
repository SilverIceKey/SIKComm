package com.sik.comm.impl_can

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.plugin.PluginScope

/**
 * 便捷构造插件 Scope。
 */
internal object CanPluginScopes {
    fun scope(config: CanConfig, state: ProtocolState, msg: CommMessage? = null): PluginScope {
        return PluginScope(
            deviceId = config.deviceId,
            protocolType = config.protocolType,
            config = config,
            state = state,
            message = msg
        )
    }
}
