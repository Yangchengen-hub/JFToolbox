package com.jifeng.toolbox.ui.flash

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceDetector
import com.jifeng.toolbox.edl.EdlRescuer
import com.jifeng.toolbox.edl.EdlTransport
import com.jifeng.toolbox.edl.FirehoseProtocol
import com.jifeng.toolbox.edl.RawprogramParser
import com.jifeng.toolbox.fastboot.FastbootClient
import com.jifeng.toolbox.fastboot.FastbootFlasher
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FlashComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlashScreen() }
    }
}

@Composable
private fun FlashScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Fastboot", "9008 救砖", "分区表编辑")
    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("刷机中心", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t, style = MaterialTheme.typography.labelLarge) })
                }
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> FastbootTab()
                1 -> EdlTab()
                2 -> PartitionEditorTab()
            }
        }
    }
}

@Composable
private fun FastbootTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedPath by remember { mutableStateOf<String?>(null) }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_flash_${System.currentTimeMillis()}.zip")
            ctx.contentResolver.openInputStream(it)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            pickedPath = tmp.absolutePath
            logs.add("已选择: ${tmp.name}")
        }
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("前置条件: 被控端须进 bootloader (fastboot mode)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("当前 ADB 连接: ${if (AdbManager.isConnected) "已连接" else "未连接"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    androidx.compose.material3.Icon(Icons.Default.FolderOpen, contentDescription = null,
                        modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp)); Text(" 选择 ZIP/IMG")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AdbManager.listDevices().firstOrNull()?.let {
                                DeviceDetector.reboot(it, "bootloader")
                            }
                        }
                        logs.add("指令已发送: 重启到 bootloader")
                    }
                }) { Text("重启到 fastboot") }
            }
            pickedPath?.let {
                Text("文件: ${File(it).name}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            logs.clear(); progress = 0f; progressLabel = "校验中..."
                            val ok = withContext(Dispatchers.IO) { FastbootFlasher.validate(it) }
                            logs.add(if (ok) "✅ 校验通过: 合法 fastboot 卡刷包" else "❌ 不是合法 fastboot 卡刷包")
                            progress = null
                        }
                    }) { Text("校验") }
                    Button(onClick = {
                        scope.launch {
                            logs.clear(); progress = 0f
                            logs.add("⚠ 请确认设备已在 fastboot 模式")
                            // 真实刷写需要 FastbootClient 打开 USB, 这里走 Activity 隔离
                            logs.add("(Phase 6 将集成 USB 设备选择器, 当前演示日志)")
                        }
                    }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary)) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null,
                            modifier = Modifier.height(18.dp))
                        Text(" 开始刷写")
                    }
                }
                val parts = remember(it) {
                    if (it.endsWith(".zip")) FastbootFlasher.listPartitions(it) else emptyList()
                }
                if (parts.isNotEmpty()) {
                    Text("包含 ${parts.size} 个分区镜像:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    parts.take(8).forEach { p ->
                        Text("  • ${p.name}.img  (${p.size / 1024} KB)" +
                            if (p.name in FastbootFlasher.PROTECTED) "  ⚠受保护" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (p.name in FastbootFlasher.PROTECTED)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (parts.size > 8) Text("... 还有 ${parts.size - 8} 个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EdlTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedPath by remember { mutableStateOf<String?>(null) }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_edl_${System.currentTimeMillis()}.zip")
            ctx.contentResolver.openInputStream(it)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            pickedPath = tmp.absolutePath
            logs.add("已选择救砖包: ${tmp.name}")
        }
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("9008 EDL 救砖 (高通)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text("前置: 设备需进 9008 模式 (adb reboot edl 或按键组合)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("黑砖检测: 通过 getstorageinfo 查分区数, =0 则判定中格机文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("application/zip")) }) {
                    Text("选择救砖包")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        logs.clear(); logs.add("检测黑砖状态...")
                        // 真实检测需 EdlTransport 打开 9008 设备
                        logs.add("(需 OTG 连接 9008 设备, Phase 6 集成设备选择器)")
                    }
                }) { Text("检测黑砖") }
            }
            pickedPath?.let {
                Text("救砖包: ${File(it).name}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = {
                    scope.launch {
                        logs.clear(); progress = 0f; progressLabel = "校验中..."
                        val pack = withContext(Dispatchers.IO) {
                            EdlRescuer(FirehoseProtocol(EdlTransport()), RawprogramParser()).validatePack(it)
                        }
                        if (pack == null || !pack.isValid) {
                            logs.add("❌ 不是合法救砖包 (缺 prog_firehose.elf 或 rawprogram0.xml)")
                        } else {
                            logs.add("✅ 合法救砖包")
                            logs.add("  programmer: ${pack.programmer}")
                            logs.add("  芯片平台: ${pack.chipset}")
                            logs.add("  rawprogram: ${pack.rawprograms.size} 个")
                            logs.add("  镜像: ${pack.images.size} 个")
                            logs.add("(执行救砖需 OTG 连接 9008 设备)")
                        }
                        progress = null
                    }
                }) { Text("校验救砖包") }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PartitionEditorTab() {
    val scope = rememberCoroutineScope()
    val parts = remember { mutableStateListOf<com.jifeng.toolbox.core.Partition>() }
    var loading by remember { mutableStateOf(false) }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("分区表编辑器", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(0.dp).fillMaxWidth(0.3f))
                OutlinedButton(onClick = {
                    scope.launch {
                        loading = true; parts.clear()
                        val list = withContext(Dispatchers.IO) {
                            AdbManager.listDevices().firstOrNull()?.let {
                                DeviceDetector.probeAdbDevice(it).partitions
                            } ?: emptyList()
                        }
                        parts.addAll(list); loading = false
                    }
                }) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null,
                        modifier = Modifier.height(18.dp))
                    Text(" 读取分区表")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (loading) Text("读取中...", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (parts.isEmpty()) Text("点击上方按钮读取被控端分区表\n黑砖检测: 分区数为 0 → 中了格机文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("共 ${parts.size} 个分区", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                parts.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium,
                            color = if (p.isProtected) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (p.isProtected) FontWeight.Bold else FontWeight.Normal)
                        Text("${p.size / 1024 / 1024} MB" +
                            if (p.isProtected) "  ⚠" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLauncherForActivityResult(
    contract: ActivityResultContracts.OpenDocument,
    callback: (Uri?) -> Unit
) = androidx.activity.compose.rememberLauncherForActivityResult(contract, callback)
