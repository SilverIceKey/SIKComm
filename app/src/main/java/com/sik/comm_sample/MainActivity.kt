package com.sik.comm_sample

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.*
import com.sik.sikcore.extension.setDebouncedClickListener
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.button).setDebouncedClickListener {
            uiLog("点击：打开 USB 扫码")

            // 先装 receiver，避免第一包丢
            UsbScanHelper.setReceiver { data, offset, length ->
                val scanContent =
                    data.sliceFast(offset, length).toString(Charset.defaultCharset())
                        .replace("\r", "")
                        .replace("\\r", "")
                        .replace("\n", "")
                        .replace("\\n", "")
                uiLog("RX: $scanContent")
                Log.i("SIKComm", "接收的数据:$scanContent")
            }

            // 主线程 init/open：让 USB 授权弹窗时机更稳
            UsbScanHelper.init(
                UsbSerialConfig(
                    id = "UsbScan",
                    context = this@MainActivity,
                    deviceMatcher = UsbDeviceMatcher.VidPidWhitelist(
                        setOf(0x1A86 to 0x7523)
                    ),
                    // 粒度1：你现在这个 1A86:7523 基本就是 CH34x
                    driverPolicy = UsbDriverPolicy.Prefer(UsbDriverFamily.CH34X),
                    baudRate = 9600,
                    dataBits = 8,
                    stopBits = 1,
                    parity = 0,
                    readTimeoutMs = 200,
                    writeTimeoutMs = 200
                )
            )

            uiLog("init 已调用：等待权限弹窗/设备数据…")
        }
    }

    private fun uiLog(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        runOnUiThread {
            tvLog.append("[$t] $msg\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        UsbScanHelper.release()
    }
}
