package com.sik.comm.internal.factory

import com.sik.comm.CommChannel
import com.sik.comm.CommConfig

/**
 * 通道工厂接口。
 */
internal fun interface ChannelFactory {
    fun create(config: CommConfig): CommChannel
}
