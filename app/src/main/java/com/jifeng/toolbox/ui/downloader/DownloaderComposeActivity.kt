package com.jifeng.toolbox.ui.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.tools.SegmentedDownloader
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import java.io.File

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

    val downloader = remember { SegmentedDownloader(segmentCount = 4) }

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
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("全能下载器", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("支持 HTTP/HTTPS, 多线程分片 (Range) 加速下载。",
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
                }
            }
            Spacer(Modifier.height(12.dp))
            LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
