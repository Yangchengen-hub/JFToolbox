package com.jifeng.toolbox.ui.wireless

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.databinding.ActivityWirelessDebugBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 无线调试 MVP：开启 TCP/IP 模式后连接。
 */
class WirelessDebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWirelessDebugBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityWirelessDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableTcp.setOnClickListener {
            val serial = AdbManager.listDevices().firstOrNull() ?: return@setOnClickListener toast("无 USB 设备")
            CoroutineScope(Dispatchers.IO).launch {
                AdbManager.shell(serial, "setprop service.adb.tcp.port 5555")
                AdbManager.shell(serial, "stop adbd; start adbd")
                runOnUiThread { toast("已开启 TCP/IP 5555，请通过 IP 连接") }
            }
        }

        binding.btnConnect.setOnClickListener {
            val ip = binding.edtIp.text.toString().trim()
            if (ip.isBlank()) return@setOnClickListener toast("输入 IP")
            CoroutineScope(Dispatchers.IO).launch {
                val p = Runtime.getRuntime().exec("adb connect $ip:5555")
                p.waitFor()
                val out = p.inputStream.bufferedReader().readText()
                runOnUiThread { binding.txtStatus.append("$out\n") }
            }
        }

        binding.btnFreezeList.setOnClickListener {
            // 跳转到冻结页面
            startActivity(android.content.Intent(this, com.jifeng.toolbox.ui.freeze.FreezeActivity::class.java))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
