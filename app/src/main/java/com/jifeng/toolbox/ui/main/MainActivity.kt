package com.jifeng.toolbox.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceDetector
import com.jifeng.toolbox.core.DeviceInfo
import com.jifeng.toolbox.databinding.ActivityMainBinding
import com.jifeng.toolbox.ui.about.AboutActivity
import com.jifeng.toolbox.ui.browser.BrowserActivity
import com.jifeng.toolbox.ui.downloader.DownloaderActivity
import com.jifeng.toolbox.ui.flash.FlashActivity
import com.jifeng.toolbox.ui.freeze.FreezeActivity
import com.jifeng.toolbox.ui.terminal.TerminalActivity
import com.jifeng.toolbox.ui.wireless.WirelessDebugActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PartitionAdapter
    private var currentDevice: DeviceInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        startAdbService()
        refreshDevice()
    }

    private fun setupRecyclerView() {
        adapter = PartitionAdapter(emptyList())
        binding.recyclerViewPartitions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPartitions.adapter = adapter
    }

    private fun setupListeners() {
        binding.cardFlash.setOnClickListener { startActivity(Intent(this, FlashActivity::class.java)) }
        binding.cardTerminal.setOnClickListener { startActivity(Intent(this, TerminalActivity::class.java)) }
        binding.cardDownloader.setOnClickListener { startActivity(Intent(this, DownloaderActivity::class.java)) }
        binding.cardBrowser.setOnClickListener { startActivity(Intent(this, BrowserActivity::class.java)) }
        binding.cardWireless.setOnClickListener { startActivity(Intent(this, WirelessDebugActivity::class.java)) }
        binding.cardFreeze.setOnClickListener { startActivity(Intent(this, FreezeActivity::class.java)) }
        binding.cardAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        binding.btnRefresh.setOnClickListener { refreshDevice() }
        binding.btnRebootSys.setOnClickListener { reboot("system") }
        binding.btnRebootRec.setOnClickListener { reboot("recovery") }
        binding.btnRebootBl.setOnClickListener { reboot("bootloader") }
        binding.btnReboot9008.setOnClickListener { reboot("9008") }
    }

    private fun startAdbService() {
        // MVP 阶段不强制前台服务，后续加
    }

    private fun refreshDevice() {
        binding.txtStatus.text = "🔍 正在探测设备..."
        CoroutineScope(Dispatchers.Main).launch {
            val serials = withContext(Dispatchers.IO) { AdbManager.instance.listDevices() }
            if (serials.isEmpty()) {
                binding.txtStatus.text = "⚠️ 未检测到设备\n请通过 OTG 线连接被控设备并授权 USB 调试"
                currentDevice = null
                adapter.update(emptyList())
                return@launch
            }
            val serial = serials.first()
            val info = withContext(Dispatchers.IO) { DeviceDetector.probeAdbDevice(serial) }
            currentDevice = info
            binding.txtStatus.text = "✅ ${info.displayName}\nAndroid ${info.androidVersion} · ${info.chipset}\n模式: ${info.connectionMode.label} · Root: ${info.hasRoot ?: '?'}"

            if (info.partitions.isNotEmpty()) {
                adapter.update(info.partitions)
                binding.recyclerViewPartitions.visibility = View.VISIBLE
            } else {
                binding.recyclerViewPartitions.visibility = View.GONE
            }
        }
    }

    private fun reboot(target: String) {
        val serial = currentDevice?.serial ?: return
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) { DeviceDetector.reboot(serial, target) }
            binding.txtStatus.text = "🔄 正在重启到 $target ..."
        }
    }
}
