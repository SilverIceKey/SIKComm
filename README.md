# SIKComm

**统一 Android 通信层 · 半双工友好 · 轻量易用**

SIKComm 是一个专注于 Android 平台的轻量级通信框架，目标是提供一个 **稳定、简单、易接入** 的统一通信层，只解决三件事：

1. **连接设备**（串口 / CAN / USB-Serial / USB-HID）
2. **发送指令、接收数据**
3. **把原始字节流交给业务解析**

---

## ✨ 特点

- **四类统一通道**  
  原生串口、SocketCAN、USB-Serial、USB-HID 统一为 `CommChannel` 接口，接入方式一致。

- **强制串行化收发（半双工友好）**  
  虽然串口理论上全双工，但大量工业设备（尤其 RS485/RS232）属于半双工或接近半双工。  
  对每个设备采用「**单队列串行调度**」，确保收发不会互相抢占。

- **单向设备友好**  
  对于如扫码头等「只收不发」或「偶尔发」的设备，框架原生支持，不需要额外处理。

- **统一消息输入 / 输出模型**  
  框架统一处理底层收发，业务只需提供「如何解析字节流」即可。

- **无插件系统、无自动注入、无复杂扩展机制**  
  只保留通信层最必要的能力，保证可控、可维护、易接入。

---

## 📦 支持的通道类型

| 通道类型 | 适用场景 | 底层实现 |
|---------|---------|---------|
| **SerialPort** | RS232 / RS485 / USB-Serial（原生 /dev/ttySx） | JNI + termios |
| **SocketCAN** | 工业 CAN 总线（Linux SocketCAN） | JNI + socketcan |
| **USB-Serial** | USB 转串口设备（CDC / CH34x / CP210x / FTDI / PL2303） | [felHR85/UsbSerial](https://github.com/felHR85/UsbSerial) |
| **USB-HID** | HID 扫码枪 / 键盘类 / 其它 HID 设备 | Android `UsbManager` |

### SerialPort（RS232 / RS485 / USB-Serial 原生）
- 半双工队列模式
- 设备路径、波特率、校验等基本参数
- 支持「仅接收」模式
- 设备可持续读取数据并推送给解析逻辑

### SocketCAN
- 支持标准 CAN & CAN FD（取决于系统支持）
- 可选的基础 ID 过滤
- 与串口统一的收发模式

### USB-Serial
- 基于 felHR85/UsbSerial，支持主流 USB 转串口芯片
- 设备匹配：支持 VID/PID 白名单
- 驱动策略：Auto 自动探测，或强制指定驱动族
- 扫码枪拼包策略：400 ms 窗口内分段数据自动拼接，超时清空

### USB-HID
- 面向 class = 3 的 HID 设备
- 支持 interface / endpoint 自定义选择
- 自动处理 USB 权限请求

---

## 🏗️ 项目结构

```
SIKComm/
├── sikcomm/                    # 核心 Library 模块（发布到 JitPack）
│   ├── src/main/java/com/sik/comm/
│   │   ├── SikComm.kt          # 对外唯一入口
│   │   ├── CommChannel.kt      # 统一通道接口
│   │   ├── CommConfig.kt       # 配置基类（sealed interface）
│   │   ├── CommReceiver.kt     # 接收回调
│   │   ├── CommException.kt    # 统一异常体系
│   │   ├── SerialConfig.kt     # 串口配置
│   │   ├── CanConfig.kt        # CAN 配置
│   │   ├── UsbSerialConfig.kt  # USB-Serial 配置
│   │   ├── UsbHidConfig.kt     # USB-HID 配置
│   │   ├── ByteArrayExt.kt     # ByteArray 工具
│   │   ├── NativeSerial.kt     # 串口 JNI
│   │   ├── NativeCan.kt        # CAN JNI
│   │   ├── NativeUsbSerial.kt  # USB-Serial 封装
│   │   └── NativeUsbHid.kt     # USB-HID 封装
│   │   └── internal/           # 内部实现（不对外暴露）
│   │       ├── channel/        # 通道实现（BaseCommChannel + 4 个具体实现）
│   │       ├── transport/      # Transport 抽象 + 4 个实现 + MockTransport
│   │       ├── ioloop/         # HalfDuplexIoLooper / FullDuplexLooper
│   │       ├── usb/            # UsbPermissionBroker
│   │       ├── pipeline/       # ReceivePipeline / QrAssembleStage
│   │       ├── state/          # ChannelState 状态机
│   │       └── factory/        # ChannelFactory / ChannelRegistry
│   └── src/main/cpp/           # JNI / Native 层（CMake）
│       ├── sikcomm.cpp
│       ├── serialport_jni.cpp
│       └── socketcan_jni.cpp
├── app/                        # 示例 Demo App
│   └── src/main/java/com/sik/comm_sample/
│       ├── MainActivity.kt
│       └── UsbScanHelper.kt
├── gradle.properties           # VERSION=2.1.0
└── jitpack.yml                 # JitPack 构建配置
```

---

## 🚀 快速开始

### 1. 引入依赖

**JitPack**

在 `settings.gradle.kts` 中确保包含 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

在模块 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation("com.github.silvericekey:SIKComm:2.1.0")
}
```

### 2. 打开一个串口

```kotlin
import com.sik.comm.*

