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

package com.sik.comm.core.extension

import com.sik.comm.core.model.CommMessage

inline fun <reified T : Number> CommMessage.metaNum(key: String): T? =
    (metadata[key] as? Number)?.let {
        @Suppress("UNCHECKED_CAST")
        when (T::class) {
            Int::class    -> it.toInt() as T
            Long::class   -> it.toLong() as T
            Short::class  -> it.toShort() as T
            Byte::class   -> it.toByte() as T
            else          -> null
        }
    }

fun CommMessage.metaBool(key: String): Boolean? =
    (metadata[key] as? Boolean)

fun CommMessage.withMeta(vararg pairs: Pair<String, Any>): CommMessage =
    copy(metadata = metadata + pairs)