package com.sik.comm_sample

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolManager
import com.sik.comm.core.protocol.ProtocolType
import com.sik.comm.impl_modbus.ModbusConfig
import com.sik.comm.impl_modbus.ModbusProtocol
import com.sik.comm.impl_modbus.easy.ModbusClient
import com.sik.sikcore.extension.setDebouncedClickListener
import com.sik.sikcore.thread.ThreadUtils


class MainActivity : AppCompatActivity() {
    private val modbusProtocol = ModbusProtocol()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.button).setDebouncedClickListener {
            ThreadUtils.runOnIO {
                initBle()
            }
        }
    }

    private suspend fun initBle() {
        // 1) 注册配置（原有方式）
        val cfg = ModbusConfig(
            deviceId = "PLC_A",
            portName = "/dev/ttyS3",  // 或 "tcp://192.168.1.100:502"
            baudRate = 115200,
            defaultUnitId = 1
        )
        val proto = ModbusProtocol().apply { registerConfig(cfg) }
        proto.connect(cfg.deviceId)

        // 2) 更“人话”的客户端
        val client = ModbusClient(
            protocol = proto,
            deviceId = cfg.deviceId,
            defaultUnitId = cfg.defaultUnitId ?: 1
        )

        // 3) 读保持寄存器 0x03
        val regs: ShortArray = client.readHoldingRegisters(addr = 0x0000, qty = 4)

        // 4) 写单寄存器 0x06
        client.writeSingleRegister(addr = 0x0001, value = 1234)
    }
}