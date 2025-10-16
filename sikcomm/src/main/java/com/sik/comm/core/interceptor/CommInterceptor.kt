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

package com.sik.comm.core.interceptor

import com.sik.comm.core.model.CommMessage

/**
 * 通信消息拦截器。
 *
 * 拦截器用于在消息发送与接收过程中插入自定义处理逻辑，例如：
 * - 日志打印
 * - 加解密
 * - CRC 校验
 * - 模拟响应（Mock）
 *
 * 拦截器通过 InterceptorChain 串联，可以控制是否继续执行后续流程，
 * 也可以直接返回响应结果实现 mock。
 */
interface CommInterceptor {

    /**
     * 拦截方法。
     *
     * 你可以：
     * 1. 调用 chain.proceed(message) 继续传递给下一个拦截器；
     * 2. 直接返回 CommMessage 作为响应，终止后续链（常用于 mock）；
     *
     * @param chain 当前拦截链
     * @param original 原始待发送的消息
     * @return 最终处理后的响应消息
     */
    suspend fun intercept(chain: InterceptorChain, original: CommMessage): CommMessage
}
