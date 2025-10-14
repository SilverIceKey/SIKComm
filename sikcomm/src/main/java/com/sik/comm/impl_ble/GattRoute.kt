package com.sik.comm.impl_ble

import java.util.UUID

data class GattRoute(
    val service: UUID,
    val writeChar: UUID,
    val notifyChar: UUID,
)
