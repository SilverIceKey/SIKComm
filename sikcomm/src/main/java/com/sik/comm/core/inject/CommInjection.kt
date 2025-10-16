/*
 * Copyright 2025 折千
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sik.comm.core.inject

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.interceptors.LoggingInterceptor

/**
 * 通信注入器（CommInjection）。
 *
 * 用于自动注册拦截器和插件，支持：
 * - 通过 config 声明注入内容
 * - 黑白名单过滤控制（按 class 或 tag）
 * - 后期支持自动发现（SPI / 注解等）
 */
object CommInjection {

    // 默认拦截器
    private val defaultInterceptors = mutableListOf<CommInterceptor>(LoggingInterceptor())

    // 默认插件
    private val defaultPlugins = mutableListOf<CommPlugin>()

    /**
     * 注册全局默认拦截器（所有 config 均默认包含）。
     */
    fun registerGlobalInterceptor(interceptor: CommInterceptor) {
        defaultInterceptors += interceptor
    }

    /**
     * 注册全局默认插件（所有 config 均默认包含）。
     */
    fun registerGlobalPlugin(plugin: CommPlugin) {
        defaultPlugins += plugin
    }

    /**
     * 初始化 config 时注入插件/拦截器。
     *
     * 会将全局默认 + config 自定义的内容混合后返回。
     */
    fun injectTo(config: ProtocolConfig): InjectedResult {
        val allInterceptors = defaultInterceptors + config.getAllInterceptors()
        val allPlugins = defaultPlugins + config.getAllPlugins()
        return InjectedResult(
            interceptors = allInterceptors.distinctBy { it::class },
            plugins = allPlugins.distinctBy { it::class }
        )
    }

    /**
     * 注入结果结构体。
     */
    data class InjectedResult(
        val interceptors: List<CommInterceptor>,
        val plugins: List<CommPlugin>
    )
}
