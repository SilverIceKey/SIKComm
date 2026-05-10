package com.sik.comm

/**
 * 通道打开结果回调。
 *
 * 适用于需要感知异步打开结果的场景（如 USB 权限请求）。
 * 对于同步 open 的通道（Serial / CAN），回调也会在 [CommChannel.open] 返回时立即触发。
 *
 * 使用方式：
 * ```kotlin
 * channel.setOpenCallback { success, error ->
 *     if (success) println("打开成功")
 *     else println("打开失败: ${error?.message}")
 * }
 * channel.open()
 * ```
 */
fun interface OpenCallback {

    /**
     * @param success true 表示通道已成功打开；false 表示失败或被拒绝
     * @param error   失败时的异常信息；success=true 时为 null
     */
    fun onResult(success: Boolean, error: Throwable?)
}
