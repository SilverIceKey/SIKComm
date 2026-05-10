# SIKComm 使用指南

> 对应版本：2.1.0+

---

## 一、基本使用流程

所有通道遵循统一的三步模型：

```kotlin
// 1. 创建配置
val config = SerialConfig(id = "my-serial", devicePath = "/dev/ttyS1", baudRate = 115200)

// 2. 打开通道
val channel = SikComm.open(config)

// 3. 设置回调并打开
channel.setReceiver { data, offset, length ->
    // 处理收到的字节流
}
channel.open()
```

---

## 二、四种通道详解

### 2.1 原生串口（SerialPort）

适用于 RS232 / RS485 / 原生 USB-Serial（/dev/ttySx）。

```kotlin
val config = SerialConfig(
    id = "serial-1",
    devicePath = "/dev/ttyS1",
    baudRate = 115200,
    dataBits = 8,
    stopBits = 1,
    parity = 0,        // 0=None, 1=Odd, 2=Even
    readTimeoutMs = 500,
    writeTimeoutMs = 500
)

val channel = SikComm.open(config)

channel.setOpenCallback { success, error ->
    if (!success) Log.e("SIKComm", "串口打开失败: ${error?.message}")
}

channel.setReceiver { data, offset, length ->
    val payload = data.sliceFast(offset, length)
    Log.i("SIKComm", "RX: ${payload.toHex()}")
}

channel.open()

// 发送（需在协程中调用）
lifecycleScope.launch {
    try {
        val written = channel.send(byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A))
        Log.i("SIKComm", "TX bytes: $written")
    } catch (e: CommException.NotOpen) {
        Log.e("SIKComm", "通道未打开")
    } catch (e: CommException.WriteTimeout) {
        Log.e("SIKComm", "写入超时")
    }
}
```

### 2.2 SocketCAN

适用于工业 CAN 总线（Linux SocketCAN）。

```kotlin
val config = CanConfig(
    id = "can0",
    ifName = "can0",
    bitrate = 500_000,   // 可选：如果系统已配置好 can0，可传 null
    fdMode = false,
    readTimeoutMs = 500,
    writeTimeoutMs = 500
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

### 2.3 USB-Serial（扫码枪等）

适用于 USB 转串口设备（CH340、CP2102、FT232 等）。

```kotlin
val config = UsbSerialConfig(
    id = "qr-scanner",
    context = this,
    deviceMatcher = UsbDeviceMatcher.QrScannerDemoWhitelist,
    qrAssemblePolicy = QrAssemblePolicy(
        mergeWindowMs = 400L,
        resetTimeoutSeconds = 3,
        dropCrLf = true
    )
)

val channel = SikComm.open(config)

// USB 权限是异步的，强烈建议使用 OpenCallback
channel.setOpenCallback { success, error ->
    if (success) {
        Log.i("SIKComm", "扫码枪已就绪")
    } else {
        Log.e("SIKComm", "扫码枪打开失败: ${error?.message}")
    }
}

channel.setReceiver { data, offset, length ->
    val text = String(data.sliceFast(offset, length), Charsets.UTF_8)
    Log.i("SIKComm", "扫码结果: $text")
}

channel.open()  // 会触发系统 USB 权限对话框
```

### 2.4 USB-HID

适用于 HID 扫码枪、键盘类等 class=3 设备。

```kotlin
val config = UsbHidConfig(
    id = "hid-scanner",
    context = this,
    deviceMatcher = UsbDeviceMatcher.VidPidWhitelist(
        setOf(0x1234 to 0x5678)
    ),
    interfaceIndex = 0
)

val channel = SikComm.open(config)

channel.setOpenCallback { success, error ->
    if (!success) {
        Toast.makeText(this, "请插入 HID 设备并授权", Toast.LENGTH_SHORT).show()
    }
}

channel.setReceiver { data, offset, length ->
    Log.i("SIKComm", "HID RX: ${data.sliceFast(offset, length).toHex()}")
}

channel.open()
```

---

## 三、生命周期管理

### 3.1 打开

- `open()` 是**幂等**的：已打开时重复调用直接返回
- Serial / CAN 是**同步**的：`open()` 返回时即完成或抛异常
- USB-Serial / USB-HID 是**异步**的：`open()` 触发权限请求，结果通过 `OpenCallback` 通知

### 3.2 关闭

```kotlin
override fun onDestroy() {
    super.onDestroy()
    channel.close()  // 释放资源、停止协程、关闭 JNI 句柄
}
```

### 3.3 状态查询

```kotlin
if (channel.isOpen()) {
    lifecycleScope.launch { channel.send(data) }
}
```

---

## 四、错误处理

### 4.1 异常体系

```kotlin
try {
    channel.send(data)
} catch (e: CommException.NotOpen) {
    // 通道未打开
} catch (e: CommException.OpenFailed) {
    // 打开失败，e.code 为错误码
} catch (e: CommException.WriteTimeout) {
    // 写入超时
} catch (e: CommException.TransportError) {
    // 底层传输错误，e.code 为错误码
}
```

### 4.2 常见错误场景

| 场景 | 异常 / 行为 | 处理建议 |
|------|-----------|---------|
| 设备未插 | USB: `OpenCallback(false, ...)` / Serial: `OpenFailed` | 提示用户检查设备连接 |
| USB 权限拒绝 | `OpenCallback(false, ...)` | 提示用户在系统设置中授权 |
| 发送时通道已关闭 | `CommException.NotOpen` | 检查 `isOpen()` 后再发送 |
| 写入超时 | `CommException.WriteTimeout` | 检查设备是否响应，或增加 timeout |

---

## 五、无硬件环境测试（MockTransport）

在 CI 或开发机上，可使用 `MockTransport` 进行纯模拟测试。

### 5.1 基础用法

```kotlin
val mockTransport = MockTransport()
val config = SerialConfig(id = "test", devicePath = "/dev/ttyTest", baudRate = 9600)

// 注入 MockTransport（仅内部测试可用）
val channel = SerialChannelImpl(config, mockTransport)

// 模拟设备发数据
mockTransport.injectRead(channel.handle, byteArrayOf(0x01, 0x02))

// 验证写入内容
val records = mockTransport.writeRecords()
assertArrayEquals(byteArrayOf(0xAB, 0xCD), records[0].data)
```

### 5.2 模拟错误

```kotlin
mockTransport.nextOpenFail = true          // 模拟 open 失败
mockTransport.nextReadError = -1           // 模拟读错误
mockTransport.readDelayMs = 200            // 模拟 200ms 读延迟
mockTransport.autoResponse = responseBytes // 模拟请求-应答
```

### 5.3 运行测试

```bash
./gradlew :sikcomm:testDebugUnitTest
```

---

## 六、最佳实践

1. **先 setReceiver 再 open()**  
   避免 open 成功到 setReceiver 之间丢失第一包数据。

2. **USB 设备务必使用 OpenCallback**  
   不要假设 `open()` 调用后设备立即可用。

3. **send() 在协程中调用**  
   `send()` 是 suspend 函数，不要在主线程直接调用。

4. **生命周期绑定**  
   在 `Activity.onDestroy()` 或 `ViewModel.onCleared()` 中调用 `channel.close()`。

5. **数据解析在 Receiver 中完成**  
   框架只负责原始字节流，黏包、分帧、CRC 由业务层在 `CommReceiver` 中处理。
