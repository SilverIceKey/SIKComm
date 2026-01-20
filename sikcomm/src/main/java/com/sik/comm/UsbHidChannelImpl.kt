package com.sik.comm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal class UsbHidChannelImpl(
    private val config: UsbHidConfig
) : CommChannel {

    companion object {
        private const val TAG = "SIKComm-HID"
    }

    override val id: String get() = config.id

    private val appContext = config.context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var handle: Long = 0L
    @Volatile private var receiver: CommReceiver? = null

    private var ioJob: Job? = null
    private val openRequested = AtomicBoolean(false)

    private data class WriteJob(
        val data: ByteArray,
        val timeoutMs: Int,
        val result: CompletableDeferred<Int> = CompletableDeferred()
    )

    private val writeQueue: Channel<WriteJob> = Channel(Channel.UNLIMITED)

    private val permissionAction = "${appContext.packageName}.SIKCOMM_USB_PERMISSION_HID.$id"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.i(TAG, "permission result granted=$granted device=${device?.deviceName} (id=$id)")
            if (granted) openInternal()
        }
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.receiver = receiver
    }

    override fun open() {
        if (isOpen()) return
        if (!openRequested.compareAndSet(false, true)) return

        try {
            appContext.registerReceiver(permissionReceiver, IntentFilter(permissionAction))
        } catch (_: Throwable) {}

        val device = NativeUsbHid.findDevice(appContext, config.deviceMatcher)
        if (device == null) {
            Log.e(TAG, "open: no matched device (id=$id)")
            return
        }

        Log.i(
            TAG,
            "device: name=${device.deviceName} vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} ifCount=${device.interfaceCount} (id=$id)"
        )
        val intf = device.getInterface(config.interfaceIndex)
        Log.i(
            TAG,
            "  if[${config.interfaceIndex}] cls=${intf.interfaceClass} sub=${intf.interfaceSubclass} proto=${intf.interfaceProtocol} epCount=${intf.endpointCount} (id=$id)"
        )

        if (NativeUsbHid.hasPermission(appContext, device)) {
            Log.i(TAG, "open: already has permission (id=$id)")
            openInternal()
        } else {
            Log.i(TAG, "open: request permission (id=$id)")
            requestPermission(device)
        }
    }

    private fun openInternal() {
        if (isOpen()) return

        val h = NativeUsbHid.open(appContext, config)
        if (h <= 0L) {
            Log.e(TAG, "openInternal: NativeUsbHid.open failed (id=$id)")
            return
        }
        handle = h
        startIoLoop()
    }

    override fun close() {
        ioJob?.cancel()
        ioJob = null

        val h = handle
        if (h != 0L) {
            NativeUsbHid.close(h)
            handle = 0L
        }

        try { appContext.unregisterReceiver(permissionReceiver) } catch (_: Throwable) {}

        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) { "UsbHidChannelImpl#send called when not open (id=$id)" }
        val t = timeoutMs ?: config.writeTimeoutMs
        val job = WriteJob(bytes.copyOf(), t)
        writeQueue.send(job)
        return job.result.await()
    }

    private fun startIoLoop() {
        ioJob = scope.launch {
            // buffer 可以大，但 NativeUsbHid.read() 已经强制按 maxPacketSize 读取
            val buffer = ByteArray(4096)
            Log.i(TAG, "ioLoop start (id=$id)")

            var negCount = 0

            while (isActive && isOpen()) {
                val hRead = handle
                if (hRead == 0L) break

                val n = NativeUsbHid.read(hRead, buffer, config.readTimeoutMs)
                when {
                    n > 0 -> {
                        negCount = 0

                        // ✅ 打原始 hex（只打前 64 字节，避免刷屏）
                        val show = minOf(n, 64)
                        val hex = buildString(show * 3) {
                            for (i in 0 until show) append(String.format("%02X ", buffer[i]))
                        }
                        Log.i(TAG, "RX n=$n hex($show)=$hex (id=$id)")

                        receiver?.onBytesReceived(buffer, 0, n)
                        continue
                    }

                    n == 0 -> {
                        // timeout no data
                        continue
                    }

                    else -> { // n < 0
                        // HID 上 -1 很常见：当成 timeout 继续读
                        negCount++
                        if (negCount % 50 == 0) {
                            Log.w(TAG, "read returned -1 for $negCount times (id=$id)")
                        }
                        continue
                    }
                }

                // 读不到数据时才处理写
                val w = writeQueue.tryReceive().getOrNull()
                if (w != null) {
                    val hWrite = handle
                    if (hWrite == 0L) {
                        w.result.completeExceptionally(IllegalStateException("HID handle closed during write (id=$id)"))
                        continue
                    }
                    val written = NativeUsbHid.write(hWrite, w.data, 0, w.data.size, w.timeoutMs)
                    if (written >= 0) w.result.complete(written)
                    else w.result.completeExceptionally(IllegalStateException("HID write error=$written (id=$id)"))
                }
            }

            Log.i(TAG, "ioLoop end (id=$id)")
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pi = PendingIntent.getBroadcast(appContext, 0, Intent(permissionAction), flags)
        usbManager.requestPermission(device, pi)
    }
}
