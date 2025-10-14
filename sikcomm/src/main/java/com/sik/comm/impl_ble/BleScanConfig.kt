package com.sik.comm.impl_ble


/** 扫描与目标选择配置（无业务） */
enum class ScanStrategy { KNOWN_MAC_ONLY, NEW_DEVICE }


data class BleScanConfig(
    val strategy: ScanStrategy = ScanStrategy.KNOWN_MAC_ONLY,
    val totalBudgetMs: Long = 12_000,
    // 仅用于 NEW_DEVICE：环境快照一轮，之后进入连续模式
    val ambientMs: Long = 800,

    // 连续扫描参数（避免频繁启停）
    val continuous: Boolean = true,     // ✅ 连续扫描而非窗口重启
    val batchReportMs: Long = 500,      // ✅ setReportDelay 批量上报，减压回调

    // 判定门槛（NEW_DEVICE）
    val minRssi: Int = -85,
    val minSightings: Int = 2,
    val notBeforeMs: Long = 500,

    // 仅当 continuous=false 时才用到
    val windowMs: Long = 800,           // 单窗口长度
    val minRestartIntervalMs: Long = 3000 // 窗口之间的最小间隔，避免频繁启停
)
