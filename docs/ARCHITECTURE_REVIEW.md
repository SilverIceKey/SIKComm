# SIKComm 架构诊断与优化建议

> 约束：**不修改对外 API**（`SikComm.open()`、`CommChannel`、`CommConfig` 子类、`CommReceiver` 保持原样）
>
> 目标：消除强行统一带来的代码腐化，提升可维护性和扩展性。

---

## 一、当前核心问题诊断

### 1.1 四个 ChannelImpl 都是「大泥球」

每个实现类同时承担了 5 层职责：

| 职责 | Serial | CAN | USB-Serial | USB-HID |
|------|--------|-----|------------|---------|
| 连接生命周期（open/close/isOpen） | ✅ | ✅ | ✅ | ✅ |
| IO 循环调度 | ✅（读优先） | ✅（全双工） | ✅（读优先） | ✅（读优先，但读错误处理不同） |
| USB 权限管理 | ❌ | ❌ | ✅ | ✅ |
| 接收数据加工 | ❌ | ❌ | ✅（QrAssembler） | ❌ |
| 协程作用域管理 | ✅ | ✅ | ✅ | ✅ |

**结果**：SerialChannelImpl 231 行、UsbSerialChannelImpl 326 行、UsbHidChannelImpl 208 行、CanChannelImpl 156 行。逻辑越堆越多，改一处怕牵三处。

### 1.2 重复代码严重，但无法复用

- **读优先 IO 循环**：SerialChannelImpl 和 UsbSerialChannelImpl 的 `startIoLoop()` 几乎一模一样（差在 JNI 调用名和读错误处理）。
- **USB 权限管理**：UsbSerialChannelImpl 和 UsbHidChannelImpl 的 `permissionReceiver`、`requestPermission()`、`openInternal()` 流程几乎一模一样（差在 action 后缀和 NativeXX 调用名）。

### 1.3 同一接口下行为不一致

`CommChannel.send()` 的底层行为完全不同，但调用方无感知：

| 通道 | send 实际行为 | 风险 |
|------|-------------|------|
| Serial / USB-Serial / USB-HID | 投递到 `Channel<WriteJob>`，由 IO 循环串行处理 | 延迟不可控，队列可能堆积 |
| CAN | 直接在 `Dispatchers.IO` 调用 JNI write | 与 read 并发，虽然物理全双工，但无背压控制 |

对外接口没暴露任何差异信号，调试时只能靠打日志猜。

### 1.4 错误处理风格分裂

| 场景 | Serial | CAN | USB-Serial | USB-HID |
|------|--------|-----|------------|---------|
| open 失败 | `require()` 抛异常 | `require()` 抛异常 | `Log.e` 静默返回 | `Log.e` 静默返回 |
| send 时未 open | `check()` 抛异常 | `check()` 抛异常 | `check()` 抛异常 | `check()` 抛异常 |
| JNI read 错误 | `break` 退出循环 | `break` 退出循环 | 视情况 `break` 或 `continue` | `continue` 并计数 |
| JNI write 错误 | 直接 complete | 直接返回 | completeExceptionally | completeExceptionally |

**问题**：调用方无法通过统一方式判断「这通道是不是挂了」。

### 1.5 Native 层没有统一抽象

`NativeSerial` / `NativeCan` / `NativeUsbSerial` / `NativeUsbHid` 四个对象各自为政：
- 方法签名不统一（`NativeSerial.read` 有 offset/length，`NativeUsbSerial.read` 没有）
- 返回值语义不统一（有的 -1 是错误，有的 -1 是 timeout）
- 无法做 mock 测试或统一日志拦截

### 1.6 配置 → 实现的映射写死

`SikComm.open()` 里的 `when` 硬编码。新增通道类型必须改这里，无法运行时扩展。

---

## 二、优化方案（分阶段落地）

### 阶段 1：提取公共组件（低风险、高收益）

目标：把重复代码抽出来，不改任何对外接口。

#### 2.1.1 抽象 `Transport` — 纯底层读写

把四个 Native 对象的能力抽象成一个内部接口：

```kotlin
// internal，不对外暴露
internal interface Transport {
    fun open(config: CommConfig): Long          // >0: handle; <=0: fail
    fun close(handle: Long)
    fun read(handle: Long, buffer: ByteArray, timeoutMs: Int): Int   // >0: bytes; 0: timeout; <0: error
    fun write(handle: Long, data: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int
}
```

