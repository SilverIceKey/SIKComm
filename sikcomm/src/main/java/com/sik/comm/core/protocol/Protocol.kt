package com.sik.comm.core.protocol

import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.TxPlan
import com.sik.comm.core.policy.ChainPolicy

/**
 * 通信协议的统一接口。
 * 所有具体协议（如 BLE、TCP、Mock 等）都应实现该接口，
 * 以适配统一的上层调用方式。
 */
interface Protocol {

    /**
     * 向指定设备发送消息，并获取响应。
     *
     * @param deviceId 设备唯一标识（如 MAC、IP）
     * @param msg 待发送的消息体
     * @return 响应消息体（通常是设备回复）
     */
    suspend fun send(deviceId: String, msg: CommMessage): CommMessage

    /**
     * 多包链式发送：按 plan.frames 顺序写出，每步交给 policy 决定如何等待/校验/是否继续。
     */
    suspend fun sendChain(deviceId: String, plan: TxPlan, policy: ChainPolicy): List<CommMessage>

    /**
     * 主动连接指定设备。
     *
     * @param deviceId 设备唯一标识
     */
    fun connect(deviceId: String)

    /**
     * 主动断开指定设备连接。
     *
     * @param deviceId 设备唯一标识
     */
    fun disconnect(deviceId: String)

    /**
     * 判断当前设备是否已连接。
     *
     * @param deviceId 设备唯一标识
     * @return true 表示已连接，false 表示未连接
     */
    fun isConnected(deviceId: String): Boolean
}
