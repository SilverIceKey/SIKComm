package com.sik.comm

/**
 * SIKComm 对外唯一入口。
 *
 * 使用方式：
 * val channel = SikComm.open(config)
 * channel.setReceiver(...)
 * channel.open()
 * channel.send(...)
 */
object SikComm {

    /**
     * 根据不同类型的配置创建对应的通道实现。
     *
     * @param config 通道配置（串口 / CAN）
     * @return       对应的 CommChannel 实现
     */
    @JvmStatic
    fun open(config: CommConfig): CommChannel = when (config) {
        is SerialConfig -> SerialChannelImpl(config)
        is CanConfig    -> CanChannelImpl(config)
    }
}
