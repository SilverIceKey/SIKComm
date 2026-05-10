package com.sik.comm.internal.pipeline

import com.sik.comm.CommReceiver
import com.sik.comm.QrAssemblePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 扫码枪拼包 Stage。
 *
 * 行为对齐原 demo：
 * - [mergeWindowMs] 窗口内的分段数据拼接成一次回调
 * - 超过 [resetTimeoutSeconds] 清空旧数据
 * - 可选剔除 \r(13) 与 \n(10)
 *
 * 注意：本 stage 需要协程支持（用于延迟拼接），因此依赖外部传入的 [scope]。
 */
internal class QrAssembleStage(
    private val policy: QrAssemblePolicy,
    private val scope: CoroutineScope
) : PipelineStage {

    private val lock = Any()
    private val parts = ArrayList<String>(8)

    @Volatile
    private var lastTimeMs: Long = 0L

    private var emitJob: Job? = null

    override fun process(data: ByteArray, offset: Int, length: Int, downstream: CommReceiver) {
        if (length <= 0) return

        val now = System.currentTimeMillis()
        val slice = data.copyOfRange(offset, offset + length)

        // 剔除 \r\n
        val filtered = if (!policy.dropCrLf) {
            slice
        } else {
            val tmp = ByteArray(slice.size)
            var k = 0
            for (b in slice) {
                val v = b.toInt() and 0xFF
                if (v != 13 && v != 10) {
                    tmp[k++] = b
                }
            }
            if (k == 0) return
            tmp.copyOfRange(0, k)
        }

        val text = try {
            String(filtered, Charsets.UTF_8)
        } catch (_: Throwable) {
            return
        }
        if (text.isEmpty()) return

        synchronized(lock) {
            resetIfExpired(now)
            parts.add(text)

            emitJob?.cancel()
            emitJob = scope.launch {
                delay(policy.mergeWindowMs)
                val merged = synchronized(lock) {
                    if (parts.isEmpty()) return@launch
                    buildString { for (p in parts) append(p) }.also { parts.clear() }
                }
                if (merged.isNotEmpty()) {
                    val out = merged.toByteArray(Charsets.UTF_8)
                    downstream.onBytesReceived(out, 0, out.size)
                }
            }
        }
    }

    private fun resetIfExpired(now: Long) {
        if (lastTimeMs == 0L) {
            lastTimeMs = now
            return
        }
        val diffSeconds = (now - lastTimeMs) / 1000L
        if (diffSeconds >= policy.resetTimeoutSeconds) {
            parts.clear()
        }
        lastTimeMs = now
    }
}
