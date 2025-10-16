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

package com.sik.comm.core.model

/**
 * 通信消息统一结构。
 * 作为所有协议层发送和接收的标准数据容器。
 *
 * 可以通过拦截器、编解码器等扩展 payload 的封装方式。
 */
data class CommMessage(

    /** 命令类型，通常为设备的功能指令（如 GET_TOKEN、SET_MODE） */
    val command: String,

    /** 原始数据内容，二进制格式（如 CRC 校验后数据） */
    val payload: ByteArray,

    /** 附加元数据（可选），例如超时时间、标识 ID、额外参数等 */
    val metadata: Map<String, Any> = emptyMap()
)
