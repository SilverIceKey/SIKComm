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

package com.sik.comm.core.protocol

import com.sik.comm.core.model.CommMessage

/**
 * “裸”I/O，直接对接传输层：写/读原始帧。
 * - writeRaw: 直接把 sent.payload 写出去
 * - readRaw : 按策略需要读取一帧
 */
interface LinkIO {
    suspend fun writeRaw(msg: CommMessage)
    suspend fun readRaw(timeoutMs: Int, expectedSize: Int?, silenceGapMs: Int): CommMessage
}