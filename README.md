# SIKComm：多协议统一通信框架企划方案（v2）

---

## 🧭 项目简介
**SIKComm** 是一个高扩展性、模块化、插件化的多协议统一通信框架，旨在简化工业场景中的设备通信开发。

支持包括 BLE、TCP、串口（485/232）、Mock 等多种协议，通过统一接口屏蔽协议差异，具备插件扩展、任务调度、Mock 测试等能力，可作为中大型项目通信层标准化组件使用。

---

## 📐 核心架构模块

### 1. `ProtocolManager`（协议中心）
- 管理协议生命周期、连接状态和多设备连接池。

### 2. `Protocol` 接口
```kotlin
interface Protocol {
    suspend fun send(deviceId: String, msg: CommMessage): CommMessage
    fun connect(deviceId: String)
    fun disconnect(deviceId: String)
    fun isConnected(deviceId: String): Boolean
}
```

### 3. `ProtocolConfig`
- 每个设备的配置项，包括：
    - 协议类型（BLE/TCP/...）
    - 唯一 ID（MAC/IP/...）
    - 插件 / 拦截器 列表
    - 是否启用 Mock
    - 协议专属参数（端口、UUID...）

### 4. `CommMessage`
```kotlin
data class CommMessage(
    val command: String,
    val payload: ByteArray,
    val metadata: Map<String, Any> = emptyMap()
)
```

### 5. `InterceptorChain`（拦截器链）
- 类似 OkHttp 的消息预处理机制：加密、校验、Mock 等。

### 6. `Plugin`（插件）
- 生命周期插件（连接、断开、收发）
- 典型场景：心跳、状态上报、重连等

### 7. `CommInjection`（自动注入机制）
- 支持拦截器/插件自动注册与条件过滤（黑白名单）

### 8. `MockInterceptor`
- 无设备模拟通信，支持延迟、失败、模拟数据响应

### 9. `DeviceStateCenter`
- 提供设备连接状态订阅能力
- 枚举：Disconnected / Connecting / Ready / Busy

### 10. `MessageCodec`
```kotlin
interface MessageCodec {
    fun encode(comm: CommMessage): ByteArray
    fun decode(bytes: ByteArray): CommMessage
}
```

### 11. `DeviceTaskManager`
```kotlin
suspend fun <T> withLock(deviceId: String, block: suspend () -> T): T
```
- 控制串行任务，避免连接冲突

### 12. `ProtocolLogger`
- 日志事件上报：连接、断开、收发、异常等

---

## 🌐 支持协议类型（可扩展）
- ✅ BLE
- ✅ TCP
- ✅ RS485
- ✅ MOCK
- 🛠 计划支持：CAN、MQTT、USB-HID

---

## 🧩 插件类型示例
| 插件名 | 功能 |
|--------|------|
| HeartbeatPlugin | 定时心跳、断线感知 |
| ReconnectPlugin | 掉线自动重连 |
| BusyProtectPlugin | 执行期间防断连接 |
| LoggerPlugin | 日志打点与追踪 |


## 🛡️ 拦截器类型示例
| 拦截器名 | 功能 |
|----------|------|
| LoggingInterceptor | 打印请求/响应 |
| CrcCheckInterceptor | 自动加校 CRC 校验码 |
| SecurityInterceptor | 命令加解密 |
| MockInterceptor | 拦截真实通信并返回模拟结果 |

---

## 🔧 MockProfile 示例
```kotlin
data class MockProfile(
    val delayMillis: Long = 0,
    val failEveryN: Int = 0,
    val mockResponse: (CommMessage) -> CommMessage
)
```

---

## 📦 推荐项目结构
```
SIKComm/
├── core/              // 核心接口与抽象类
├── impl-ble/          // BLE 协议实现
├── impl-tcp/          // TCP 协议实现
├── impl-mock/         // Mock 实现
├── plugins/           // 所有插件统一存放
│   ├── heartbeat/     // 心跳插件
│   ├── logger/        // 日志插件
│   └── reconnect/     // 重连插件
├── interceptors/      // 所有拦截器统一存放
│   └── crc/           // CRC 拦截器
├── codec/             // 编解码模块
├── test-tool/         // 测试工具 / 模拟器
```

---

## 📈 落地流程建议
1. ✅ 实现 BLE + MOCK 协议支持
2. ✅ 封装 `ProtocolManager` 中心调度
3. ✅ 构建自动注入系统 `CommInjection`
4. ✅ 插件系统与心跳、日志、状态跟踪落地
5. ✅ 编写调试用 DEMO 与测试工具
6. 🔄 接入业务真实通信命令 / 控制逻辑
7. 🔄 模块独立优化、支持 AIDL/多进程可选方案

---

## 🎯 最终愿景
> **SIKComm**：让设备通信不再混乱，屏蔽底层协议细节，支持灵活扩展与稳定复用，助力工业/物联网/设备控制类 App 快速落地、稳定上线。

---

- 2025年10月16之前版本（tag: 1.0.16）使用 MIT License
- 自 1.0.17 起，改用 Apache License 2.0

