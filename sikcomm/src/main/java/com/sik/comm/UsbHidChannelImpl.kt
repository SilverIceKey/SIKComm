package com.sik.comm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.sik.comm.internal.ioloop.HalfDuplexIoLooper
import com.sik.comm.internal.transport.AndroidUsbHidTransport
import com.sik.comm.internal.transport.Transport
import com.sik.comm.internal.usb.UsbPermissionBroker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicInteger

/**
 * USB-HID 通道实现。
 *
 * 特性：
 * - 半双工读优先 IO 调度
 * - 内置 USB 权限管理
 * - HID read 返回 -1 很常见，内部按 timeout 处理并抑制高频日志
 */
internal class UsbHidChannelImpl(
    private val config: UsbHidConfig,
    transportOverride: Transport? = null
) : CommChannel {

    companion object {
        private const val TAG = "SIKComm-HID"
    }

    override val id: String get() = config.id

    private val appContext: Context = config.context.applicationContext

    private val transport: Transport = transportOverride ?: AndroidUsbHidTransport()
    private val negCounter = AtomicInteger(0)
    private val looper = HalfDuplexIoLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs,
        readErrorFilter = { true },  // HID 读错误一律继续循环
        onReadSuccess = { negCounter.set(0) }  // 读成功时重置负值计数
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val permissionBroker = UsbPermissionBroker(
        context = appContext,
        actionSuffix = "SIKCOMM_USB_PERMISSION_HID.$id",
        onGranted = ::openInternal
    )

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var receiver: CommReceiver? = null

    private var ioJob: Job? = null

    override fun setReceiver(receiver: CommReceiver?) {
        this.receiver = receiver
    }

    override fun open() {
        if (isOpen()) return

        val device = com.sik.comm.NativeUsbHid.findDevice(appContext, config.deviceMatcher)
        if (device == null) {
            Log.e(TAG, "open: no matched device (id=$id)")
            return
        }

        Log.i(TAG, "device: name=${device.deviceName} vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} ifCount=${device.interfaceCount} (id=$id)")
        val intf = device.getInterface(config.interfaceIndex)
        Log.i(TAG, "  if[${config.interfaceIndex}] cls=${intf.interfaceClass} sub=${intf.interfaceSubclass} proto=${intf.interfaceProtocol} epCount=${intf.endpointCount} (id=$id)")

        permissionBroker.request(device)
    }

    private fun openInternal() {
        if (isOpen()) return
        val h = transport.open(config)
        if (h <= 0L) {
            Log.e(TAG, "openInternal: transport.open failed (id=$id)")
            return
        }
        handle = h
        ioJob = looper.start(scope, h) { receiver }
    }

    override fun close() {
        ioJob?.cancel()
        ioJob = null

        looper.shutdown()

        if (handle != 0L) {
            transport.close(handle)
            handle = 0L
        }

        permissionBroker.dispose()
        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) { "UsbHidChannelImpl#send called when not open (id=$id)" }
        return looper.send(bytes, timeoutMs ?: config.writeTimeoutMs)
    }
}
