package com.sik.sikcomm

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.core.CommBridge
import com.sik.comm.bluetooth.BleManager
import com.sik.comm.task.SerialTask

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bleManager = BleManager(applicationContext)
        CommBridge.initBle(bleManager)

        // Demo call to library using the new task interface
        CommBridge.sendTask(
            SerialTask("demo", "/dev/ttyS1", byteArrayOf(0x00))
        )
    }
}