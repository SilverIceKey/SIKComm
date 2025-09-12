package com.sik.comm_sample.ble

/**
 * 指令，均为未加密或解密后的
 */
object BleConst {

    const val BLE_LOCAL_NAME = "keyLock"

    const val MAX_KEY_STAND_BY: Int = 2
    const val MAX_KEY_CONNECT_COUNT: Int = 2

    const val MTU = 500
    const val SCAN_TIMEOUT = 20_000L

    const val SERVICE_UUID = "0000FEE7-0000-1000-8000-00805F9B34FB"
    const val INDICATE_UUID = "0000FED1-0000-1000-8000-00805F9B34FB"
    const val WRITE_UUID = "0000FED2-0000-1000-8000-00805F9B34FB"

    val STATUS_WORK = byteArrayOf(0x01)     // 工作模式
    val STATUS_READY = byteArrayOf(0x02)    // 待机模式

    /**
     * byteArrayOf不可变，可以使用 mutableListOf 来创建一个可变的列表，然后使用 toByteArray 方法
     * byteArray也有toList()方法
     */
    // 获取令牌，需增加4字节的时间戳，总长8个字节长度
    val REQ_GET_TOKEN = byteArrayOf(0x01, 0x01, 0x05, 0x00)

    // 获取令牌响应，最后4个是token，总长15个字节长度
    val RSP_GET_TOKEN = byteArrayOf(0x01, 0x02, 0x04)

    // 设备工作模式切换
    val REQ_SWITCH_MODE = byteArrayOf(0x02, 0x01, 0x02, 0x01)

    // 工作模式切换响应
    val RSP_SWITCH_MODE = byteArrayOf(0x02, 0x02, 0x03, 0x01)

    // 工作票下发
    val REQ_SEND_WORK_TICKET = byteArrayOf(0x02, 0x01)

    // 工作票下发响应
    val RSP_SEND_WORK_TICKET = byteArrayOf(0x02, 0x02, 0x06, 0x02)

    // 获取设备当前状态
    val REQ_CURRENT_STATUS = byteArrayOf(0x03, 0x01, 0x01, 0x01)

    // 获取当前设备响应
    val RSP_CURRENT_STATUS = byteArrayOf(0x03, 0x02, 0x02, 0x01)

    // 获取设备工作票完成情况
    val REQ_WORK_TICKET_RESULT = byteArrayOf(0x03, 0x01, 0x01, 0x02)

    // 获取设备工作票完成情况响应
    val RSP_WORK_TICKET_RESULT = byteArrayOf(0x03, 0x02)

    // 获取设备工作票完成情况分包
    val REQ_WORK_TICKET_RESULT_PART = byteArrayOf(0x03, 0x01, 0x06, 0x02)

    // 获取钥匙电量
    val REQ_POWER_STATUS = byteArrayOf(0x03, 0x01, 0x01, 0x03)

    // 获取钥匙电量响应
    val RSP_POWER_STATUS = byteArrayOf(0x03, 0x02, 0x03, 0x03)

    // 传输文件
    val REQ_TRANSFER_FILE = byteArrayOf(0x06, 0x01)

    // 传输文件完成响应
    val RSP_TRANSFER_FILE = byteArrayOf(0x06, 0x02)

    // 获取固件版本号
    val REQ_GET_VERSION = byteArrayOf(0xEE.toByte(), 0x01, 0x02, 0x01, 0x01)

    // 获取固件版本号响应
    val RSP_GET_VERSION = byteArrayOf(0xEE.toByte(), 0x02, 0x03, 0x01)

    /**
     * 蓝牙断开指令
     */
    val REQ_DISCONNECT_BLE = byteArrayOf(0x02.toByte(), 0x01, 0x01, 0xEA.toByte())

    /**
     * 蓝牙断开回复
     */
    val RES_DISCONNECT_BLE = byteArrayOf(0x02.toByte(), 0x02, 0x02, 0xEA.toByte())

    /**
     * 关机或重启指令
     */
    val REQ_SHUTDOWN_OR_REBOOT = byteArrayOf(0x02.toByte(), 0x01, 0x02, 0xEE.toByte())

    /**
     * 关机回复
     */
    val RES_SHUTDOWN_OR_REBOOT = byteArrayOf(0x02.toByte(), 0x02, 0x03, 0xEE.toByte())
}