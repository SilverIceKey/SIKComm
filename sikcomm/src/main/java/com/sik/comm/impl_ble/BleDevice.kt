package com.sik.comm.impl_ble

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.model.ProtocolState
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.state.DeviceStateCenter
import com.sik.comm.impl_ble.io.BleIo
import com.sik.comm.impl_ble.internal.RequestRouter

/** 单设备运行态：持有 IO、路由器、插件/拦截器 */
internal class BleDevice(
    val config: BleConfig,
    val io: BleIo,
    val interceptors: List<CommInterceptor>,
    val plugins: List<CommPlugin>
) {
    val router = RequestRouter<ByteArray>() // 值类型为通知原始帧（由上层解码）
    @Volatile var running = false

    fun onError(t: Throwable) {
        router.rejectAll(t)
        DeviceStateCenter.updateState(config.deviceId, ProtocolState.ERROR)
        plugins.forEach { p -> runCatching { p.onError(BlePluginScopes.scope(config, ProtocolState.ERROR), t) } }
    }
}