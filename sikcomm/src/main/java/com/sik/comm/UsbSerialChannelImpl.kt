package com.sik.comm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.sik.comm.internal.ioloop.HalfDuplexIoLooper
import com.sik.comm.internal.pipeline.QrAssembleStage
import com.sik.comm.internal.pipeline.ReceivePipeline
import com.sik.comm.internal.transport.FelUsbSerialTransport
import com.sik.comm.internal.transport.Transport
import com.sik.comm.internal.usb.UsbPermissionBroker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * USB-Serial 通道实现（felHR85/UsbSerial）。
 *
 * 特性：
 * - 半双工读优先 IO 调度
 * - 内置 USB 权限管理
 * - 可选扫码枪拼包策略（通过 [ReceivePipeline] 实现）
 */
internal class UsbSerialChannelImpl(
    private val config: UsbSerialConfig,
    transportOverride: Transport? = null
) : CommChannel {

    companion object {
        private const val TAG = "SIKComm-USB"
    }

    override val id: String get() = config.id

    private val appContext: Context = config.context.applicationContext

    private val transport: Transport = transportOverride ?: FelUsbSerialTransport()
    private val looper = HalfDuplexIoLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs,
        readErrorFilter = { it == -1 }  // -1 视为 timeout，继续循环
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val permissionBroker = UsbPermissionBroker(
        context = appContext,
        actionSuffix = "SIKCOMM_USB_PERMISSION.$id",
        onGranted = ::openInternal
    )

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var userReceiver: CommReceiver? = null

    private var ioJob: Job? = null

    private val pipeline: CommReceiver? by lazy {
        val policy = config.qrAssemblePolicy ?: return@lazy userReceiver
        ReceivePipeline(
            stages = listOf(QrAssembleStage(policy, scope)),
            finalReceiver = userReceiver
        )
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.userReceiver = receiver
    }

    override fun open() {
        if (isOpen()) return

        val device = com.sik.comm.NativeUsbSerial.findDevice(appContext, config.deviceMatcher)
        if (device == null) {
            Log.e(TAG, "open: no matched device (id=$id)")
            return
        }

        dumpDevice(device)
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
        ioJob = looper.start(scope, h) { pipeline ?: userReceiver }
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
        check(isOpen()) { "UsbSerialChannelImpl#send called when not open (id=$id)" }
        return looper.send(bytes, timeoutMs ?: config.writeTimeoutMs)
    }

    private fun dumpDevice(device: UsbDevice) {
        try {
            Log.i(TAG, "device: name=${device.deviceName} vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} ifCount=${device.interfaceCount} (id=$id)")
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                Log.i(TAG, "  if[$i] cls=${intf.interfaceClass} sub=${intf.interfaceSubclass} proto=${intf.interfaceProtocol} epCount=${intf.endpointCount} (id=$id)")
            }
        } catch (_: Throwable) {
        }
    }
}
