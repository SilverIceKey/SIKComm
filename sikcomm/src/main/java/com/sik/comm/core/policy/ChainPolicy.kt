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

package com.sik.comm.core.policy

import com.sik.comm.core.model.ChainStepResult
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.protocol.LinkIO

/**
 * 针对不同协议（单包 / 多包）的链路策略。
 * afterSendStep 会在每次写出一帧之后被调用，用来【阻塞等待并消费】响应帧，
 * 决定是否继续后续步骤，以及两步之间的间隔。
 */
interface ChainPolicy {
    suspend fun afterSendStep(
        stepIndex: Int,
        sent: CommMessage,
        io: LinkIO
    ): ChainStepResult
}