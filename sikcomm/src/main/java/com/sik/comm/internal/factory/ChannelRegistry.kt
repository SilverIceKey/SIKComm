package com.sik.comm.internal.factory

import com.sik.comm.CommConfig
import kotlin.reflect.KClass

/**
 * 配置类 → 通道工厂的注册表。
 */
internal object ChannelRegistry {

    private val factories = mutableMapOf<KClass<*>, ChannelFactory>()

    fun register(configClass: KClass<*>, factory: ChannelFactory) {
        factories[configClass] = factory
    }

    fun resolve(config: CommConfig): ChannelFactory? {
        return factories[config::class]
    }

    fun clear() {
        factories.clear()
    }
}
