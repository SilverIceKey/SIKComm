package com.sik.comm_sample.ble

import com.sik.comm.impl_ble.GattRoute
import java.util.UUID

object KeyGattRouteProvider {
    fun route() = GattRoute(
        service = UUID.fromString(BleConst.SERVICE_UUID),
        writeChar = UUID.fromString(BleConst.WRITE_UUID),
        notifyChar = UUID.fromString(BleConst.INDICATE_UUID),
        requestMtu = BleConst.MTU
    )
}
