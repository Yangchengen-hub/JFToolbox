package com.jifeng.toolbox.ui.flash

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jifeng.toolbox.edl.FirehoseLoaderRegistry
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 芯片引导 (firehose loader) 选择器。
 *
 * 功能:
 *   - 顶部「自动探测」按钮: 通过 ADB getprop 读 ro.board.platform, 显示并匹配注册表
 *   - 中部 LazyColumn: 列出 [FirehoseLoaderRegistry.REGISTRY] 全部条目,
 *     每条目显示 chipset / vendor / platforms 别名 / 本地缓存状态
 *   - 每条目右侧: 未缓存 → 下载按钮; 已缓存 → 选择按钮 (回传路径给调用方)
 *   - 底部: 自定义路径选择 (本地 .elf 文件)
 *
 * 回传: Intent extra "loader_path" = 选中文件绝对路径, resultCode = RESULT_OK
 */
class LoaderPickerComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoaderPickerScreen() }
    }
}

@Composable
private fun LoaderPickerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // firehose loader 本地缓存目录
    val cacheDir = remember { File(ctx.cacheDir, "firehose_loaders").apply { mkdirs() } }

    var detectedPlatform by remember { mutableStateOf<String?>(null) }
    var detectedEntry by remember { mutableStateOf<FirehoseLoaderRegistry.LoaderEntry?>(null) }
    var detecting by remember { mutableStateOf(false) }

    val localFiles = remember { mutableStateListOf<File>() }
    // 正在下载的 filename 集合, 用于禁用按钮 + 显示进度
    val downloadingSet = remember { mutableStateListOf<String>() }
    val progressMap = remember { mutableStateListOf<Pair<String, Float>>() }
    val logs = remember { mutableStateListOf<String>() }
    var customPath by remember { mutableStateOf<String?>(null) }

    // 刷新本地缓存列表
    fun refreshLocal() {
        localFiles.clear()
        localFiles.addAll(FirehoseLoaderRegistry.listLocal(cacheDir))
    }
    LaunchedEffect(Unit) { refreshLocal() }

    // 自定义 .elf 选择器
    val customLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val tmp = File(cacheDir, "custom_${System.currentTimeMillis()}.elf")
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(it)?.use { i ->
                        tmp.outputStream().use { i.copyTo(it) }
                    }
                }
                if (tmp.exists() && tmp.length() > 0) {
                    customPath = tmp.absolutePath
                    logs.add("✅ 已选自定义 loader: ${tmp.name} (${tmp.length() / 1024} KB)")
                    refreshLocal()
                } else {
                    logs.add("❌ 自定义 loader 复制失败")
                }
            }
        }
    }

    /** 返回选中的 loader 路径给调用方。 */
    fun pickAndFinish(file: File) {
        logs.add("✓ 已选: ${file.name}")
        val intent = Intent().apply { putExtra("loader_path", file.absolutePath) }
        (ctx as? Activity)?.setResult(Activity.RESULT_OK, intent)
        (ctx as? Activity)?.finish()
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("芯片引导 (firehose loader) 选择", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // ---------- 顶部: 自动探测 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                detecting = true
                                detectedPlatform = null; detectedEntry = null
                                val serial = AdbManager.currentSerial.orEmpty()
                                val p = withContext(Dispatchers.IO) {
                                    FirehoseLoaderRegistry.detectPlatform(serial)
                                }
                                detectedPlatform = p
                                detectedEntry = p?.let { FirehoseLoaderRegistry.match(it) }
                                logs.add(when {
                                    p == null -> "❌ 未探测到平台 (ADB 未连接或 getprop 无值)"
                                    detectedEntry != null -> "✅ 平台=$p → 命中 ${detectedEntry!!.chipset} (${detectedEntry!!.filename})"
                                    else -> "⚠ 平台=$p, 但注册表未命中"
                                })
                                detecting = false
                            }
                        }) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 自动探测平台")
                        }
                        if (detecting) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    }
                    Text("ADB: ${if (AdbManager.isConnected) "已连接 serial=${AdbManager.currentSerial ?: "?"}" else "未连接"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    detectedPlatform?.let { p ->
                        Text("ro.board.platform = $p", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        detectedEntry?.let { e ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("匹配: ${e.chipset} (${e.vendor}) → ${e.filename}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                val cached = localFiles.any { it.name.equals(e.filename, ignoreCase = true) }
                                if (cached) {
                                    OutlinedButton(onClick = {
                                        localFiles.firstOrNull { it.name.equals(e.filename, ignoreCase = true) }
                                            ?.let { pickAndFinish(it) }
                                    }) { Text("选用") }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 中部: REGISTRY 列表 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                Column {
                    Text("共 ${FirehoseLoaderRegistry.REGISTRY.size} 个内置条目 (已缓存 ${localFiles.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(FirehoseLoaderRegistry.REGISTRY) { entry ->
                            LoaderRow(
                                entry = entry,
                                cached = localFiles.any { it.name.equals(entry.filename, ignoreCase = true) },
                                downloading = entry.filename in downloadingSet,
                                progress = progressMap.firstOrNull { it.first == entry.filename }?.second,
                                onDownload = {
                                    scope.launch {
                                        downloadingSet.add(entry.filename)
                                        progressMap.add(entry.filename to 0f)
                                        logs.add("↓ 开始下载 ${entry.filename} ...")
                                        val f = withContext(Dispatchers.IO) {
                                            FirehoseLoaderRegistry.download(entry, cacheDir) { pct ->
                                                // 切回主线程更新进度
                                                scope.launch {
                                                    val idx = progressMap.indexOfFirst { it.first == entry.filename }
                                                    if (idx >= 0) progressMap[idx] = entry.filename to pct
                                                }
                                            }
                                        }
                                        downloadingSet.remove(entry.filename)
                                        progressMap.removeAll { it.first == entry.filename }
                                        if (f != null) {
                                            logs.add("✅ 下载完成: ${entry.filename} (${f.length() / 1024} KB)")
                                            refreshLocal()
                                        } else {
                                            logs.add("❌ 下载失败: ${entry.filename} (检查网络 / downloadUrl 占位)")
                                        }
                                    }
                                },
                                onSelect = {
                                    localFiles.firstOrNull { it.name.equals(entry.filename, ignoreCase = true) }
                                        ?.let { pickAndFinish(it) }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 底部: 自定义路径 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("自定义 loader", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            customLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 选择本地 .elf / .mbn")
                        }
                        customPath?.let { p ->
                            Text("已选: ${File(p).name}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            OutlinedButton(onClick = { pickAndFinish(File(p)) }) {
                                Text("选用")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LogTerminal(logs, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LoaderRow(
    entry: FirehoseLoaderRegistry.LoaderEntry,
    cached: Boolean,
    downloading: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onSelect: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (cached) Icons.Default.CheckCircle else Icons.Default.Memory,
            contentDescription = null,
            tint = if (cached) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.chipset, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(entry.vendor.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cached) {
                    Spacer(Modifier.width(6.dp))
                    Text("已缓存", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Text(entry.filename, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("别名: ${entry.platforms.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (downloading && progress != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        if (downloading) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        } else if (cached) {
            OutlinedButton(onClick = onSelect) { Text("选择") }
        } else {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.height(18.dp))
                Text(" 下载")
            }
        }
    }
}
