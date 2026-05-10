package com.sik.comm.internal.channel

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.sik.comm.CommReceiver
import com.sik.comm.UsbHidConfig
import com.sik.comm.internal.ioloop.HalfDuplexIoLooper
import com.sik.comm.internal.transport.AndroidUsbHidTransport
import com.sik.comm.internal.transport.Transport
import com.sik.comm.internal.usb.UsbPermissionBroker
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicInteger

/**
 * USB-HID 通道实现。
 */
internal class UsbHidChannelImpl(
    private val config: UsbHidConfig,
    transportOverride: Transport? = null
) : BaseCommChannel(config.id) {

    companion object {
        private const val TAG = "SIKComm-HID"
    }

    private val appContext: Context = config.context.applicationContext

    private val transport: Transport = transportOverride ?: AndroidUsbHidTransport()
    private val negCounter = AtomicInteger(0)
    private val looper = HalfDuplexIoLooper(
        transport = transport,
        readTimeoutMs = config.readTimeoutMs,
        readErrorFilter = { true },
        onReadSuccess = { negCounter.set(0) }
    )

    private var ioJob: Job? = null

    private val permissionBroker = UsbPermissionBroker(
        context = appContext,
        actionSuffix = "SIKCOMM_USB_PERMISSION_HID.$id",
        onGranted = ::openInternal,
        onDenied = { transitionState(com.sik.comm.internal.state.ChannelState.Closed) }
    )

    override fun doOpen(): Long = transport.open(config)

    override fun onOpened(handle: Long) {
        ioJob = looper.start(scope, handle) { currentReceiver }
    }

    override fun doClose(handle: Long) {
        ioJob?.cancel()
        ioJob = null
        looper.shutdown()
        transport.close(handle)
        permissionBroker.dispose()
    }

    override fun open() {
        if (isOpen()) return
        if (!tryTransition(com.sik.comm.internal.state.ChannelState.Closed, com.sik.comm.internal.state.ChannelState.Opening)) return

        val device = com.sik.comm.NativeUsbHid.findDevice(appContext, config.deviceMatcher)
        if (device == null) {
            Log.e(TAG, "open: no matched device (id=$id)")
            transitionState(com.sik.comm.internal.state.ChannelState.Closed)
            return
        }

        Log.i(TAG, "device: name=${device.deviceName} vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} ifCount=${device.interfaceCount} (id=$id)")
        val intf = device.getInterface(config.interfaceIndex)
        Log.i(TAG, "  if[${config.interfaceIndex}] cls=${intf.interfaceClass} sub=${intf.interfaceSubclass} proto=${intf.interfaceProtocol} epCount=${intf.endpointCount} (id=$id)")

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

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        val handle = requireHandle()
        return looper.send(bytes, timeoutMs ?: config.writeTimeoutMs)
    }
}
