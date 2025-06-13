# SIKComm

**SIKComm (Smart Industrial Kit - Communication)** 是一个面向 Android 工程类设备的通信库，统一封装了 **串口通信（基于 `/dev/ttyS*` 的 Modbus RTU）** 与 **蓝牙（BLE）通信管理**，支持多设备管理、任务调度、自动重连、设备探测等工业场景核心需求。

---

## ✨ 特性一览

- ✅ 支持标准 Modbus RTU 协议，无需 `libserial_port.so`
- ✅ 串口通信基于 `ParcelFileDescriptor`，完全兼容 Android 工程机
- ✅ 支持蓝牙 BLE 多设备连接池管理
- ✅ 支持串口自动扫描、从站地址探测
- ✅ 支持通信心跳、设备掉线自动判定
- ✅ 无侵入式设计，支持项目中直接引入

---

## 📦 模块结构

sikcomm/
├── serial/ // 串口 + Modbus 通信封装
├── bluetooth/ // 蓝牙设备连接与通信调度
├── device/ // 多设备注册、状态监控
├── core/ // 通信桥抽象，统一调度接口
app/
├── ... // 示例与测试代码，仅用于演示库的使用方式

---

## 🚀 快速使用

```kotlin
val port = SerialPortIO("/dev/ttyS1")
val request = ModbusRtuMaster.buildReadRequest(1, 0x0000, 1)

port.clearInput()
port.write(request)
Thread.sleep(5)
val response = port.read(7)

if (ModbusRtuMaster.isValidResponse(response)) {
    println("合法响应：${response.joinToString(" ") { "%02X".format(it) }}")
}
// 完成通信后记得关闭串口
port.close()
```

同样的逻辑也可以通过 `CommTask` 在 `CommBridge` 中调度完成：

```kotlin
val bleManager = BleManager(appContext)
CommBridge.initBle(bleManager)

CommBridge.sendTask(
    SerialTask("device1", "/dev/ttyS1", byteArrayOf(0x00))
)
```

🧠 环境要求
Android 7.0+

已开启串口权限（root 或默认 666 权限）

若使用蓝牙：需动态申请 BLUETOOTH/LOCATION 权限

