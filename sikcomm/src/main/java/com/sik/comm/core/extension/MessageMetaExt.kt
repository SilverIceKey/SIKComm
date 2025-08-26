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