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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.jifeng.toolbox.notify.FlashNotificationManager
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import com.jifeng.toolbox.usb.UsbDeviceManager
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
    val tabs = listOf("Fastboot", "单镜像刷入", "9008 救砖", "分区表编辑")
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
                1 -> SingleImageTab()
                2 -> EdlTab()
                3 -> PartitionEditorTab()
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
                        val zipPath = it
                        scope.launch {
                            logs.clear()
                            progress = 0f
                            progressLabel = "查找 fastboot 设备..."

                            // 1. 查找 fastboot 设备 (设备须已进 bootloader)
                            val usbMgr = UsbDeviceManager.get(ctx)
                            val device = withContext(Dispatchers.IO) { usbMgr.findFastbootDevice() }
                            if (device == null) {
                                logs.add("❌ 未检测到 fastboot 设备")
                                logs.add("请将设备重启到 bootloader/fastboot 模式 (adb reboot bootloader 或按键组合)")
                                if (AdbManager.isConnected) {
                                    logs.add("提示: 当前 ADB 已连接, 可点击「重启到 fastboot」")
                                } else {
                                    logs.add("提示: 当前无 ADB 连接, 请先用 OTG 连接并授权被控端, 再重启到 bootloader")
                                }
                                FlashNotificationManager.flashFailed(ctx, "设备",
                                    "未检测到 fastboot 设备, 请先进 bootloader")
                                progress = null
                                return@launch
                            }

                            // 2. 真实刷写: FastbootFlasher.flash() 内部完成
                            //    校验 → 权限 → 打开设备 → 逐分区 erase/download/flash → 通知栏 → reboot
                            progressLabel = "刷写中..."
                            val ok = withContext(Dispatchers.IO) {
                                FastbootFlasher.flash(
                                    ctx = ctx,
                                    device = device,
                                    zipPath = zipPath,
                                    onProgress = { name, cur, total ->
                                        // 切回主线程更新 Compose 进度状态
                                        scope.launch {
                                            progress = (cur.toFloat() / total).coerceIn(0f, 1f)
                                            progressLabel = "$name ($cur/$total)"
                                        }
                                    },
                                    onLog = { msg ->
                                        // 切回主线程追加日志
                                        scope.launch { logs.add(msg) }
                                    }
                                )
                            }
                            // 收尾状态由 onLog/onProgress 已实时更新; 仅清进度条
                            progress = null
                            progressLabel = if (ok) "刷写完成" else "刷写失败"
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
private fun SingleImageTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedPath by remember { mutableStateOf<String?>(null) }
    var partition by remember { mutableStateOf("boot") }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_img_${System.currentTimeMillis()}.img")
            ctx.contentResolver.openInputStream(it)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            pickedPath = tmp.absolutePath
            logs.add("已选择镜像: ${tmp.name} (${tmp.length() / 1024} KB)")
            // 自动从文件名推断分区名
            val infer = tmp.nameWithoutExtension.lowercase()
            if (infer in FastbootFlasher.PROTECTED + setOf(
                    "boot", "recovery", "system", "vendor", "product",
                    "vbmeta", "dtbo", "super", "userdata", "cache"
                )) {
                partition = infer
                logs.add("已自动识别分区: $partition")
            }
        }
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("单镜像刷入 (fastboot flash)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text("前置: 设备须进 bootloader (fastboot mode)。\n选 .img → 选分区 → 刷入。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("当前 ADB 连接: ${if (AdbManager.isConnected) "已连接" else "未连接"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null,
                        modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp)); Text(" 选择 .img")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AdbManager.listDevices().firstOrNull()?.let { dev ->
                                DeviceDetector.reboot(dev, "bootloader")
                            }
                        }
                        logs.add("指令已发送: 重启到 bootloader")
                    }
                }) { Text("重启到 fastboot") }
            }

            pickedPath?.let { path ->
                val f = File(path)
                Text("文件: ${f.name} (${f.length() / 1024} KB)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("分区:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(value = partition, onValueChange = { partition = it.trim().lowercase() },
                        singleLine = true, modifier = Modifier.fillMaxWidth(0.6f),
                        label = { Text("分区名") })
                }

                // 常用分区快捷按钮
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("boot", "recovery", "vbmeta", "dtbo", "system").forEach { p ->
                        FilterChip(
                            selected = partition == p,
                            onClick = { partition = p },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (partition in FastbootFlasher.PROTECTED) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning,
                            contentDescription = null, tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.height(16.dp))
                        Spacer(Modifier.height(0.dp))
                        Text(" ⚠ 受保护分区, 误刷可能变砖, 请确认来源!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            logs.clear(); progress = 0f; progressLabel = "查找 fastboot 设备..."
                            val usbMgr = UsbDeviceManager.get(ctx)
                            val device = withContext(Dispatchers.IO) { usbMgr.findFastbootDevice() }
                            if (device == null) {
                                logs.add("❌ 未检测到 fastboot 设备")
                                logs.add("请将设备重启到 bootloader/fastboot 模式")
                                if (AdbManager.isConnected) logs.add("提示: 当前 ADB 已连接, 可点击「重启到 fastboot」")
                                progress = null
                                return@launch
                            }
                            if (!usbMgr.hasPermission(device)) {
                                usbMgr.requestFastbootPermission(device)
                                logs.add("请在系统弹窗中授权 USB 权限后再次点击「刷入」")
                                progress = null
                                return@launch
                            }
                            val rawConn = usbMgr.openDevice(device)
                            if (rawConn == null) {
                                logs.add("❌ 打开 USB 设备失败"); progress = null; return@launch
                            }
                            val client = FastbootClient()
                            if (!client.open(device, rawConn)) {
                                logs.add("❌ Fastboot 接口打开失败"); client.close()
                                progress = null; return@launch
                            }
                            logs.add("✅ Fastboot 设备已连接, max-download=${client.getvar("max-download-size") ?: "?"}")
                            progressLabel = "刷写 $partition ..."
                            val ok = withContext(Dispatchers.IO) {
                                client.flashImage(partition, path,
                                    onInfo = { msg -> scope.launch { logs.add(msg) } },
                                    onProgress = { pct -> scope.launch { progress = pct / 100f } })
                            }
                            client.close()
                            progress = null
                            progressLabel = if (ok) "✅ $partition 刷写完成" else "❌ $partition 刷写失败"
                            logs.add(progressLabel!!)
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null,
                            modifier = Modifier.height(18.dp))
                        Text(" 刷入 $partition")
                    }

                    OutlinedButton(onClick = {
                        scope.launch {
                            val usbMgr = UsbDeviceManager.get(ctx)
                            val device = withContext(Dispatchers.IO) { usbMgr.findFastbootDevice() }
                            if (device == null) { logs.add("❌ 未检测到 fastboot 设备"); return@launch }
                            if (!usbMgr.hasPermission(device)) { usbMgr.requestFastbootPermission(device); return@launch }
                            val rawConn = usbMgr.openDevice(device) ?: run {
                                logs.add("❌ 打开 USB 设备失败"); return@launch
                            }
                            val client = FastbootClient()
                            if (client.open(device, rawConn)) {
                                logs.add("当前槽位: ${client.getvar("current-slot") ?: "?"}")
                                logs.add("已刷写槽位: ${client.getvar("slot-count") ?: "?"}")
                                logs.add("max-download: ${client.getvar("max-download-size") ?: "?"}")
                                logs.add("产品: ${client.getvar("product") ?: "?"}")
                                logs.add("已解锁: ${client.getvar("unlocked") ?: "?"}")
                                client.reboot()
                                logs.add("✓ 设备已重启")
                            } else {
                                logs.add("❌ Fastboot 接口打开失败")
                            }
                            client.close()
                        }
                    }) { Text("查询 + 重启") }
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
            // 芯片引导 (firehose loader) 选择: 跳转独立 Activity, 自动探测 / 下载 / 选用
            Button(onClick = {
                ctx.startActivity(Intent(ctx, LoaderPickerComposeActivity::class.java))
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null, modifier = Modifier.height(18.dp)
                )
                Text(" 芯片引导选择 (firehose loader)")
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
    val ctx = LocalContext.current
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
            Spacer(Modifier.height(8.dp))
            // 跳转到独立的 rawprogram.xml 分区表编辑器
            Button(onClick = {
                ctx.startActivity(Intent(ctx, PartitionEditorComposeActivity::class.java))
            }, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Default.Storage, contentDescription = null,
                    modifier = Modifier.height(18.dp))
                Text(" 打开分区表编辑器 (rawprogram.xml)")
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
