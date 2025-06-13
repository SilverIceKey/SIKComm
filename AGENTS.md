> 本文档用于指导协助本项目开发的 AI 代理理解核心目标、开发约定、模块职责与行为边界。

---

## 🎯 项目目标

- 构建一个 Android 上运行的通信工具库，名为 `SIKComm`
- 同时封装以下两种通信能力：
    - 串口通信（基于 Linux `/dev/ttyS*`，支持 Modbus RTU 协议）
    - 蓝牙 BLE 通信（支持扫描、连接、通知监听、写入）
- 满足工程设备（如工业网关、蓝牙传感器）管理需求
- 支持设备探测、通信调度、状态追踪、日志记录等通用逻辑

---

## 📦 模块职责

### serial/
- 负责原生串口打开、写入、读取、帧时间控制、缓冲清理
- 实现 Modbus RTU 协议封装，支持功能码：0x03、0x06、0x10
- 不依赖任何 native `.so` 文件

### bluetooth/
- 封装 Android 蓝牙 BLE 功能，包括设备扫描、连接池管理、特征值通信
- 支持最多 N 个设备并发连接，支持设备状态监听与心跳机制

### device/
- 管理所有设备的连接状态、最后通信时间、在线/离线判定
- 提供设备注册、标识、通信任务分发接口

### core/
- 提供统一的通信调度入口（比如 `CommBridge.sendTask()`）
- 实现任务重试、连接前 hook（如开启电源）、连接后通信等控制流程

---

## 🛠 开发规则（AI 必须遵守）

- 所有模块**不允许持有 Activity/Fragment Context**，只允许使用 `ApplicationContext`
- 所有串口通信操作需自动清空 input buffer 以防读取残留数据
- 所有 Modbus RTU 通信必须确保：
    - 写后延迟 ≥ 帧间最小时间（推荐 ≥3ms）
    - 校验 CRC16 正确后再认为有效响应
- 所有蓝牙设备通信必须支持断线重连机制，失败上限可配置
- 所有任务（串口/蓝牙）必须支持失败重试 + 延迟策略
- 所有任务执行过程中不得阻塞主线程，应在后台线程或调度器中运行
- 所有日志使用统一接口，例如 `CommLogger.debug(...)`
- 项目内不允许出现第三方无关库依赖（保持最小化）

---

## 🤖 AI 可以参与的开发行为

| 行为 | 说明 |
|------|------|
| ✅ 生成协议封装代码 | 如 Modbus 组包/解析、蓝牙命令管理等 |
| ✅ 生成设备状态管理类 | 设备注册、状态跟踪、心跳管理 |
| ✅ 实现通信任务封装 | 如 `Task(read register)` → 自动连接 + 发送 + 解析 |
| ✅ 生成多设备调度控制器 | 连接池、队列、状态更新逻辑 |
| ✅ 编写文档、生成模板代码 | 提供工程文档、测试用例、调试日志方案 |

---

## 🚫 禁止行为

| 行为 | 原因 |
|------|------|
| ❌ 使用 JNI/native `.so` | 不可移植，违背纯 Android 目标 |
| ❌ 使用 coroutine + ViewModel | 项目为非 UI 项，完全与生命周期隔离 |
| ❌ 调用第三方串口库（如 usb-serial-for-android） | 仅允许使用 `/dev/ttyS*` |
| ❌ 在非必要类中持有 Context | 串口和蓝牙逻辑应彻底解耦 UI 层 |

---

## ✅ 示例约定

```kotlin
// 正确的串口通信流程
val port = SerialPortIO("/dev/ttyS1")
port.clearInput()
port.write(modbusRequest)
Thread.sleep(5)
val response = port.read(9)
assert(ModbusRtuMaster.isValidResponse(response))
```

```kotlin
// 正确的蓝牙通信任务调用
BleTaskManager.submit(device = mac, task = ReadTemperatureTask())
```