val config = SerialConfig(
    id = "serial-1",
    devicePath = "/dev/ttyS1",
    baudRate = 115200,
    dataBits = 8,
    stopBits = 1,
    parity = 0
)

val channel = SikComm.open(config)

channel.setReceiver { data, offset, length ->
    // 在这里处理收到的字节流：黏包、分帧、CRC、协议解析
    val payload = data.sliceFast(offset, length)
    Log.i("SIKComm", "RX: ${payload.toHex()}")
}

channel.open()

// 发送（suspend 函数，需在协程中调用）
lifecycleScope.launch {
    val written = channel.send(byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A, 0xC5, 0xCD))
    Log.i("SIKComm", "TX bytes: $written")
}
```

### 3. 打开 USB-Serial（扫码枪示例）

```kotlin
val config = UsbSerialConfig(
    id = "qr",
    context = this,
    deviceMatcher = UsbDeviceMatcher.QrScannerDemoWhitelist, // 或自定义 VidPidWhitelist
    qrAssemblePolicy = QrAssemblePolicy(
        mergeWindowMs = 400L,      // 400ms 内分段拼接
        resetTimeoutSeconds = 3,    // 超过 3s 清空旧数据
        dropCrLf = true             // 剔除 \r\n
    )
)

val channel = SikComm.open(config)

channel.setReceiver { data, offset, length ->
    val text = String(data.sliceFast(offset, length))
    Log.i("SIKComm", "扫码结果: $text")
}

channel.open()
```

### 4. 打开 USB-HID

```kotlin
val config = UsbHidConfig(
    id = "hid-1",
    context = this,
    deviceMatcher = UsbDeviceMatcher.VidPidWhitelist(
        setOf(0x1234 to 0x5678)
    ),
    interfaceIndex = 0
)

val channel = SikComm.open(config)
channel.setReceiver { data, offset, length ->
    Log.i("SIKComm", "HID RX: ${data.sliceFast(offset, length).toHex()}")
}
channel.open()
```

### 5. 打开 SocketCAN

```kotlin
val config = CanConfig(
    id = "can0",
    ifName = "can0",
    bitrate = 500_000,
    fdMode = false
)

val channel = SikComm.open(config)
channel.setReceiver { data, offset, length ->
    Log.i("SIKComm", "CAN RX: ${data.sliceFast(offset, length).toHex()}")
}
channel.open()

lifecycleScope.launch {
    channel.send(byteArrayOf(0x01, 0x02, 0x03))
}
```

---

## 🔧 数据解析（由业务提供）

业务只需提供一段逻辑，用来把底层收到的字节流转成业务结构，例如：

- 起始位、结束位
- 帧头 / 帧尾
- 长度字段
- CRC 校验
- 分包 / 拆包（粘包处理）

框架不限制格式，保持最大自由度。

---

## ⚠️ 版本兼容性

### 2.x 系列

- 与 1.x **完全不兼容**
- 移除 BLE、TCP、Mock
- 移除所有插件与自动注入体系
- API 全部重写，只保留通信所需能力
- 配置格式完全不同
- 推荐视为全新框架使用

### 当前版本

> 当前代码对应版本：**2.1.0**

### 2.1.0 行为变化注意

- `send()` 在通道未打开时，从 `IllegalStateException` 改为 `CommException.NotOpen`。
  - 若业务层精确捕获 `IllegalStateException`，请改为捕获 `CommException` 或 `RuntimeException`。
- 新增 `CommException` 异常体系，支持精细化错误处理：
  ```kotlin
  try {
      channel.send(data)
  } catch (e: CommException.NotOpen) {
      // 通道未打开
  } catch (e: CommException.WriteTimeout) {
      // 写入超时
  } catch (e: CommException.TransportError) {
      // 底层传输错误，e.code 为错误码
  }
  ```

---

## 📜 License

- **1.0.16 及之前版本：MIT License**
- **自 1.0.17 起：Apache License 2.0**

详见 [LICENSE](./LICENSE) 文件。

---

## 📌 适用场景

- RS485 半双工设备
- 串口传感器、下位机控制
- 工业 CAN 总线（SocketCAN）
- USB 转串口模块（CH340、CP2102、FT232 等）
- HID 扫码枪 / 键盘类设备
- 强依赖串行化收发的设备通信

---

## 🛠️ 环境要求

| 项目 | 版本 |
|------|------|
| Android minSdk | 24 |
| Android compileSdk | 35 |
| JDK | 11+ |
| Kotlin | 2.0.21 |
| AGP | 8.9.2 |
| CMake | 3.22.1 |
