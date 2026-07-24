package com.jifeng.toolbox.ui.firmware

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.tools.FirmwareSearcher
import com.jifeng.toolbox.tools.SegmentedDownloader
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.launch
import java.io.File

class FirmwareComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FirmwareScreen() }
    }
}

@Composable
private fun FirmwareScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<FirmwareSearcher.FirmwareEntry>() }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    val downloader = remember { SegmentedDownloader(segmentCount = 4) }

    // 订阅下载状态
    LaunchedEffect(downloader) {
        downloader.state.collect { state ->
            when (state) {
                is SegmentedDownloader.State.Downloading -> {
                    progress = state.progress
                    val speedTxt = if (state.speedKBps > 1024) "%.1f MB/s".format(state.speedKBps / 1024.0) else "${state.speedKBps} KB/s"
                    progressLabel = "${formatBytes(state.downloaded)} / ${if (state.total > 0) formatBytes(state.total) else "?"} · $speedTxt"
                }
                is SegmentedDownloader.State.Done -> {
                    progress = 1f
                    progressLabel = "下载完成"
                    logs.add("✓ 已保存: ${state.file.name} (${formatBytes(state.file.length())})")
                }
                is SegmentedDownloader.State.Failed -> {
                    progress = null
                    progressLabel = "下载失败"
                    logs.add("✗ ${state.message}")
                }
                SegmentedDownloader.State.Cancelled -> {
                    progress = null
                    progressLabel = "已取消"
                    logs.add("⏹ 已取消")
                }
                else -> {}
            }
        }
    }

    // 订阅搜索状态
    LaunchedEffect(Unit) {
        FirmwareSearcher.state.collect { state ->
            when (state) {
                is FirmwareSearcher.SearchState.Searching -> { isSearching = true }
                is FirmwareSearcher.SearchState.Done -> {
                    isSearching = false
                    results.clear()
                    results.addAll(state.results)
                    if (state.results.isEmpty()) logs.add("未找到匹配固件")
                }
                is FirmwareSearcher.SearchState.Failed -> {
                    isSearching = false
                    logs.add("✗ ${state.message}")
                }
                else -> {}
            }
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("固件下载", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("搜索 GitHub 上的 ROM/Recovery 刷机包, 多线程下载。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("设备代号/型号 (如 sargo / SM-S9210)") }, singleLine = true)
                    Button(
                        onClick = {
                            if (query.isBlank()) return@Button
                            logs.clear()
                            logs.add("搜索: $query")
                            scope.launch { FirmwareSearcher.search(query) }
                        },
                        enabled = !isSearching && query.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.height(18.dp))
                        }
                        Text(" 检索固件")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                if (results.isEmpty() && !isSearching) {
                    Text("输入设备代号后点击「检索固件」搜索 GitHub 上的 ROM 和 Recovery",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(results) { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(entry.displayTitle, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("${entry.releaseName} · ${entry.displayDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            entry.assets.forEach { asset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            logs.clear()
                                            logs.add("下载: ${asset.name} (${asset.sizeFormatted})")
                                            val dir = File(ctx.getExternalFilesDir(null), "firmware").absolutePath
                                            downloader.start(asset.downloadUrl, dir, asset.name)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null,
                                        modifier = Modifier.height(16.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(0.dp))
                                    Text("  ${asset.name} (${asset.sizeFormatted})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
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
