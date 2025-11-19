package com.sik.comm_sample

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.SerialConfig
import com.sik.comm.SikComm
import com.sik.sikcore.extension.setDebouncedClickListener
import com.sik.sikcore.thread.ThreadUtils


class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.button).setDebouncedClickListener {
            ThreadUtils.runOnIO {
                SikComm.open(SerialConfig("ScanDevice", "/dev/ttyS3", 9600)).apply {
                    open()
                }
                    .setReceiver { data, offset, length ->
                        Log.i(
                            "SIKComm",
                            "data: ${data.toHexString()}, offset: $offset, length: $length"
                        )
                    }
            }
        }
    }
}