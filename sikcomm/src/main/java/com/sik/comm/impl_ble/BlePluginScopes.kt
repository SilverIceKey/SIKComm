package com.sik.comm.impl_ble

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.plugin.PluginScope

internal object BlePluginScopes {
    fun scope(cfg: BleConfig, state: ProtocolState, msg: CommMessage? = null): PluginScope =
        PluginScope(
            deviceId = cfg.deviceId,
            protocolType = cfg.protocolType,
            config = cfg,
            state = state,
            message = msg
        )
}