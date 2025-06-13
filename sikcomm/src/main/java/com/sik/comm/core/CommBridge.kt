package com.sik.comm.core

import com.sik.comm.device.DeviceManager
import com.sik.comm.serial.SerialPortIO
import com.sik.comm.bluetooth.BleManager
import com.sik.comm.task.BleTask
import com.sik.comm.task.CommTask
import com.sik.comm.task.SerialTask
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unified communication entry that schedules tasks.
 */
object CommBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private var bleManager: BleManager? = null

    fun initBle(manager: BleManager) {
        bleManager = manager
    }

    /**
     * Submit [task] for execution. The task will run on a background thread
     * and be retried if it fails.
     */
    fun sendTask(task: CommTask, retries: Int = 3, delayMs: Long = 100) {
        executor.execute {
            var attempt = 0
            while (attempt < retries) {
                DeviceManager.updateDevice(task.deviceId)
                val success = when (task) {
                    is SerialTask -> handleSerial(task)
                    is BleTask -> handleBle(task)
                }
                if (success) break
                attempt++
                if (attempt < retries) {
                    TimeUnit.MILLISECONDS.sleep(delayMs)
                }
            }
        }
    }

    private fun handleSerial(task: SerialTask): Boolean {
        return try {
            val io = SerialPortIO(task.portPath)
            io.clearInput()
            io.write(task.payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun handleBle(task: BleTask): Boolean {
        val manager = bleManager ?: return false
        return manager.send(task.mac, task.payload)
    }
}
