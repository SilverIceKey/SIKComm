package com.sik.comm_sample

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.impl_ble.BleConfigSeed
import com.sik.comm.impl_ble.BleScanConfig
import com.sik.comm.impl_ble.ScanOptions
import com.sik.comm.impl_ble.ScanStrategy
import com.sik.comm.impl_ble.scan.AndroidBleScanner
import com.sik.comm.impl_ble.scan.ScanResolver
import com.sik.comm.impl_ble.scan.toSeeds
import com.sik.comm_sample.PermissionUtils.hasPermission
import com.sik.comm_sample.ble.BleConst
import com.sik.sikcore.extension.setDebouncedClickListener
import com.sik.sikcore.extension.toJson
import com.sik.sikcore.thread.ThreadUtils


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

        findViewById<Button>(R.id.button).setDebouncedClickListener {
            initBle()
        }
    }

    private fun initBle() {

    }
}