然后给四种通道各写一个 `Transport` 实现：
- `JniSerialTransport`（包装 NativeSerial）
- `JniCanTransport`（包装 NativeCan，但 write 需要 frameId/flags —— 这里可以用适配器模式）
- `FelUsbSerialTransport`（包装 NativeUsbSerial）
- `AndroidUsbHidTransport`（包装 NativeUsbHid）

**收益**：
- 通道实现里不再直接调 JNI，可以换实现、加拦截日志、做 mock 测试。
- 读/写的签名统一了，错误语义可以在这里做一层翻译（把各家的 -1 翻译成统一的 `TransportResult`）。

#### 2.1.2 提取 `HalfDuplexIoLooper` — 读优先循环模板

Serial / USB-Serial / USB-HID 都是「读优先单循环」。提取一个可复用的类：

```kotlin
// internal
internal class HalfDuplexIoLooper(
    private val transport: Transport,
    private val readTimeoutMs: Int,
    private val writeTimeoutMs: Int,
    private val onReadError: (Int) -> Boolean = { false }  // 返回 true 表示继续循环
) {
    fun start(scope: CoroutineScope, handle: Long, receiver: CommReceiver?): Job { ... }
    fun send(bytes: ByteArray, timeoutMs: Int?): Int { ... }  // 投递到 writeQueue
    fun shutdown() { ... }
}
```

**收益**：SerialChannelImpl、UsbSerialChannelImpl、UsbHidChannelImpl 的 IO 循环从 ~80 行缩到 3 行。读错误处理策略可以通过 lambda 注入。

#### 2.1.3 提取 `UsbPermissionBroker` — 统一 USB 权限

UsbSerialChannelImpl 和 UsbHidChannelImpl 各有一份几乎一样的权限代码。提取成：

```kotlin
// internal
internal class UsbPermissionBroker(
    context: Context,
    actionSuffix: String,
    onGranted: () -> Unit
) {
    fun request(device: UsbDevice): Boolean  // 返回 true 表示已有权限/已请求；false 表示无设备
    fun dispose()
}
```

**收益**：UsbSerial 和 UsbHid 的权限相关代码从 ~50 行缩到 ~10 行。

---

### 阶段 2：管道化接收处理（中风险、中收益）

#### 2.2.1 `ReceivePipeline` — 让 QrAssembler 可插拔

当前 `QrAssembler` 焊死在 `UsbSerialChannelImpl` 里。把它改成通用的 pipeline stage：

```kotlin
// internal
internal fun interface PipelineStage {
    fun process(data: ByteArray, offset: Int, length: Int, downstream: CommReceiver)
}

internal class ReceivePipeline(
    private val stages: List<PipelineStage>,
    private val finalReceiver: CommReceiver?
) : CommReceiver {
    override fun onBytesReceived(data: ByteArray, offset: Int, length: Int) {
        // 按顺序过 stages，最后交给 finalReceiver
    }
}
```

`QrAssembleStage` 作为一个实现。`UsbSerialChannelImpl` 里只要：

```kotlin
private val pipeline = config.qrAssemblePolicy?.let {
    ReceivePipeline(listOf(QrAssembleStage(it)), userReceiver)
} ?: userReceiver
```

**收益**：
- 拼包逻辑可以从 UsbSerialChannelImpl 里彻底移出，其他通道也能复用。
- 未来新增「解密 stage」「粘包 stage」不需要改通道实现。

---

### 阶段 3：统一错误模型与状态机（中风险、高收益）

#### 2.3.1 内部统一 `ChannelState`

四个实现各自用 `@Volatile var handle: Long` 管理状态，容易出错。建议内部统一：

```kotlin
// internal
internal sealed class ChannelState {
    data object Closed : ChannelState()
    data object Opening : ChannelState()
    data class Open(val handle: Long) : ChannelState()
    data class Failed(val cause: Throwable) : ChannelState()
}
```

配合 `AtomicReference` 做 CAS 状态转换。`open()` / `close()` / `send()` 都基于状态机判断，不再散落着各种 `if (isOpen()) return`。

#### 2.3.2 `send()` 内部统一异常策略

当前调用方无法区分「写成功」「写超时」「通道已关闭」「JNI 错误」。

