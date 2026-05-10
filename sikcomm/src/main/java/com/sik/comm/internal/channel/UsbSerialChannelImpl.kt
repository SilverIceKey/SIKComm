package com.sik.comm.internal.channel

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.sik.comm.CommReceiver
import com.sik.comm.UsbSerialConfig
import com.sik.comm.internal.ioloop.HalfDuplexIoLooper
import com.sik.comm.internal.pipeline.QrAssembleStage
import com.sik.comm.internal.pipeline.ReceivePipeline
import com.sik.comm.internal.transport.FelUsbSerialTransport
import com.sik.comm.internal.transport.Transport
import com.sik.comm.internal.usb.UsbPermissionBroker
import kotlinx.coroutines.Job

/**
 * USB-Serial 通道实现（felHR85/UsbSerial）。
 */
internal class UsbSerialChannelImpl(
    private val config: UsbSerialConfig,
    transportOverride: Transport? = null
) : BaseCommChannel(config.id) {

    companion object {
        private const val TAG = "SIKComm-USB"
    }

    private val appContext: Context = config.context.applicationContext

    private val transport: Transport = transportOverride ?: FelUsbSerialTransport()
    private val looper = HalfDuplexIoLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs,
        readErrorFilter = { it == -1 }
    )

    private var ioJob: Job? = null

    private val permissionBroker = UsbPermissionBroker(
        context = appContext,
        actionSuffix = "SIKCOMM_USB_PERMISSION.$id",
        onGranted = ::openInternal,
        onDenied = { transitionState(com.sik.comm.internal.state.ChannelState.Closed) }
    )

    private val pipeline: CommReceiver? by lazy {
        val policy = config.qrAssemblePolicy ?: return@lazy currentReceiver
        ReceivePipeline(
            stages = listOf(QrAssembleStage(policy, scope)),
            finalReceiver = currentReceiver
        )
    }

    override fun setReceiver(receiver: CommReceiver?) {
        currentReceiver = receiver
    }

    override fun open() {
        if (isOpen()) return
        if (!tryTransition(com.sik.comm.internal.state.ChannelState.Closed, com.sik.comm.internal.state.ChannelState.Opening)) return

        val device = com.sik.comm.NativeUsbSerial.findDevice(appContext, config.deviceMatcher)
        if (device == null) {
            Log.e(TAG, "open: no matched device (id=$id)")
            transitionState(com.sik.comm.internal.state.ChannelState.Closed)
            return
        }

        dumpDevice(device)
        permissionBroker.request(device)
    }

    private fun openInternal() {
        if (isOpen()) return
        val h = doOpen()
        if (h <= 0L) {
            Log.e(TAG, "openInternal: doOpen failed (id=$id)")
            transitionState(com.sik.comm.internal.state.ChannelState.Closed)
            return
        }
        transitionState(com.sik.comm.internal.state.ChannelState.Open(h))
        onOpened(h)
    }

    override fun doOpen(): Long = transport.open(config)

    override fun onOpened(handle: Long) {
        ioJob = looper.start(scope, handle) { pipeline ?: currentReceiver }
    }

    override fun doClose(handle: Long) {
        ioJob?.cancel()
        ioJob = null
        looper.shutdown()
        transport.close(handle)
        permissionBroker.dispose()
    }

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        val handle = requireHandle()
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
