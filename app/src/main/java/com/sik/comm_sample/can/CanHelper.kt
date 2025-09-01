package com.sik.comm_sample.can

import android.util.Log
import com.sik.comm.core.protocol.ProtocolManager
import com.sik.comm.core.protocol.ProtocolType
import com.sik.comm.impl_can.CanProtocol
import com.sik.comm.impl_can.SdoRequest
import com.sik.comm.impl_can.SdoResponse
import com.sik.comm.impl_can.toCommMessage
import com.sik.comm.impl_can.toSdoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * can总线帮助工具
 */
object CanHelper {
    /**
     * 作用域
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 节点对应的设备类型列表
     */
    private val nodeMap: HashMap<Int, CanCommands.GenericCommands> = hashMapOf()

    /**
     * 扫描节点范围
     */
    private val scanRange: IntRange = 1..2

    /**
     * 连接
     */
    fun connect() {
        val canProtocol = CanProtocol()
        ProtocolManager.register(ProtocolType.CAN, canProtocol)
        // 绑定配置（每个节点一个 deviceId，建议 "can0@<nodeId>"）
        ProtocolManager.bindDeviceConfig(CustomCanConfig.instance)
        canProtocol.registerConfig(CustomCanConfig.instance)
        // 连接
        ProtocolManager.connect(CustomCanConfig.instance.deviceId)
    }

    /**
     * 读取
     */
    fun readFrom(req: SdoRequest.Read, callback: (SdoResponse.ReadData) -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                ProtocolManager.getProtocol(CustomCanConfig.instance.deviceId)
                    .send(CustomCanConfig.instance.deviceId, req.toCommMessage())
            }.onSuccess { rsp ->
                callback(rsp.toSdoResponse() as SdoResponse.ReadData)
            }.onFailure {
                Log.i("SIKComm", "读取失败:${it}")
            }
        }
    }

    /**
     * 写入到
     */
    fun writeTo(req: SdoRequest.Write, callback: (SdoResponse.WriteAck) -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                ProtocolManager.getProtocol(CustomCanConfig.instance.deviceId)
                    .send(CustomCanConfig.instance.deviceId, req.toCommMessage())
            }.onSuccess { rsp ->
                callback(rsp.toSdoResponse() as SdoResponse.WriteAck)
            }.onFailure {
                Log.i("SIKComm", "写入失败:${it}")
            }
        }
    }
}