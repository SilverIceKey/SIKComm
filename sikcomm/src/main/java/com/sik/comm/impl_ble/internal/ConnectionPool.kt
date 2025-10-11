package com.sik.comm.impl_ble.internal

import kotlinx.coroutines.channels.Channel

class ConnectionPool(maxGlobalConnections: Int) {
    private val tokens = Channel<Unit>(maxGlobalConnections).apply {
        repeat(maxGlobalConnections) { trySend(Unit) }
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        tokens.receive()         // acquire
        return try {
            block()
        } finally {
            tokens.send(Unit)    // release
        }
    }
}
