package com.sik.comm.impl_can.link

import android.content.Context
import android.hardware.usb.*
import com.sik.comm.impl_can.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SlcanUsbLink(
    private val appCtx: Context,
    override val config: CanLinkConfig.SlcanUsbConfig
) : CanLink {

    override var isOpen: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rx = MutableSharedFlow<CANFrame>(extraBufferCapacity = 256)
    override val frames = rx.asSharedFlow()

    private var usb: UsbManager? = null
    private var device: UsbDevice? = null
    private var conn: UsbDeviceConnection? = null
    private var intf: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var readerJob: Job? = null

    override suspend fun open() {
        if (isOpen) return
        usb = appCtx.getSystemService(Context.USB_SERVICE) as UsbManager

        val cand = usb!!.deviceList.values.firstOrNull { dev ->
            val vidOk = config.vendorId?.let { it == dev.vendorId } ?: true
            val pidOk = config.productId?.let { it == dev.productId } ?: true
            val nameOk = config.deviceNameContains?.let { dev.deviceName.contains(it, true) } ?: true
            vidOk && pidOk && nameOk
        } ?: error("No USB device matched config")

        device = cand
        require(usb!!.hasPermission(cand)) { "USB permission not granted" }

        conn = usb!!.openDevice(cand) ?: error("Open usb device failed")

        // 找 BULK IN/OUT
        loop@ for (i in 0 until cand.interfaceCount) {
            val itf = cand.getInterface(i)
            if (conn!!.claimInterface(itf, true)) {
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                    }
                }
                if (inEp != null && outEp != null) {
                    intf = itf; epIn = inEp; epOut = outEp
                    break@loop
                } else conn!!.releaseInterface(itf)
            }
        }
        require(epIn != null && epOut != null) { "No BULK endpoints" }

        // 可选：CDC/芯片初始化（根据你的适配器资料补齐）
        val idx = when (config.bitrate) {
            10_000 -> 0; 20_000 -> 1; 50_000 -> 2; 100_000 -> 3
            125_000 -> 4; 250_000 -> 5; 500_000 -> 6; 800_000 -> 7
            else -> 8
        }
        runCatching {
            writeAscii(SlcanCodec.Cmd.setBitrateIndex(idx))
            writeAscii(SlcanCodec.Cmd.OPEN)
        }

        readerJob = scope.launch {
            val buf = ByteArray(1024)
            val line = StringBuilder(128)
            while (isActive) {
                val n = conn!!.bulkTransfer(epIn, buf, buf.size, 100)
                if (n == null || n <= 0) continue
                for (i in 0 until n) {
                    val c = buf[i].toInt().toChar()
                    if (c == '\r') {
                        val str = line.toString()
                        line.setLength(0)
                        SlcanCodec.decode(str)?.let { rx.emit(it) }
                    } else if (c != '\n') line.append(c)
                }
            }
        }

        isOpen = true
    }

    override suspend fun send(frame: CANFrame): Boolean {
        if (!isOpen) return false
        val payload = SlcanCodec.encode(frame)
        val out = epOut ?: return false
        val c = conn ?: return false
        val n = c.bulkTransfer(out, payload, payload.size, 100)
        return n != null && n > 0
    }

    override fun close() {
        isOpen = false
        runCatching { writeAscii(SlcanCodec.Cmd.CLOSE) }
        readerJob?.cancel()
        runCatching { intf?.let { conn?.releaseInterface(it) } }
        runCatching { conn?.close() }
        epIn = null; epOut = null; intf = null; conn = null; device = null
    }

    private fun writeAscii(cmd: String) {
        val out = epOut ?: return
        val c = conn ?: return
        c.bulkTransfer(out, cmd.toByteArray(), cmd.length, 100)
    }
}
