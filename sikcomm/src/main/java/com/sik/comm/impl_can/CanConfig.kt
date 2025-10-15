package com.sik.comm.impl_can

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.plugin.CommPlugin
import com.sik.comm.core.protocol.ProtocolType


/**
 * CAN 协议配置（二级抽象）。
 *
 * 用途：
 * - 提供底层 I/O 初始化所需参数（接口名、波特率、采样点、是否在 open 前自动拉起 canX 等）
 * - 提供协议层路由所需参数（tx/rx ID 基址、默认 nodeId）
 *
 * 建议 deviceId 形如 "can0@12"（接口名 + NodeId）。若不在 deviceId 中带 NodeId，
 * 也可以在发送时通过 CommMessage.metadata["nodeId"] 传入，或设置 defaultNodeId。
 *
 * @param deviceId       设备唯一标识（建议 "canX@NodeId"）
 * @param interfaceName  SocketCAN 接口名（例如 "can0"）
 * @param bitrate        波特率（bps），用于“拉起接口”的系统命令；系统层已配置时可保留默认
 * @param samplePoint    采样点（0.0~1.0；null 则不在命令中附带）
 * @param upOnOpen       open() 前是否自动通过系统命令把接口 down→type can→up（开发/调试便捷）
 * @param useSu          是否通过 su -c 执行上述命令（量产建议 false，交由系统 init.rc 处理）
 * @param readTimeoutMs  PF_CAN 读超时（JNI 里设置 SO_RCVTIMEO）
 * @param filters        CAN RAW 过滤器 (id, mask) 列表；为空表示不过滤（接收所有）
 * @param txBaseId       发送帧 ID 基址，默认 0x600（按你方协议文档）
 * @param rxBaseId       接收帧 ID 基址，默认 0x600（若按 CANopen 标准应答则用 0x580）
 * @param defaultNodeId  可选默认节点 ID（没传 metadata["nodeId"] 时使用）
 * @param additionalPlugins      协议默认插件列表（心跳、重连、上报等）
 * @param additionalInterceptors 协议默认拦截器列表（日志、Mock、CRC 等）
 */
open class CanConfig(
    deviceId: String,
    val interfaceName: String,
    // —— I/O 初始化参数 —— //
    val bitrate: Int = 1_000_000,
    val samplePoint: Double? = 0.875,
    val upOnOpen: Boolean = true,
    val useSu: Boolean = true,
    val readTimeoutMs: Int = 2000,
    val filters: List<Pair<Int, Int>> = emptyList(),
    /** 新增：SDO 方言（默认 CANopen） */
    val sdo: SdoDialect = SdoDialect.CANOPEN,

    // —— 协议路由参数 —— //
    val txBaseId: Int = 0x600,
    val rxBaseId: Int = 0x600,
    val defaultNodeId: Int? = null,

    // —— 扩展 —— //
    override val additionalPlugins: List<CommPlugin> = emptyList(),
    override val additionalInterceptors: List<CommInterceptor> = emptyList()
) : ProtocolConfig(
    deviceId = deviceId,
    protocolType = ProtocolType.CAN,
)
