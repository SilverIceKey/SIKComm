package com.sik.comm

/**
 * SocketCAN 通道配置。
 *
 * 本配置不强制要求 JNI 去 bringUp 接口，
 * bitrate / fdMode 仅作为“可选”参数传入 JNI 层使用。
 */
data class CanConfig(
    override val id: String,
    val ifName: String,              // 如: "can0" / "can1"
    val bitrate: Int? = null,        // 可选：如果 JNI 需要负责 `ip link set ... bitrate`
    val fdMode: Boolean = false,     // 是否 CAN FD 模式
    override val readTimeoutMs: Int = 500,
    override val writeTimeoutMs: Int = 500,
    val extra: Map<String, Any?> = emptyMap()
) : CommConfig
