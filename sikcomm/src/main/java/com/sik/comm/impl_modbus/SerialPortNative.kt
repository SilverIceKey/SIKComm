package com.sik.comm.impl_modbus

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class SerialPortNative {
    companion object {
        init { System.loadLibrary("sikcomm") } // <<=== 你要的 loadLibrary
        @JvmStatic external fun ensurePermWithSu(path: String, mode: Int /* 0666=438 */): Boolean
    }

    // 供 native 读取的字段（close 时用）
    @Suppress("MemberVisibilityCanBePrivate")
    var mFd: FileDescriptor? = null
        private set

    val inputStream: FileInputStream? get() = mFd?.let { FileInputStream(it) }
    val outputStream: FileOutputStream? get() = mFd?.let { FileOutputStream(it) }

    external fun open(
        path: String,
        baudRate: Int,
        dataBits: Int,
        parity: Int,    // 0:none 1:odd 2:even
        stopBits: Int,  // 1 or 2
        flags: Int
    ): FileDescriptor?

    external fun close()

    /**
     * 一把梭：先 su 改权限，再 open
     */
    fun openAndInit(
        path: String,
        baudRate: Int,
        dataBits: Int = 8,
        parity: Int = 0,
        stopBits: Int = 1,
        flags: Int = 0,
        trySu: Boolean = true
    ): Boolean {
        if (trySu) runCatching { ensurePermWithSu(path, 0x666) }
        val fd = open(path, baudRate, dataBits, parity, stopBits, flags) ?: return false
        mFd = fd
        return true
    }
}