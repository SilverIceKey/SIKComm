package com.sik.comm.impl_can

internal class NativeCan {
    companion object { init { System.loadLibrary("data") } }

    external fun openSocket(ifName: String): Int
    external fun setReadTimeoutMs(fd: Int, timeoutMs: Int): Int
    external fun setFilters(fd: Int, ids: IntArray, masks: IntArray): Int
    external fun writeFrame(fd: Int, canId: Int, payload: ByteArray, len: Int): Int
    external fun readFrame(fd: Int, outId: IntArray, outData: ByteArray): Int
    external fun closeSocket(fd: Int)
    external fun setRawLoopback(fd: Int, enabled: Boolean): Int
    external fun setRecvOwnMsgs(fd: Int, enabled: Boolean): Int
}
