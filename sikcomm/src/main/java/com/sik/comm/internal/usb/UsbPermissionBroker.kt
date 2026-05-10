package com.sik.comm.internal.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB 权限统一代理。
 *
 * 封装了 USB 设备的权限请求流程：
 * 1. 注册 BroadcastReceiver 监听权限结果
 * 2. 检查当前是否已有权限
 * 3. 若无权限，弹系统对话框请求
 * 4. 权限 granted 后回调 [onGranted]
 */
internal class UsbPermissionBroker(
    context: Context,
    private val actionSuffix: String,
    private val onGranted: () -> Unit,
    private val onDenied: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "SIKComm-UsbPermission"
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val permissionAction = "${appContext.packageName}.$actionSuffix"
    private val disposed = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            Log.i(TAG, "permission result granted=$granted device=${device?.deviceName}")
            if (granted) onGranted() else onDenied?.invoke()
        }
    }

    /**
     * 尝试请求权限。
     *
     * @param device 目标 USB 设备
     * @return true 表示已有权限并立即触发 [onGranted]；false 表示已发起权限请求
     */
    fun request(device: UsbDevice): Boolean {
        if (disposed.get()) return false

        // 注册接收器（幂等保护：重复注册会崩）
        try {
            appContext.registerReceiver(receiver, IntentFilter(permissionAction))
        } catch (_: Throwable) {
            // 已注册，忽略
        }

        return if (usbManager.hasPermission(device)) {
            Log.i(TAG, "already has permission")
            onGranted()
            true
        } else {
            Log.i(TAG, "requesting permission...")
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(appContext, 0, Intent(permissionAction), flags)
            usbManager.requestPermission(device, pi)
            false
        }
    }

    /**
     * 释放资源（注销 BroadcastReceiver）。
     */
    fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Throwable) {
                // 可能未注册或已注销
            }
        }
    }
}
