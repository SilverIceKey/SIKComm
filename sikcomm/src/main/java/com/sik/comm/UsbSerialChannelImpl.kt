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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal class UsbSerialChannelImpl(
    private val config: UsbSerialConfig
) : CommChannel {

    companion object {
        private const val TAG = "SIKComm-USB"
    }

    override val id: String get() = config.id

    private val appContext = config.context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var handle: Long = 0L
    @Volatile
    private var receiver: CommReceiver? = null

    /** 业务侧传进来的 receiver（可能被内部 wrapper 包一层） */
    @Volatile
    private var userReceiver: CommReceiver? = null

    /** demo 行为对齐：400ms 拼包 + 3s 超时清空 + 可选剔除 \r\n */
    private val qrAssembler: QrAssembler? = config.qrAssemblePolicy?.let { QrAssembler(it) }

    private var ioJob: Job? = null
    private val openRequested = AtomicBoolean(false)

    private data class WriteJob(
        val data: ByteArray,
        val timeoutMs: Int,
        val result: CompletableDeferred<Int> = CompletableDeferred()
    )

    private val writeQueue: Channel<WriteJob> = Channel(Channel.UNLIMITED)

    private val permissionAction = "${appContext.packageName}.SIKCOMM_USB_PERMISSION.$id"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.i(TAG, "permission result granted=$granted device=${device?.deviceName} (id=$id)")
            if (!granted) return
            // 真正 open
            openInternal()
        }
    }

    override fun setReceiver(receiver: CommReceiver?) {
        this.userReceiver = receiver
        // 若开启了扫码拼包策略，则内部包装，确保对外调用方式不变、但行为与 demo 一致
        this.receiver = if (receiver == null) {
            null
        } else {
            val asm = qrAssembler
            if (asm == null) receiver else asm.wrap(receiver)
        }
    }

    override fun open() {
        if (isOpen()) return
        if (!openRequested.compareAndSet(false, true)) return

        // 注册权限回调（幂等保护：如果重复注册会崩，所以只在首次 open 尝试时注册）
        try {
            appContext.registerReceiver(permissionReceiver, IntentFilter(permissionAction))
        } catch (_: Throwable) {
        }

        val device = NativeUsbSerial.findDevice(appContext, config.deviceMatcher)
        if (device == null) {
            Log.e(TAG, "open: no matched device (id=$id)")
            return
        }

        dumpDevice(device)

        if (NativeUsbSerial.hasPermission(appContext, device)) {
            Log.i(TAG, "open: already has permission (id=$id)")
            openInternal()
        } else {
            Log.i(TAG, "open: request permission (id=$id)")
            requestPermission(device)
        }
    }

    private fun openInternal() {
        if (isOpen()) return

        val h = NativeUsbSerial.open(appContext, config)
        if (h <= 0L) {
            Log.e(TAG, "openInternal: NativeUsbSerial.open failed (id=$id)")
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
            NativeUsbSerial.close(h)
            handle = 0L
        }

        try {
            appContext.unregisterReceiver(permissionReceiver)
        } catch (_: Throwable) {
        }

        scope.cancel()
    }

    override fun isOpen(): Boolean = handle != 0L

    override suspend fun send(bytes: ByteArray, timeoutMs: Int?): Int {
        check(isOpen()) { "UsbSerialChannelImpl#send called when not open (id=$id)" }
        val t = timeoutMs ?: config.writeTimeoutMs

        val job = WriteJob(
            data = bytes.copyOf(),
            timeoutMs = t
        )
        writeQueue.send(job)
        return job.result.await()
    }

    /**
     * IO 循环：读优先；读不到才写（对标 SerialChannelImpl）:contentReference[oaicite:6]{index=6}
     */
    private fun startIoLoop() {
        ioJob = scope.launch {
            val buffer = ByteArray(4096)

            Log.i(TAG, "ioLoop start (id=$id)")

            while (isActive && isOpen()) {
                val hRead = handle
                if (hRead == 0L) break

                val n = NativeUsbSerial.read(hRead, buffer, config.readTimeoutMs)
                when {
                    // n>0: got bytes
                    n > 0 -> {
                        receiver?.onBytesReceived(buffer, 0, n)
                        continue
                    }

                    // 重点：按你的要求对齐设备行为
                    // 有些实现里 n=-1 只是“本轮没读到数据”，不是失败；等价于 timeout。
                    n == 0 || n == -1 -> {
                        // no data, fall through to write
                    }

                    // 其他负数：当作真正错误
                    else -> {
                        Log.e(TAG, "read error n=$n (id=$id)")
                        break
                    }
                }

                val w = writeQueue.tryReceive().getOrNull()
                if (w != null) {
                    val hWrite = handle
                    if (hWrite == 0L) {
                        w.result.completeExceptionally(IllegalStateException("USB handle closed during write (id=$id)"))
                        continue
                    }
                    val written = NativeUsbSerial.write(hWrite, w.data, 0, w.data.size, w.timeoutMs)
                    if (written >= 0) w.result.complete(written)
                    else w.result.completeExceptionally(IllegalStateException("USB write error=$written (id=$id)"))
                }
            }

            Log.i(TAG, "ioLoop end (id=$id)")
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(appContext, 0, Intent(permissionAction), flags)
        usbManager.requestPermission(device, pi)
    }

    private fun dumpDevice(device: UsbDevice) {
        try {
            Log.i(
                TAG,
                "device: name=${device.deviceName} vid=${hex(device.vendorId)} pid=${hex(device.productId)} ifCount=${device.interfaceCount} (id=$id)"
            )
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                Log.i(
                    TAG,
                    "  if[$i] cls=${intf.interfaceClass} sub=${intf.interfaceSubclass} proto=${intf.interfaceProtocol} epCount=${intf.endpointCount} (id=$id)"
                )
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * 扫码枪拼包器：严格对齐 demo UsbService.mCallback.onReceivedData
     * - DeleteElement：剔除 13/10
     * - 3 秒超时清空旧数据
     * - 400ms 窗口内的分段数据拼接一次回调
     *
     * 注意：这里的输出是“UTF-8 文本对应的 bytes”。
     */
    private inner class QrAssembler(
        private val policy: QrAssemblePolicy
    ) {
        private val lock = Any()
        private val parts = ArrayList<String>(8)

        @Volatile
        private var lastTimeMs: Long = 0L

        @Volatile
        private var emitJob: Job? = null

        fun wrap(downstream: CommReceiver): CommReceiver {
            return CommReceiver { data, offset, length ->
                onChunk(downstream, data, offset, length)
            }
        }

        private fun resetListData(now: Long) {
            if (lastTimeMs == 0L) {
                lastTimeMs = now
                return
            }
            val diffSeconds = ((now - lastTimeMs) / 1000L)
            if (diffSeconds >= policy.resetTimeoutSeconds) {
                parts.clear()
            }
            lastTimeMs = now
        }

        private fun onChunk(downstream: CommReceiver, data: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return

            val now = System.currentTimeMillis()

            // 复制一份：底层 buffer 会复用
            val slice = data.copyOfRange(offset, offset + length)

            // 对标 demo DeleteElement：剔除 13/10
            val filtered: ByteArray = if (!policy.dropCrLf) {
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

            // 对标 demo new String(bytes, "UTF-8")
            val text = try {
                String(filtered, Charsets.UTF_8)
            } catch (_: Throwable) {
                return
            }
            if (text.isEmpty()) return

            synchronized(lock) {
                resetListData(now)
                parts.add(text)

                // 对标 demo: removeCallbacks + postDelayed(400)
                emitJob?.cancel()
                emitJob = scope.launch {
                    delay(policy.mergeWindowMs)
                    val merged = synchronized(lock) {
                        if (parts.isEmpty()) return@launch
                        buildString {
                            for (p in parts) append(p)
                        }.also { parts.clear() }
                    }
                    if (merged.isNotEmpty()) {
                        val out = merged.toByteArray(Charsets.UTF_8)
                        downstream.onBytesReceived(out, 0, out.size)
                    }
                }
            }
        }
    }

    private fun hex(x: Int): String = "0x" + x.toString(16).uppercase()
}