在不改 `send` 签名的前提下，内部可以统一抛自定义异常（调用方如果只看返回值，行为不变；如果想精细化处理，可以 catch）：

```kotlin
// 保持返回 Int，但内部统一异常类型
sealed class CommException(message: String) : RuntimeException(message) {
    class NotOpen(id: String) : CommException("Channel $id is not open")
    class WriteTimeout(id: String) : CommException("Write timeout on $id")
    class TransportError(id: String, val code: Int) : CommException("Transport error $code on $id")
}
```

**收益**：调试时堆栈更清晰；业务层可以选择性 catch 做重试。

---

### 阶段 4：解耦配置映射（低风险、长期收益）

#### 2.4.1 `ChannelFactory` 注册表

把 `SikComm.open()` 的硬编码 `when` 改成注册表：

```kotlin
// internal
internal fun interface ChannelFactory {
    fun create(config: CommConfig): CommChannel
}

object SikComm {
    private val factories = mutableMapOf<KClass<*>, ChannelFactory>()

    init {
        register(SerialConfig::class) { SerialChannelImpl(it as SerialConfig) }
        register(CanConfig::class) { CanChannelImpl(it as CanConfig) }
        register(UsbSerialConfig::class) { UsbSerialChannelImpl(it as UsbSerialConfig) }
        register(UsbHidConfig::class) { UsbHidChannelImpl(it as UsbHidConfig) }
    }

    @JvmStatic
    fun open(config: CommConfig): CommChannel {
        val factory = factories[config::class]
            ?: error("No channel factory registered for ${config::class.simpleName}")
        return factory.create(config)
    }

    // 未来支持运行时扩展（比如插件化）
    fun register(configClass: KClass<*>, factory: ChannelFactory) { ... }
}
```

**收益**：
- 新增通道类型不需要改 `SikComm.kt` 主干。
- 单元测试可以注入 MockFactory。

---

## 三、重构后预期结构

```
internal/
  transport/
    Transport.kt              # 统一底层接口
    JniSerialTransport.kt
    JniCanTransport.kt
    FelUsbSerialTransport.kt
    AndroidUsbHidTransport.kt
  ioloop/
    IoLooper.kt               # 抽象
    HalfDuplexReadFirstLooper.kt
    FullDuplexLooper.kt       # CAN 用
  usb/
    UsbPermissionBroker.kt    # USB 权限统一处理
  pipeline/
    PipelineStage.kt
    ReceivePipeline.kt
    QrAssembleStage.kt        # 从 UsbSerialChannelImpl 里搬出来
  state/
    ChannelState.kt           # 统一状态机
  factory/
    ChannelFactory.kt
    ConfigToChannelRegistry.kt
  channel/
    BaseCommChannel.kt        # 可选：抽取公共模板
    SerialChannelImpl.kt      # 预计从 231 行 → ~60 行
    CanChannelImpl.kt         # 预计从 156 行 → ~50 行
    UsbSerialChannelImpl.kt   # 预计从 326 行 → ~80 行
    UsbHidChannelImpl.kt      # 预计从 208 行 → ~60 行
```

---

## 四、落地建议

| 阶段 | 内容 | 预估改动文件数 | 风险 | 建议时机 |
|------|------|-------------|------|---------|
| 1 | 提取 `Transport` + `HalfDuplexIoLooper` + `UsbPermissionBroker` | ~8 个新文件 + 4 个重构 | 低（纯内部移动） | **现在就可以做** |
| 2 | `ReceivePipeline` + `QrAssembleStage` 迁移 | ~3 个新文件 + 1 个重构 | 低 | 阶段 1 完成后 |
| 3 | `ChannelState` 状态机 + 统一异常 | ~2 个新文件 + 4 个重构 | 中（需回归测试） | 有充分测试后 |
| 4 | `ChannelFactory` 注册表 | ~2 个新文件 + 1 个重构 | 低 | 随时 |

**最关键的一句话**：

> 现在的「强行统一」是接口层面的统一，实现层面还是四套各自为政的代码。
> 优化的核心不是继续捏合它们，而是**把公共部分真地抽象成可复用组件**，让差异部分通过组合（组合 Transport + IoLooper + Pipeline + PermissionBroker）来表达，而不是通过复制粘贴 + 注释区分来表达。
