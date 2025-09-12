package com.sik.comm.impl_ble

import java.util.concurrent.Semaphore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

/**
 * Global connection pool: real concurrency is only consumed while connecting
 * and discovering/subscribing. Scanning and advertisement waiting do NOT take a permit.
 */
class ConnectionPool(maxGlobalConnections: Int) {
    private val sem = Semaphore(maxGlobalConnections, true)

    suspend fun <T> withPermit(block: suspend () -> T): T =
        suspendCancellableCoroutine { cont ->
            sem.acquire()
            cont.invokeOnCancellation { sem.release() }
            GlobalScope.launch {
                try { cont.resume(block()) } finally { sem.release() }
            }
        }
}