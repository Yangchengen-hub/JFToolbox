package com.jifeng.toolbox.ui.flash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceDetector
import com.jifeng.toolbox.edl.EdlRescuer
import com.jifeng.toolbox.edl.EdlTransport
import com.jifeng.toolbox.edl.FirehoseProtocol
import com.jifeng.toolbox.edl.FlashGuideDatabase
import com.jifeng.toolbox.edl.RawprogramParser
import com.jifeng.toolbox.fastboot.FastbootClient
import com.jifeng.toolbox.fastboot.FastbootFlasher
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import com.jifeng.toolbox.ui.theme.JFColors
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
    val tabs = listOf("Fastboot", "单镜像", "9008 救砖", "全品牌引导", "分区表")
    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("刷机中心", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
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
                3 -> CloudGuideTab()
                4 -> PartitionEditorTab()
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
            Text("前置: 被控端须进 bootloader (fastboot mode)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ADB: ${if (AdbManager.isConnected) "已连接" else "未连接"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text(" 选择 ZIP/IMG")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AdbManager.listDevices().firstOrNull()?.let { DeviceDetector.reboot(it, "bootloader") }
                        }
                        logs.add("已发送: 重启到 bootloader")
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
                            logs.add(if (ok) "✅ 校验通过" else "❌ 不是合法 fastboot 卡刷包")
                            progress = null
                        }
                    }) { Text("校验") }
                    Button(onClick = {
                        val zipPath = it
                        scope.launch {
                            logs.clear(); progress = 0f; progressLabel = "查找 fastboot 设备..."
                            val usbMgr = UsbDeviceManager.get(ctx)
                            val device = withContext(Dispatchers.IO) { usbMgr.findFastbootDevice() }
                            if (device == null) {
                                logs.add("❌ 未检测到 fastboot 设备"); progress = null
                                return@launch
                            }
                            progressLabel = "刷写中..."
                            val ok = withContext(Dispatchers.IO) {
                                FastbootFlasher.flash(ctx, device, zipPath,
                                    onProgress = { name, cur, total ->
                                        scope.launch {
                                            progress = (cur.toFloat() / total).coerceIn(0f, 1f)
                                            progressLabel = "$name ($cur/$total)"
                                        }
                                    },
                                    onLog = { msg -> scope.launch { logs.add(msg) } })
                            }
                            progress = null
                            progressLabel = if (ok) "刷写完成" else "刷写失败"
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.height(18.dp)); Text(" 开始刷写")
                    }
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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_img_${System.currentTimeMillis()}.img")
            ctx.contentResolver.openInputStream(it)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            pickedPath = tmp.absolutePath
            logs.add("已选择: ${tmp.name} (${tmp.length() / 1024} KB)")
            val infer = tmp.nameWithoutExtension.lowercase()
            if (infer in FastbootFlasher.PROTECTED + setOf("boot","recovery","system","vendor","product","vbmeta","dtbo","super","userdata","cache"))
                partition = infer
        }
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("单镜像刷入 (fastboot flash)", fontWeight = FontWeight.SemiBold)
            Text("前置: 设备须进 bootloader。选 .img → 选分区 → 刷入。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.height(18.dp)); Text(" 选择 .img")
                }
                OutlinedButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) {
                        AdbManager.listDevices().firstOrNull()?.let { DeviceDetector.reboot(it, "bootloader") }
                    }; logs.add("已重启到 bootloader") }
                }) { Text("重启到 fastboot") }
            }
            pickedPath?.let { path ->
                Text("文件: ${File(path).name}", color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("分区:")
                    OutlinedTextField(value = partition, onValueChange = { partition = it.trim().lowercase() },
                        singleLine = true, modifier = Modifier.fillMaxWidth(0.55f), label = { Text("分区名") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("boot","recovery","vbmeta","dtbo","system").forEach { p ->
                        FilterChip(selected = partition == p, onClick = { partition = p },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                if (partition in FastbootFlasher.PROTECTED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(16.dp))
                        Text(" ⚠ 受保护分区, 误刷可能变砖!", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(onClick = {
                    scope.launch {
                        logs.clear(); progress = 0f; progressLabel = "查找 fastboot 设备..."
                        val usbMgr = UsbDeviceManager.get(ctx)
                        val device = withContext(Dispatchers.IO) { usbMgr.findFastbootDevice() }
                        if (device == null) {
                            logs.add("❌ 未检测到 fastboot 设备")
                            if (AdbManager.isConnected) logs.add("可点「重启到 fastboot」")
                            progress = null; return@launch
                        }
                        if (!usbMgr.hasPermission(device)) {
                            usbMgr.requestFastbootPermission(device)
                            logs.add("请在系统弹窗中授权 USB 权限")
                            progress = null; return@launch
                        }
                        val rawConn = usbMgr.openDevice(device)
                        if (rawConn == null) { logs.add("❌ 打开 USB 设备失败"); progress = null; return@launch }
                        val client = FastbootClient()
                        if (!client.open(device, rawConn)) {
                            logs.add("❌ Fastboot 接口打开失败"); client.close(); progress = null; return@launch
                        }
                        logs.add("✅ 已连接, max-download=${client.getvar("max-download-size") ?: "?"}")
                        progressLabel = "刷写 $partition ..."
                        val ok = withContext(Dispatchers.IO) {
                            client.flashImage(partition, path,
                                onInfo = { msg -> scope.launch { logs.add(msg) } },
                                onProgress = { pct -> scope.launch { progress = pct / 100f } })
                        }
                        client.close()
                        progress = null
                        progressLabel = if (ok) "✅ $partition 完成" else "❌ $partition 失败"
                        logs.add(progressLabel!!)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.height(18.dp)); Text(" 刷入 $partition")
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
            Text("9008 EDL 救砖 (高通)", fontWeight = FontWeight.SemiBold)
            Text("前置: 设备进 9008 (adb reboot edl 或按键组合 / 短接)。Loader 与芯片必须精确匹配。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("application/zip")) }) { Text("选择救砖包") }
            }
            Button(onClick = {
                ctx.startActivity(Intent(ctx, LoaderPickerComposeActivity::class.java))
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Memory, null, modifier = Modifier.height(18.dp)); Text(" 芯片引导选择 (firehose loader)")
            }
            pickedPath?.let {
                Text("救砖包: ${File(it).name}")
                Button(onClick = {
                    scope.launch {
                        progress = 0f; progressLabel = "校验中..."
                        val pack = withContext(Dispatchers.IO) {
                            EdlRescuer(FirehoseProtocol(EdlTransport()), RawprogramParser()).validatePack(it)
                        }
                        if (pack == null || !pack.isValid) logs.add("❌ 不是合法救砖包")
                        else { logs.add("✅ 合法: ${pack.programmer} / ${pack.chipset}"); logs.add("(执行救砖需 OTG 连接 9008 设备)") }
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
private fun CloudGuideTab() {
    var keyword by remember { mutableStateOf("") }
    val guides = remember(keyword) { FlashGuideDatabase.searchBrand(keyword) }
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = keyword, onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索品牌或芯片 (小米 / SM8550 / MTK ...)") },
            singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(guides) { guide -> BrandGuideItem(guide, ctx) }
            item { ToolSetupItem(ctx) }
        }
    }
}

@Composable
private fun BrandGuideItem(guide: FlashGuideDatabase.BrandGuide, ctx: Context) {
    var expanded by remember { mutableStateOf(false) }
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Text(guide.icon, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(guide.brand, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("${guide.loaderSources.size} 个 Loader · ${guide.authBypass.size} 种免授权方案",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideSectionItem(Icons.Default.PhonelinkSetup, "进入 9008") { MonoBlock(guide.edlKeyCombo, ctx) }
                GuideSectionItem(Icons.Default.Route, "短接点 / 工程线") { MonoBlock(guide.testPoint, ctx) }
                GuideSectionItem(Icons.Default.Security, "免授权方法") {
                    guide.authBypass.forEachIndexed { i, m ->
                        Text("${i + 1}. $m", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                GuideSectionItem(Icons.Default.Memory, "Loader 下载源") {
                    guide.loaderSources.forEach { ls ->
                        Column(modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(10.dp)) {
                            Text("${ls.chipset} → ${ls.filename}",
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            ls.urls.forEachIndexed { idx, url ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("源${idx + 1}: $url",
                                        style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { copyText(ctx, url) }) {
                                        Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(16.dp).height(16.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                GuideSectionItem(Icons.Default.MenuBook, "推荐工具") {
                    guide.toolHints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 1.dp)) }
                }
                Surface(color = JFColors.Warning.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("⚠ ${guide.notes}", style = MaterialTheme.typography.labelSmall,
                        color = JFColors.Warning,
                        modifier = Modifier.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideSectionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp).height(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.padding(start = 22.dp)) { content() }
    }
}

@Composable
private fun MonoBlock(text: String, ctx: Context) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = { copyText(ctx, text) }) {
            Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(14.dp).height(14.dp))
        }
    }
}

@Composable
private fun ToolSetupItem(ctx: Context) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = 16.dp) {
        Text("🛠 工具链", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        FlashGuideDatabase.TOOL_SETUP.forEach { line ->
            Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (line.endsWith(":")) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

@Composable
private fun PartitionEditorTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts = remember { mutableStateListOf<com.jifeng.toolbox.core.Partition>() }
    var loading by remember { mutableStateOf(false) }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("分区表编辑器", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    scope.launch {
                        loading = true; parts.clear()
                        val list = withContext(Dispatchers.IO) {
                            AdbManager.listDevices().firstOrNull()?.let { DeviceDetector.probeAdbDevice(it).partitions }
                                ?: emptyList()
                        }
                        parts.addAll(list); loading = false
                    }
                }) { Icon(Icons.Default.Refresh, null, modifier = Modifier.height(18.dp)); Text(" 读取分区表") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ctx.startActivity(Intent(ctx, PartitionEditorComposeActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Storage, null, modifier = Modifier.height(18.dp)); Text(" rawprogram.xml 编辑器")
            }
            Spacer(Modifier.height(12.dp))
            if (loading) Text("读取中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (parts.isEmpty()) Text("点击上方按钮读取被控端分区表\n分区数为 0 → 中了格机文件",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else parts.forEach { p ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.name, color = if (p.isProtected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (p.isProtected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall)
                    Text("${p.size / 1024 / 1024} MB", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("jf", text))
}

@Composable
private fun rememberLauncherForActivityResult(
    contract: ActivityResultContracts.OpenDocument,
    callback: (Uri?) -> Unit
) = androidx.activity.compose.rememberLauncherForActivityResult(contract, callback)
