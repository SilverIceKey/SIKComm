package com.sik.comm_sample

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.QrAssemblePolicy
import com.sik.comm.UsbDeviceMatcher
import com.sik.comm.UsbSerialConfig
import com.sik.comm.sliceFast
import com.sik.comm.toHex
import com.sik.sikcore.extension.setDebouncedClickListener
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
            uiLog("点击：打开 HID 扫码")

            // 先挂 receiver，避免第一包丢
            UsbScanHelper.setReceiver { data, offset, length ->
                val scanContent =
                    data.sliceFast(offset, length).apply {
                        Log.i("SIKCOMM-DATA", "${this.joinToString(",")}")
                    }.toHex()
                        .replace("\r", "")
                        .replace("\\r", "")
                        .replace("\n", "")
                        .replace("\\n", "")
                uiLog("RX: $scanContent")
                Log.i("SIKComm", "接收的数据:$scanContent")
            }

            UsbScanHelper.init(
                UsbSerialConfig(
                    id = "qr",
                    context = this,
                    deviceMatcher = UsbDeviceMatcher.QrScannerDemoWhitelist, // demo 同款白名单
                )
            )

            uiLog("init 已调用：等待权限/数据…")
        }
    }

    private fun uiLog(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        runOnUiThread { tvLog.append("[$t] $msg\n") }
    }

    override fun onDestroy() {
        super.onDestroy()
        UsbScanHelper.release()
    }
}
