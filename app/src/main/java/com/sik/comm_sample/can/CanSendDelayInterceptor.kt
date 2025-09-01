package com.sik.comm_sample.can

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.interceptor.InterceptorChain
import com.sik.comm.core.model.CommMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.SystemClock
import android.util.Log
import kotlin.math.max

/**
 * CAN 总线发送最小间隔拦截器（带协程锁）
 */
class CanSendDelayInterceptor(
    private val minIntervalMs: Long = 50L  // 可配置
) : CommInterceptor {

    companion object {
        // 用单一互斥量串行化“检查→等待→发送→更新时间”
        private val mutex = Mutex()
        // 用 elapsedRealtime 计时间隔，避免受系统时间调整影响
        @Volatile var lastSendTick: Long = 0L
    }

    override suspend fun intercept(chain: InterceptorChain, original: CommMessage): CommMessage {
        return mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastSendTick
            val waitMs = max(0L, minIntervalMs - elapsed)

            if (waitMs > 0) {
                Log.i("SIKComm","发送间隔 ${elapsed}ms < ${minIntervalMs}ms，延迟 ${waitMs}ms")
                delay(waitMs)
            } else {
                Log.i("SIKComm","发送间隔 ${elapsed}ms，>= ${minIntervalMs}ms，直接发送")
            }

            // 发送
            val rsp = chain.proceed(original)

            // 注意：以“发送完成时刻”作为下次基准；如果你更想“开始发送时刻”，把这行挪到 proceed() 之前即可
            lastSendTick = SystemClock.elapsedRealtime()
            rsp
        }
    }
}
