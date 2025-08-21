@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.sik.comm.impl_can

import android.content.Context
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.protocol.Protocol
import com.sik.comm.impl_can.link.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * 把 CAN 适配到你现成的 Protocol 接口
 */
class CanProtocol(
    private val context: Context,
    private val linkResolver: (deviceId: String) -> CanLinkConfig
) : Protocol {

    private data class Session(
        val link: CanLink,
        val job: Job,
        val rx: Channel<CANFrame>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, Session>()

    override suspend fun send(deviceId: String, msg: CommMessage): CommMessage {
        val frame = msg.toCANFrameOrThrow()   // ← 这里改成从 CommMessage 解析
        ensureConnected(deviceId)
        val s = sessions[deviceId] ?: error("Not connected")
        if (!s.link.send(frame)) error("send failed")
        return msg
    }

    override fun connect(deviceId: String) {
        scope.launch { ensureConnected(deviceId) }
    }

    override fun disconnect(deviceId: String) {
        sessions.remove(deviceId)?.let { s ->
            s.job.cancel()
            runCatching { s.link.close() }
        }
    }

    override fun isConnected(deviceId: String): Boolean =
        sessions[deviceId]?.link?.isOpen == true

    private suspend fun ensureConnected(deviceId: String) {
        if (sessions[deviceId]?.link?.isOpen == true) return
        val cfg = linkResolver(deviceId)
        val link: CanLink = when (cfg) {
            is CanLinkConfig.SlcanTcpConfig -> SlcanTcpLink(cfg)
            is CanLinkConfig.SlcanUsbConfig -> SlcanUsbLink(context.applicationContext, cfg)
            is CanLinkConfig.BleNusConfig -> BleNusLink(context.applicationContext, cfg)
            is CanLinkConfig.SocketCanConfig -> SocketCanLink(cfg)
            is CanLinkConfig.MockConfig -> MockCanLink(cfg)
        }
        link.open()
        val rxCh = Channel<CANFrame>(capacity = Channel.BUFFERED)
        val job = scope.launch {
            link.frames.collect { rxCh.trySend(it) }
        }
        sessions[deviceId] = Session(link, job, rxCh)
    }
}
