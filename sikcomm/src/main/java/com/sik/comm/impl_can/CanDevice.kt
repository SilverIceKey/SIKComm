package com.sik.comm.impl_can

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.state.DeviceStateCenter
import com.sik.comm.core.model.ProtocolState
import kotlinx.coroutines.*

/**
 * 单设备运行态。
 * - 管理底层 I/O
 * - 持有 Router
 * - 运行读循环
 */
internal class CanDevice(
    val config: CanConfig,
    val io: CanIo,
    val interceptors: List<CommInterceptor>,
    val plugins: List<CommPlugin>,
    private val externalScope: CoroutineScope
) {
    private val job = SupervisorJob()
    private val scope = externalScope + job + Dispatchers.IO

    val router = CanRequestRouter<SdoResponse>()

    @Volatile
    var running = false

    fun startReaderLoop(onFrame: (CanFrame) -> Unit) {
        running = true
        scope.launch {
            while (running && io.isOpen()) {
                try {
                    val f = io.read() ?: continue
                    onFrame(f)
                } catch (t: Throwable) {
                    running = false
                    router.rejectAll(t)
                    DeviceStateCenter.updateState(config.deviceId, ProtocolState.ERROR)
                    // 插件错误回调
                    plugins.forEach { p ->
                        runCatching { p.onError(CanPluginScopes.scope(config, ProtocolState.ERROR), t) }
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { io.close() }
        job.cancel()
    }
}
