# SIKComm 泄露点排查与修复报告

**日期**: 2026-05-21  
**范围**: `sikcomm` 库核心通道、`app` 示例模块  
**结论**: 共发现 7 处泄露/资源不一致问题，已全部修复并通过单元测试。

---

## 1. 内存泄漏：Activity 通过 Receiver lambda 被单例长期引用（高危）

### 现象
- `UsbScanHelper` 是 `object` 单例，持有 `commChannel` 与 `pendingReceiver`。
- `MainActivity` 在 `setReceiver { ... }` 中通过 lambda 捕获了 `MainActivity` 自身（`uiLog` 为实例方法）。
- `MainActivity.onDestroy()` 中 `UsbScanHelper.release()` 被**注释掉**，Activity 销毁后引用链仍存活。

### 引用链
```
UsbScanHelper(object) → pendingReceiver → lambda → MainActivity
```

### 修复
- `MainActivity.onDestroy()`: 取消注释并调用 `UsbScanHelper.release()`。
- `UsbScanHelper.release()`: 额外清空 `pendingReceiver = null`，切断 lambda 引用。

---

## 2. 内存泄漏：`ReceivePipeline` 懒加载后固定持有旧 `CommReceiver`（高危）

### 现象
- `UsbSerialChannelImpl` 中 `pipeline` 使用 `val by lazy`：
  ```kotlin
  private val pipeline: CommReceiver? by lazy {
      val policy = config.qrAssemblePolicy ?: return@lazy currentReceiver
      ReceivePipeline(
          stages = listOf(QrAssembleStage(policy, scope)),
          finalReceiver = currentReceiver   // ← 初始化时快照，后续不可变
      )
  }
  ```
- 一旦 `pipeline` 被初始化，`finalReceiver` 即固定为当时快照的 `currentReceiver`。
- 后续 `setReceiver(null)` 或更换新 Receiver 时，`pipeline` 仍持有旧的 Activity 引用。

### 修复
- 修改 `ReceivePipeline`，将 `finalReceiver` 改为可变引用，并暴露 `setFinalReceiver()` 方法。
- `UsbSerialChannelImpl` 重写 `setReceiver()`，在更新 `currentReceiver` 后同步更新 `pipeline?.finalReceiver`。

---

## 3. 协程作用域泄漏：`BaseCommChannel.open()` 失败时未 cancel scope（中危）

### 现象
- `BaseCommChannel` 的 `scope` 仅在 `close()` 中被 cancel。
- 若 `doOpen()` 抛出异常，`open()` 会设置 `ChannelState.Failed` 并 rethrow，但**未 cancel scope**。
- 此时 channel 处于 Failed 状态，scope 中的 `SupervisorJob` 长期存活，直到 channel 被 GC。

### 修复
- 在 `open()` 的 catch 块中增加 `scope.cancel()`，确保异常路径下作用域被释放。

---

## 4. 功能缺陷（衍生泄漏）：`close()` 后 scope 被 cancel，导致再次 `open()` 时 IO 循环无法启动（中危）

### 现象
- `scope` 在构造函数中一次性创建，在 `close()` 中永久 cancel。
- 若用户按"close → 再 open"顺序使用（文档未禁止），第二次 `open()` 成功后将状态置为 Open，但 `onOpened()` 中通过 `scope.launch` 启动的协程因 scope 已 cancel 而**立即失效**。
- 状态机显示 Open，实际无读循环，数据被静默丢弃，且 send() 会因 `requireHandle()` 通过但底层无协程工作而表现异常。

### 修复
- 将 `scope` 从构造函数中的 `val` 改为可在 `open()` 时重新初始化的属性。
- 在 `open()` 进入 Opening 状态时，若检测到 scope 已 cancel，重新创建 `SupervisorJob() + Dispatchers.IO`。
- `close()` 仍负责 cancel scope，但下次 open 会自动恢复。

---

## 5. Channel 资源堆积：`HalfDuplexIoLooper.writeQueue` 未关闭（中危）

### 现象
- `writeQueue` 是 `Channel<WriteJob>(Channel.UNLIMITED)`。
- `shutdown()` 仅通过 `tryReceive()` 清空当前队列，**未关闭 Channel**。
- 若 IO 协程退出后外部仍调用 `send()`，数据会被投递到无消费者的 Channel 中，无限堆积。

### 修复
- `shutdown()` 中先调用 `writeQueue.close()`，使后续 `send()` 抛出 `ClosedSendChannelException`，阻止数据堆积。
- 同时保留 `tryReceive()` 清空逻辑，给已入队的 WriteJob 返回异常。

---

## 6. Native 资源不一致：`JniCanTransport` bringUp 后 open 失败未 bringDown（低危）

### 现象
- `JniCanTransport.open()`：
  ```kotlin
  if (bitrate != null) {
      NativeCan.bringUp(c.ifName, bitrate, c.fdMode)   // 可能成功
  }
  val fd = NativeCan.open(c.ifName)                    // 可能失败
  ```
- 若 `bringUp` 成功、`open` 失败返回 `fd <= 0`，函数直接返回错误，**未调用 `bringDown`**。
- 导致 CAN 接口处于 up 状态但没有 socket 关联，影响后续操作。

### 修复
- 在 `open()` 的失败路径中，若 `bitrate != null` 且之前已 bringUp，补充调用 `NativeCan.bringDown(c.ifName)`。

---

## 7. BroadcastReceiver 安全网：`UsbPermissionBroker` 被丢弃时可能泄漏（低危）

### 现象
- `UsbPermissionBroker` 在 `request()` 中注册 BroadcastReceiver，在 `dispose()` 中注销。
- 若 `UsbSerialChannelImpl` / `UsbHidChannelImpl` 被直接丢弃（未调用 `close()`），`dispose()` 永远不会执行，receiver 持续注册在系统中。
- 虽然正常链路下 `doClose()` 会调用 `dispose()`，但防御性编程不足。

### 修复
- 在 `BroadcastReceiver.onReceive()` 中增加 guard：若检测到 `disposed.get() == true`，主动执行 `unregisterReceiver(this)` 自我清理。
- 这样即使发生泄漏，至少在下一次收到同 action 广播时（或系统触发时）能自我注销。

---

## 验证结果

### 已执行
- `./gradlew :sikcomm:test` — 全部单元测试通过。
- 手动 review 修改后的引用链，确认无 Activity → Channel 强引用残留。

### 未执行
- `./gradlew :sikcomm:connectedAndroidTest` — 需要物理设备/USB 外设，当前环境不具备。
- 内存泄漏专业工具（LeakCanary）长时间压测 — 建议接入到 `app` 模块进行回归验证。

### 风险
- 修复 4（scope 重新创建）改变了 `BaseCommChannel` 的生命周期语义，但行为更符合接口"幂等 open"的直觉。如果外部代码依赖"close 后 scope 永不可用"的隐含假设，可能会受影响（概率极低，且属于错误用法）。

---

## 下一步
1. 在 `app` 模块接入 LeakCanary，旋转屏幕 + 反复 init/release 做压测。
2. 补充 `BaseCommChannel` 的"close 后重新 open"集成测试。
3. 将 `UsbScanHelper.release()` 的调用模式写入使用指南，强调生命周期绑定。
