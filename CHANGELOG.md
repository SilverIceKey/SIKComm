# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.1.0] - 2025-05

### Added
- 新增公共异常体系 `CommException`：统一通道操作的错误类型。
  - `CommException.NotOpen` — 通道未打开时调用操作
  - `CommException.OpenFailed` — 打开通道失败
  - `CommException.WriteTimeout` — 写入超时
  - `CommException.TransportError` — 底层传输错误
- 新增 `OpenCallback` 回调接口：监听 `open()` 异步结果（尤其 USB 权限场景）。
  ```kotlin
  channel.setOpenCallback { success, error ->
      if (!success) Log.e("SIKComm", "打开失败: ${error?.message}")
  }
  ```
- 新增 `BaseCommChannel` 内部基类：统一状态机管理（`ChannelState`：Closed / Opening / Open / Failed）。
- 新增 `ReceivePipeline` + `QrAssembleStage`：接收处理管道化，拼包逻辑从 `UsbSerialChannelImpl` 中彻底解耦。
- 新增 `MockTransport` + 32 个单元测试：覆盖 Transport / IoLooper / Pipeline / Registry / QrAssemble / BaseCommChannel。

### Changed
- **行为变化**：`CommChannel.send()` 在通道未打开时，从抛出 `IllegalStateException` 改为抛出 `CommException.NotOpen`。
  - 如果业务层原先精确捕获 `IllegalStateException`，需要调整为捕获 `CommException` 或 `RuntimeException`。
- **行为变化**：`CommChannel.open()` 内部统一使用 `ChannelState` 状态机，Serial / CAN 失败时抛出 `CommException.OpenFailed`；USB 通道保持原有静默失败行为，但内部状态统一。
- 目录结构优化：四个 `ChannelImpl` 从 `com.sik.comm` 根包移至 `com.sik.comm.internal.channel`。

### Removed
- 移除未使用的 `CdcAcmDriver`、`UsbSerialDriver` 内部类。

## [2.0.6] - 2025-05

### Changed
- 统一 README 文档：补充四通道类型说明、项目结构、快速开始示例、JitPack 引入方式。

## [2.0.0] - 2025

### Added
- 全面重写 2.x 架构，与 1.x 不兼容。
- 新增 `SikComm` 统一入口：`SikComm.open(config)` 根据配置自动创建对应通道。
- 新增 `CommChannel` 统一接口：`open()` / `close()` / `isOpen()` / `send()` / `setReceiver()`。
- 新增 `CommConfig` sealed interface，支持四类通道配置：
  - `SerialConfig` — 原生串口（RS232 / RS485 / /dev/ttySx）
  - `CanConfig` — SocketCAN
  - `UsbSerialConfig` — USB 转串口（CDC / CH34x / CP210x / FTDI / PL2303）
  - `UsbHidConfig` — USB HID 设备（扫码枪 / 键盘类）
- 新增扫码枪拼包策略 `QrAssemblePolicy`：400 ms 窗口拼接 + 超时清空 + 可选剔除 `\r\n`。
- 新增 USB 设备匹配器 `UsbDeviceMatcher`：支持 `Any`、`VidPidWhitelist`、`QrScannerDemoWhitelist`。
- 新增 USB 驱动策略 `UsbDriverPolicy`：`Auto` 自动探测 / `Prefer` 强制指定驱动族。
- 新增 `ByteArrayExt` 工具扩展：`sliceFast()` / `toHex()`。
- 新增 `CdcAcmDriver` 内部实现（备用底层）。

### Changed
- 串口与 USB-Serial 通道采用「读优先」IO 循环：每轮先 read，无数据再处理写队列，避免半双工场景下收发抢占。
- SocketCAN 采用真全双工：读循环与写操作分离，互不影响。
- License 自 1.0.17 起切换为 Apache License 2.0。

### Removed
- 移除 1.x 中的 BLE、TCP、Mock 支持。
- 移除插件系统与自动注入体系。

## [1.x]

历史版本详见 Git 历史记录。1.0.16 及之前为 MIT License，自 1.0.17 起改为 Apache License 2.0。
