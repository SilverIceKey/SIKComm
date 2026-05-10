package com.sik.comm.internal.state

/**
 * 通道内部状态机。
 */
internal sealed class ChannelState {
    data object Closed : ChannelState()
    data object Opening : ChannelState()
    data class Open(val handle: Long) : ChannelState()
    data class Failed(val cause: Throwable) : ChannelState()
}
