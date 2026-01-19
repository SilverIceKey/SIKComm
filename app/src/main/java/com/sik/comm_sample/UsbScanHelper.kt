package com.sik.comm_sample

import android.util.Log
import com.sik.comm.CommChannel
import com.sik.comm.CommConfig
import com.sik.comm.CommReceiver
import com.sik.comm.SikComm

object UsbScanHelper {

    private const val TAG = "SIKComm-Sample"

    private var commChannel: CommChannel? = null
    private var pendingReceiver: CommReceiver? = null

    fun init(config: CommConfig) {
        Log.i(TAG, "UsbScanHelper.init config=${config::class.java.simpleName}")
        commChannel = SikComm.open(config)

        // 先挂 receiver（避免第一包丢失）
        pendingReceiver?.let {
            commChannel?.setReceiver(it)
            Log.i(TAG, "UsbScanHelper.init applied pendingReceiver")
        }

        commChannel?.open()
        Log.i(TAG, "UsbScanHelper.init open called, channel=${commChannel?.javaClass?.simpleName}")
    }

    fun setReceiver(onReceiver: (ByteArray, Int, Int) -> Unit) {
        val r = CommReceiver { data, offset, length -> onReceiver(data, offset, length) }
        pendingReceiver = r
        commChannel?.setReceiver(r)
        Log.i(TAG, "UsbScanHelper.setReceiver installed")
    }

    suspend fun send(data: ByteArray, timeout: Int = 500) {
        commChannel?.send(data, timeout)
    }

    fun release() {
        try {
            if (commChannel?.isOpen() == true) commChannel?.close()
        } finally {
            commChannel = null
        }
        Log.i(TAG, "UsbScanHelper.release")
    }
}
