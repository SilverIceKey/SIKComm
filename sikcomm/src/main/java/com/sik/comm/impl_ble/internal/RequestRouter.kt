package com.sik.comm.impl_ble.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/** 通用请求-应答路由器（key 为字符串） */
class RequestRouter<V> {
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<V>>()

    fun register(key: String): CompletableDeferred<V> {
        val d = CompletableDeferred<V>()
        waiters[key] = d
        return d
    }

    fun resolve(key: String, value: V) {
        waiters.remove(key)?.complete(value)
    }

    fun rejectAll(cause: Throwable) {
        val all = waiters.values.toList()
        waiters.clear()
        all.forEach { it.completeExceptionally(cause) }
    }

    suspend fun await(key: String, timeoutMs: Long): V {
        val d = waiters[key] ?: throw IllegalStateException("No waiter for key=$key")
        return try { withTimeout(timeoutMs) { d.await() } } finally { waiters.remove(key, d) }
    }
}