package com.jifeng.toolbox.ui.terminal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.databinding.ActivityTerminalBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 超级终端 MVP：内置多标签页 shell。MVP 阶段直接 exec adb shell，
 * 后续接入 libtermexec + 本地 pts。
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private var history: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 显示历史
        history = Logger.history()
        binding.txtOutput.text = history.joinToString("\n")

        binding.btnRun.setOnClickListener {
            val cmd = binding.edtCmd.text.toString().trim()
            if (cmd.isBlank()) return@setOnClickListener
            val serial = AdbManager.listDevices().firstOrNull() ?: ""
            binding.txtOutput.append("\n$ $cmd\n")
            CoroutineScope(Dispatchers.IO).launch {
                val out = AdbManager.shell(serial, cmd).orEmpty()
                runOnUiThread { binding.txtOutput.append("$out\n") }
            }
        }

        binding.btnClear.setOnClickListener {
            Logger.clear()
            binding.txtOutput.text = ""
        }
    }
}
