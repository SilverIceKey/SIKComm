/*
 * Copyright 2025 折千
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
