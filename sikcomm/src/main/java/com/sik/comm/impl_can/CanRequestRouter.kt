package com.sik.comm.impl_can

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * 请求-应答路由器（泛型版）。
 * 按 key 注册等待者，收到应答时 resolve 唤醒。
 */
class CanRequestRouter<T> {

    private val waiters = ConcurrentHashMap<CanRequestKey, CompletableDeferred<T>>()

    /** 注册一个等待者；如该 key 已存在会覆盖（按需也可改成报错） */
    fun register(key: CanRequestKey): CompletableDeferred<T> {
        val d = CompletableDeferred<T>()
        waiters[key] = d
        return d
    }

    /** 投递应答并唤醒等待者（一次性消费） */
    fun resolve(key: CanRequestKey, value: T) {
        waiters.remove(key)?.complete(value)
    }

    /** 全部失败收口（如断链/关闭） */
    fun rejectAll(cause: Throwable) {
        val all = waiters.values.toList()
        waiters.clear()
        all.forEach { it.completeExceptionally(cause) }
    }

    /** 等待应答（带超时）。无 waiter 时直接抛错。超时/异常都会清理该 waiter。 */
    suspend fun await(key: CanRequestKey, timeoutMs: Long): T {
        val d = waiters[key]
            ?: throw IllegalStateException("No waiter for key=$key")
        return try {
            withTimeout(timeoutMs) { d.await() }
        } finally {
            // 防止超时/异常后残留
            waiters.remove(key, d)
        }
    }
}