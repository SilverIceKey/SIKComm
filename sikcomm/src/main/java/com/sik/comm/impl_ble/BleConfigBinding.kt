package com.sik.comm.impl_ble

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.protocol.ProtocolType
import java.util.UUID

// 你原来的 BleConfig（保持不变）

/** 由 Seed 生成最终 BleConfig；deviceId 可不传，默认=MAC/NAME/时间戳 */
fun BleConfigSeed.bind(
    deviceId: String? = null,
    mac: String? = null,
    deriveDeviceId: (mac: String?, name: String?) -> String = BleDefaults::defaultDeviceId,
    nameHint: String? = null
): BleConfig {
    val finalDeviceId = deviceId ?: deriveDeviceId(mac, nameHint)

    return BleConfig(
        deviceId = finalDeviceId,

        whitelist = if (!mac.isNullOrBlank()) setOf(mac) else this.whitelist,
        deviceNameExact = this.deviceNameExact,
        deviceNamePrefix = this.deviceNamePrefix,
        serviceUuids = this.serviceUuids,
        manufacturerId = this.manufacturerId,
        manufacturerData = this.manufacturerData,
        manufacturerMask = this.manufacturerMask,

        service = this.service ?: throw IllegalStateException("Gatt service UUID is required at connect-time."),
        writeChar = this.writeChar ?: throw IllegalStateException("Gatt write characteristic UUID is required at connect-time."),
        notifyChar = this.notifyChar ?: throw IllegalStateException("Gatt notify characteristic UUID is required at connect-time."),

        expectMtu = this.expectMtu,
        enableNotifyOnReady = this.enableNotifyOnReady,

        connectTimeoutMs = this.connectTimeoutMs,
        discoverTimeoutMs = this.discoverTimeoutMs,
        subscribeTimeoutMs = this.subscribeTimeoutMs,
        writeTimeoutMs = this.writeTimeoutMs,
        scanWindowMs = this.scanWindowMs,
        totalScanBudgetMs = this.totalScanBudgetMs,

        preferWriteWithResponse = this.preferWriteWithResponse,
        interChunkDelayMs = this.interChunkDelayMs,
        maxInFlightChunks = this.maxInFlightChunks,
        maxRetriesPerChunk = this.maxRetriesPerChunk,

        maxGlobalConnections = this.maxGlobalConnections,

        requestKeyOf = this.requestKeyOf,
        notifyKeyOf = this.notifyKeyOf,

        additionalPlugins = this.additionalPlugins,
        additionalInterceptors = this.additionalInterceptors
    )
}