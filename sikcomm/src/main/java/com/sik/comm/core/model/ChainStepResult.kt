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


/** 执行一步后的结果 */
data class ChainStepResult(
    val received: List<CommMessage> = emptyList(), // 本步收到的帧（0..N）
    val continueNext: Boolean = true,              // 是否继续下一步
    val interFrameDelayMs: Int = 0                 // 下一步发送前的间隔
)