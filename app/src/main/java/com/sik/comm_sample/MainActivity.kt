package com.sik.comm_sample

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.core.protocol.ProtocolManager
import com.sik.comm_sample.can.CanCommands
import com.sik.comm_sample.can.CanHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

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
        CanHelper.connect()
        findViewById<Button>(R.id.button).setOnClickListener {
            thread {
                runBlocking(Dispatchers.IO) {
                    CanHelper.writeTo((CanCommands.forDevice(1)).controlLatch(1, 1)) {
                        Log.i("SIKComm", "CAN返回：${it}")
                    }
                }
            }
        }
    }
}