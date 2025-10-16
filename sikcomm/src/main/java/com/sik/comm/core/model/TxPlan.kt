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
 * 一次链式事务的“发送计划”：按顺序下发这些帧。
 * 每帧的 payload 必须是“底层可直接发送”的原始字节（已按对应协议编好）。
 * metadata 可携带每步的 timeout/expected/gap 等提示。
 */
data class TxPlan(
    val frames: List<CommMessage>,
    val txnId: Int = 0
)