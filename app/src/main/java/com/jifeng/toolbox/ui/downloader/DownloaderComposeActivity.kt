package com.jifeng.toolbox.ui.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.tools.SegmentedDownloader
import com.jifeng.toolbox.tools.TorrentParser
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloaderComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DownloaderScreen() }
    }
}

@Composable
private fun DownloaderScreen() {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    // torrent 相关状态: 解析出的信息 + 缓存的种子文件(供 downloader.startTorrent 使用)
    var torrentFile by remember { mutableStateOf<File?>(null) }
    var torrentInfo by remember { mutableStateOf<TorrentParser.TorrentInfo?>(null) }

    val downloader = remember { SegmentedDownloader(segmentCount = 4) }

    // .torrent 文件选择器 (mime: application/x-bittorrent)
    val torrentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                // 缓存到临时文件供 downloader.startTorrent 使用
                val tmp = File(ctx.cacheDir, "selected_${System.currentTimeMillis()}.torrent")
                tmp.writeBytes(bytes)
                torrentFile = tmp
                TorrentParser.parse(bytes)
            }
        }.getOrNull()?.also { info ->
            torrentInfo = info
            logs.add("📄 已加载种子: ${info.name}")
            logs.add("  大小: ${formatBytes(info.totalLength)} · 文件数: ${info.files.size}")
            logs.add("  info_hash: ${info.infoHashHex}")
        } ?: run {
            Toast.makeText(ctx, "种子文件解析失败", Toast.LENGTH_SHORT).show()
            logs.add("✗ 种子文件解析失败")
        }
    }

    // 订阅下载状态
    LaunchedEffect(downloader) {
        downloader.state.collect { state ->
            when (state) {
                is SegmentedDownloader.State.Idle -> {}
                is SegmentedDownloader.State.Downloading -> {
                    isDownloading = true
                    progress = state.progress
                    val speedTxt = if (state.speedKBps > 1024) "%.1f MB/s".format(state.speedKBps / 1024.0) else "${state.speedKBps} KB/s"
                    val dlTxt = formatBytes(state.downloaded)
                    val totalTxt = if (state.total > 0) formatBytes(state.total) else "?"
                    progressLabel = "$dlTxt / $totalTxt · $speedTxt"
                }
                is SegmentedDownloader.State.Done -> {
                    isDownloading = false
                    progress = 1f
                    progressLabel = "下载完成"
                    logs.add("✓ 已保存: ${state.file.absolutePath}")
                    logs.add("  大小: ${formatBytes(state.file.length())}")
                }
                is SegmentedDownloader.State.Failed -> {
                    isDownloading = false
                    progress = null
                    progressLabel = "下载失败"
                    logs.add("✗ ${state.message}")
                }
                SegmentedDownloader.State.Cancelled -> {
                    isDownloading = false
                    progress = null
                    progressLabel = "已取消"
                    logs.add("⏹ 下载已取消")
                }
            }
        }
    }

    JFScaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("全能下载器", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("支持 HTTP/HTTPS 多线程分片下载, 可解析 .torrent 种子并生成磁力链接。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = url, onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("下载 URL") }, singleLine = true,
                        placeholder = { Text("https://example.com/file.zip") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (url.isBlank()) return@Button
                                logs.clear()
                                val dir = File(ctx.getExternalFilesDir(null), "downloads").absolutePath
                                logs.add("目标: $url")
                                logs.add("分片数: 4 · 保存到: $dir")
                                downloader.start(url, dir)
                            },
                            enabled = !isDownloading && url.isNotBlank()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 开始下载")
                        }
                        Button(
                            onClick = { downloader.cancel() },
                            enabled = isDownloading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 取消")
                        }
                    }

                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { torrentLauncher.launch(arrayOf("application/x-bittorrent")) },
                            enabled = !isDownloading
                        ) {
                            Icon(Icons.Default.Attachment, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 选择 .torrent 种子")
                        }
                    }
                }
            }

            // 种子信息卡片: 仅在解析成功后展示
            torrentInfo?.let { info ->
                Spacer(Modifier.height(12.dp))
                TorrentInfoCard(
                    info = info,
                    isDownloading = isDownloading,
                    onCopyMagnet = {
                        val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("magnet", info.magnetUri))
                        Toast.makeText(ctx, "磁力链接已复制", Toast.LENGTH_SHORT).show()
                        logs.add("📋 已复制磁力链接")
                    },
                    onOpenExternal = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.magnetUri))
                        runCatching {
                            ctx.startActivity(Intent.createChooser(intent, "选择 BT 客户端"))
                            logs.add("↗ 已唤起外部应用")
                        }.onFailure {
                            Toast.makeText(ctx, "未找到可处理 magnet: 的应用", Toast.LENGTH_SHORT).show()
                            logs.add("✗ 未找到可处理 magnet: 的应用")
                        }
                    },
                    onHttpDownload = {
                        val file = torrentFile
                        if (file == null) {
                            Toast.makeText(ctx, "种子文件已丢失, 请重新选择", Toast.LENGTH_SHORT).show()
                        } else {
                            logs.clear()
                            val dir = File(ctx.getExternalFilesDir(null), "downloads").absolutePath
                            logs.add("尝试 HTTP web seed 下载: ${info.name}")
                            val ok = downloader.startTorrent(file, dir)
                            if (!ok) {
                                Toast.makeText(ctx,
                                    "本工具不内置 BT 协议, 请用外部客户端下载",
                                    Toast.LENGTH_LONG).show()
                                logs.add("✗ 无可用 HTTP 直链源, 请用外部 BT 客户端")
                            } else {
                                logs.add("✓ 命中 web seed, 开始分段下载")
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * 种子信息卡片: 展示名称/大小/文件数/tracker/磁力链接,
 * 并提供复制磁链 / 外部打开 / HTTP web seed 下载 三个操作按钮。
 */
@Composable
private fun TorrentInfoCard(
    info: TorrentParser.TorrentInfo,
    isDownloading: Boolean,
    onCopyMagnet: () -> Unit,
    onOpenExternal: () -> Unit,
    onHttpDownload: () -> Unit
) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("种子信息", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            InfoRow("名称", info.name)
            InfoRow("总大小", formatBytes(info.totalLength))
            InfoRow("文件数", info.files.size.toString())
            InfoRow("分片", "${info.pieceCount} 片 × ${formatBytes(info.pieceLength)}")
            if (info.createdAt != null) {
                InfoRow("创建时间", SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.getDefault()
                ).format(Date(info.createdAt * 1000)))
            }
            InfoRow("info_hash", info.infoHashHex)
            if (info.trackers.isNotEmpty()) {
                InfoRow("Tracker", info.trackers.joinToString("\n") { "· $it" })
            }
            if (info.webSeedUrls.isNotEmpty()) {
                InfoRow("Web Seed", info.webSeedUrls.joinToString("\n") { "· $it" })
            }

            Spacer(Modifier.height(4.dp))
            Text("磁力链接:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                info.magnetUri,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopyMagnet) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null,
                        modifier = Modifier.height(16.dp))
                    Text(" 复制磁链")
                }
                OutlinedButton(onClick = onOpenExternal) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null,
                        modifier = Modifier.height(16.dp))
                    Text(" 外部打开")
                }
                OutlinedButton(
                    onClick = onHttpDownload,
                    enabled = !isDownloading
                ) {
                    Icon(Icons.Default.Download, contentDescription = null,
                        modifier = Modifier.height(16.dp))
                    Text(" HTTP 下载")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("⚠ 本工具不内置 BT 协议, 解析种子后请用外部客户端（如 LibreTorrent/Flud）下载。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
