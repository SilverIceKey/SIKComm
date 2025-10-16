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

package com.sik.comm.core.codec

import com.sik.comm.core.model.CommMessage

/**
 * 通信消息编解码器。
 *
 * 每个协议配置需提供对应的 Codec，将 CommMessage 编码为 ByteArray 发送，
 * 或将接收到的 ByteArray 解码为 CommMessage。
 *
 * 示例用途：
 * - BLE：封包加头尾 + CRC
 * - TCP：以长度为前缀或分隔符为尾
 * - RS485：Modbus 封包 + 校验
 */
interface MessageCodec {

    /**
     * 编码：将消息转换为可发送的 ByteArray。
     *
     * @param msg 结构化通信消息
     * @return 二进制字节数组
     */
    fun encode(msg: CommMessage): ByteArray

    /**
     * 解码：将收到的字节数组转换为 CommMessage。
     *
     * @param data 接收到的原始字节流
     * @return 结构化消息对象
     */
    fun decode(data: ByteArray): CommMessage
}
