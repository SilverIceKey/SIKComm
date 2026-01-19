package com.sik.comm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object NativeUsbSerial {
    private const val TAG = "SIKComm-USB"

    private val idGen = AtomicLong(1)
    private val ports = ConcurrentHashMap<Long, Port>()

    private data class Port(
        val device: UsbDevice,
        val connection: UsbDeviceConnection,
        val serial: UsbSerialDevice,
        val family: UsbDriverFamily
    )

    fun listDevices(context: Context): List<UsbDevice> {
        val um = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.deviceList?.values?.toList().orEmpty()
    }

    fun findDevice(context: Context, matcher: UsbDeviceMatcher): UsbDevice? {
        return listDevices(context).firstOrNull { matcher.match(it) }
    }

    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val um = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.hasPermission(device)
    }

    /**
     * 打开 USB 串口（需要上层保证权限已获取）
     * @return handle>0 成功；0 失败
     */
    fun open(context: Context, config: UsbSerialConfig): Long {
        val app = context.applicationContext
        val um = app.getSystemService(Context.USB_SERVICE) as UsbManager

        val device = findDevice(app, config.deviceMatcher) ?: run {
            Log.w(TAG, "open: no matched device (id=${config.id})")
            return 0L
        }

        if (!um.hasPermission(device)) {
            Log.w(TAG, "open: no permission yet (id=${config.id})")
            return 0L
        }

        val conn = um.openDevice(device) ?: run {
            Log.w(TAG, "open: openDevice returned null (id=${config.id})")
            return 0L
        }

        val serial = UsbSerialDevice.createUsbSerialDevice(device, conn) ?: run {
            Log.w(TAG, "open: no driver for device vid=${hex(device.vendorId)} pid=${hex(device.productId)} (id=${config.id})")
            try { conn.close() } catch (_: Throwable) {}
            return 0L
        }

        if (!serial.syncOpen()) {
            Log.w(TAG, "open: syncOpen failed (id=${config.id})")
            try { conn.close() } catch (_: Throwable) {}
            return 0L
        }

        // 配置串口参数
        serial.setBaudRate(config.baudRate)
        serial.setDataBits(config.dataBits)
        serial.setStopBits(config.stopBits)
        serial.setParity(config.parity)
        serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

        val family = detectFamily(serial)

        // 按“粒度1”分类策略校验
        when (val p = config.driverPolicy) {
            UsbDriverPolicy.Auto -> {
                Log.i(TAG, "open: driver=AUTO picked=$family (id=${config.id})")
            }
            is UsbDriverPolicy.Prefer -> {
                if (family != p.family) {
                    Log.e(TAG, "open: driver mismatch. prefer=${p.family} but picked=$family (id=${config.id})")
                    try { serial.syncClose() } catch (_: Throwable) {}
                    try { conn.close() } catch (_: Throwable) {}
                    return 0L
                }
                Log.i(TAG, "open: driver=Prefer(${p.family}) ok (id=${config.id})")
            }
        }

        val handle = idGen.getAndIncrement()
        ports[handle] = Port(device, conn, serial, family)

        Log.i(TAG, "open: success handle=$handle vid=${hex(device.vendorId)} pid=${hex(device.productId)} family=$family (id=${config.id})")
        return handle
    }

    fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int {
        val port = ports[handle] ?: return -1
        return port.serial.syncRead(buffer, timeoutMs)
    }

    fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
        val port = ports[handle] ?: return -1
        val slice = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        return port.serial.syncWrite(slice, timeoutMs)
    }

    fun close(handle: Long) {
        val port = ports.remove(handle) ?: return
        try { port.serial.syncClose() } catch (_: Throwable) {}
        try { port.connection.close() } catch (_: Throwable) {}
        Log.i(TAG, "close: handle=$handle family=${port.family}")
    }

    private fun detectFamily(serial: UsbSerialDevice): UsbDriverFamily {
        val n = serial::class.java.simpleName.lowercase()
        return when {
            "cdc" in n -> UsbDriverFamily.CDC
            "ch34" in n || "ch340" in n || "ch341" in n -> UsbDriverFamily.CH34X
            "cp210" in n -> UsbDriverFamily.CP210X
            "ftdi" in n || "ft" in n -> UsbDriverFamily.FTDI
            "pl2303" in n || "prolific" in n -> UsbDriverFamily.PL2303
            else -> {
                // 兜底：当作 CDC/未知都不对，这里给个最安全的“CDC”，仅用于日志分类
                UsbDriverFamily.CDC
            }
        }
    }

    private fun hex(x: Int): String = "0x" + x.toString(16).uppercase()
}
