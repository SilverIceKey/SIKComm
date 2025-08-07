package com.sik.comm.core.logger

import android.util.Log
import com.sik.comm.core.model.CommMessage

/**
 * 默认协议日志记录器，使用 Log 打印。
 */
object DefaultProtocolLogger : ProtocolLogger {

    private const val TAG = "SIKComm"

    override fun onConnect(deviceId: String) {
        Log.d(TAG, "Device [$deviceId] connected")
    }

    override fun onDisconnect(deviceId: String) {
        Log.d(TAG, "Device [$deviceId] disconnected")
    }

    override fun onSend(deviceId: String, message: CommMessage) {
        Log.d(TAG, "Send to [$deviceId]: ${message.command}, payload=${message.payload.size} bytes")
    }

    override fun onReceive(deviceId: String, message: CommMessage) {
        Log.d(TAG, "Receive from [$deviceId]: ${message.command}, payload=${message.payload.size} bytes")
    }

    override fun onError(deviceId: String, error: Throwable) {
        Log.e(TAG, "Error on [$deviceId]: ${error.message}", error)
    }
}
