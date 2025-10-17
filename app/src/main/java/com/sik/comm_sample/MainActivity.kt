package com.sik.comm_sample

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sik.comm.codec.PassThroughCodec
import com.sik.comm.core.model.CommMessage
import com.sik.comm.core.model.ProtocolConfig
import com.sik.comm.core.protocol.ProtocolManager
import com.sik.comm.core.protocol.ProtocolType
import com.sik.comm.impl_modbus.ModbusConfig
import com.sik.comm.impl_modbus.ModbusProtocol
import com.sik.comm.impl_modbus.easy.ModbusClient
import com.sik.sikcore.extension.setDebouncedClickListener
import com.sik.sikcore.thread.ThreadUtils
import kotlinx.coroutines.delay


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

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun initBle() {
        // 1) 注册配置（原有方式）
        val cfg = ModbusConfig(
            deviceId = "PLC_A",
            portName = "/dev/ttyS2",  // 或 "tcp://192.168.1.100:502"
            baudRate = 115200,
            defaultUnitId = null,
            codec = PassThroughCodec()
        )
        val proto = ModbusProtocol().apply { registerConfig(cfg) }
        proto.connect(cfg.deviceId)
        delay(200)
        val rsp = proto.send("PLC_A", CommMessage("PLC_A_READ",
            byteArrayOf(0xEF.toByte(),0xAA.toByte(),0x30.toByte(),0x00.toByte(),0x00.toByte(),0x30.toByte())))
        Log.i("RSP","${rsp.payload.toHexString()}")
    }
}