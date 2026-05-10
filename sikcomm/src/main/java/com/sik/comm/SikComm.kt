package com.sik.comm

import com.sik.comm.internal.factory.ChannelFactory
import com.sik.comm.internal.factory.ChannelRegistry

/**
 * SIKComm 对外唯一入口。
 *
 * 使用方式：
 * ```kotlin
 * val channel = SikComm.open(config)
 * channel.setReceiver(...)
 * channel.open()
 * channel.send(...)
 * ```
 */
object SikComm {

    init {
        // 注册内置通道工厂
        ChannelRegistry.register(SerialConfig::class, ChannelFactory { SerialChannelImpl(it as SerialConfig) })
        ChannelRegistry.register(CanConfig::class, ChannelFactory { CanChannelImpl(it as CanConfig) })
        ChannelRegistry.register(UsbSerialConfig::class, ChannelFactory { UsbSerialChannelImpl(it as UsbSerialConfig) })
        ChannelRegistry.register(UsbHidConfig::class, ChannelFactory { UsbHidChannelImpl(it as UsbHidConfig) })
    }

    /**
     * 根据配置创建对应通道。
     *
     * @param config 通道配置
     * @return       对应的 CommChannel 实现
     * @throws IllegalStateException 如果找不到对应配置的工厂
     */
    @JvmStatic
    fun open(config: CommConfig): CommChannel {
        val factory = ChannelRegistry.resolve(config)
            ?: error("No channel factory registered for ${config::class.simpleName}")
        return factory.create(config)
    }

    /**
     * 注册自定义通道工厂（支持运行时扩展）。
     *
     * @param configClass 配置类（如 SerialConfig::class）
     * @param factory     工厂函数：(CommConfig) -> CommChannel
     */
    @JvmStatic
    fun register(configClass: Class<out CommConfig>, factory: (CommConfig) -> CommChannel) {
        ChannelRegistry.register(configClass.kotlin, com.sik.comm.internal.factory.ChannelFactory { factory(it) })
    }
}
