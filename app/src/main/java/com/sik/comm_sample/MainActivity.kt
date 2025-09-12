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
import androidx.lifecycle.lifecycleScope
import com.sik.comm.impl_ble.android.AndroidBlePlatformImpl
import com.sik.comm.impl_ble.android.ScanOptions
import com.sik.comm.impl_ble.scanAddedSince
import com.sik.comm_sample.PermissionUtils.hasPermission
import com.sik.sikcore.extension.setDebouncedClickListener
import kotlinx.coroutines.launch


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

    fun searchBluetoothDevice() {
        // 检查权限状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanPermission = hasPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            val advertisePermission = hasPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            val connectPermission = hasPermission(this, Manifest.permission.BLUETOOTH_CONNECT)

            if (!scanPermission || !advertisePermission || !connectPermission) {
                // 有一个或多个权限未授予，需要申请权限
                com.sik.comm_sample.PermissionUtils.requestPermission(
                    this, arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ), 1001
                )
            } else {
                // 权限已授予，跳转到蓝牙页面
            }
        } else {
            // 处理 Android 12 之前的版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkLocationPermissions()
            } else {
                // Android 版本低于 M，直接跳转到蓝牙页面
            }
        }
    }

    fun checkLocationPermissions() {
        // 检查权限状态
        val locationPermission =
            hasPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val accessPermission =
            hasPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!locationPermission || !accessPermission) {
            // 有一个或多个权限未授予，需要申请权限
            com.sik.comm_sample.PermissionUtils.requestPermission(
                this, arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ), 1002
            )
        } else {
            // 权限已授予，跳转到蓝牙页面
        }
    }


    private fun initBle() {
        val platform = AndroidBlePlatformImpl(this)
        lifecycleScope.launch {
            val result = scanAddedSince(platform, 18_000, emptySet(), ScanOptions())
            Log.i("BLE", "result:${result}")
        }
    }
}