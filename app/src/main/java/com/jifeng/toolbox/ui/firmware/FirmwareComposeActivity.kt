package com.jifeng.toolbox.ui.firmware

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.tools.FirmwareSearcher
import com.jifeng.toolbox.tools.SegmentedDownloader
import com.jifeng.toolbox.ui.browser.BrowserComposeActivity
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LiquidGlassClickableCard
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
    val coolapkResults = remember { mutableStateListOf<FirmwareSearcher.CoolapkSource>() }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatus by remember { mutableStateOf("") }
    // 0 = GitHub/官方源, 1 = 酷安/社区大佬
    var selectedTab by remember { mutableStateOf(0) }

    val downloader = remember { SegmentedDownloader(segmentCount = 8) }  // 8 线程下载

    // 订阅下载状态
    LaunchedEffect(downloader) {
        downloader.state.collect { state ->
            when (state) {
                is SegmentedDownloader.State.Downloading -> {
                    progress = state.progress
                    val speedTxt = if (state.speedKBps > 1024) "%.1f MB/s".format(state.speedKBps / 1024.0)
                                   else "${state.speedKBps} KB/s"
                    progressLabel = "${formatBytes(state.downloaded)} / ${
                        if (state.total > 0) formatBytes(state.total) else "?"
                    } · $speedTxt · 8线程"
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

    // 订阅搜索状态 (含搜索进度提示)
    LaunchedEffect(Unit) {
        FirmwareSearcher.state.collect { state ->
            when (state) {
                is FirmwareSearcher.SearchState.Searching -> {
                    isSearching = true
                    searchStatus = state.progress
                }
                is FirmwareSearcher.SearchState.Done -> {
                    isSearching = false
                    searchStatus = ""
                    results.clear()
                    results.addAll(state.results)
                    coolapkResults.clear()
                    coolapkResults.addAll(state.coolapkMatches)
                    logs.add("✓ 搜索完成: ${state.results.size} 个固件, ${state.coolapkMatches.size} 个社区源")
                    if (state.results.isEmpty() && state.coolapkMatches.isEmpty()) {
                        logs.add("未找到匹配固件, 可尝试更具体的型号或代号")
                    }
                }
                is FirmwareSearcher.SearchState.Failed -> {
                    isSearching = false
                    searchStatus = ""
                    logs.add("✗ ${state.message}")
                }
                else -> {}
            }
        }
    }

    /** 启动内置浏览器 (BrowserComposeActivity), 自动打开指定 URL。 */
    fun openInBrowser(url: String) {
        val intent = Intent(ctx, BrowserComposeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("initial_url", url)
        }
        ctx.startActivity(intent)
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("固件下载 · ROM 聚合",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("聚合 GitHub + 官方 API + 社区大佬 ROM 大全, 自动去重, 8 线程下载。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // 搜索栏
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("设备代号/型号 (如 sargo / SM-S9210 / alioth)") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (query.isBlank()) return@Button
                                logs.clear()
                                logs.add("聚合搜索: $query")
                                scope.launch { FirmwareSearcher.search(query) }
                            },
                            enabled = !isSearching && query.isNotBlank(),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null,
                                    modifier = Modifier.height(18.dp))
                            }
                            Text(" 检索")
                        }
                        OutlinedButton(
                            onClick = {
                                if (query.isBlank()) return@OutlinedButton
                                val url = FirmwareSearcher.buildCoolapkSearchUrl(query)
                                logs.add("内置浏览器打开酷安搜索: $url")
                                openInBrowser(url)
                            },
                            enabled = query.isNotBlank(),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null,
                                modifier = Modifier.height(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Text(" 酷安搜索")
                        }
                    }
                    // 搜索进度条
                    if (isSearching && searchStatus.isNotBlank()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(searchStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 源切换 Tab
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("GitHub/官方 (${results.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("社区大佬 (${coolapkResults.size})") })
            }
            Spacer(Modifier.height(8.dp))

            // 结果列表
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                when (selectedTab) {
                    0 -> {
                        if (results.isEmpty() && !isSearching) {
                            Text(
                                "输入设备代号后点击「检索」搜索 GitHub ROM 仓库 + OrangeFox 官方 API + 预设 ROM 源\n\n" +
                                "支持机型示例:\n" +
                                "• Pixel: sargo, redfin, raven, oriole\n" +
                                "• 小米/红米: alioth, raphael, nabu, psyche\n" +
                                "• 一加: lemonade, kebab, instantnoodle\n" +
                                "• 三星: SM-S9210, d1q\n" +
                                "• MTK: MT6989, MT6985",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(results) { entry ->
                                    FirmwareEntryCard(
                                        entry = entry,
                                        onDownload = { asset ->
                                            logs.clear()
                                            logs.add("下载: ${asset.name} (${asset.sizeFormatted})")
                                            val dir = File(ctx.getExternalFilesDir(null), "firmware").absolutePath
                                            downloader.start(asset.downloadUrl, dir, asset.name)
                                        },
                                        onOpenBrowser = { url ->
                                            logs.add("内置浏览器打开: $url")
                                            openInBrowser(url)
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        Text(
                            "酷安/自建站 ROM 合集 (跳转内置浏览器查看), 共 ${coolapkResults.size} 个匹配源",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(coolapkResults) { src ->
                                CoolapkSourceCard(
                                    src = src,
                                    onClick = {
                                        logs.add("内置浏览器打开: ${src.title}")
                                        openInBrowser(src.url)
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 刷机包校验 (MD5/SHA256 + 结构) ----------
            PackageVerifyCard(logs)

            Spacer(Modifier.height(12.dp))
            LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PackageVerifyCard(logs: MutableList<String>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedPath by remember { mutableStateOf<String?>(null) }
    var expectedMd5 by remember { mutableStateOf("") }
    var expectedSha256 by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_verify_${System.currentTimeMillis()}.zip")
            try {
                ctx.contentResolver.openInputStream(it)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                pickedPath = tmp.absolutePath
                logs.add("已选择: ${tmp.name} (${tmp.length() / 1024 / 1024} MB)")
                result = ""
            } catch (e: Exception) {
                logs.add("✗ 读取失败: ${e.message}")
            }
        }
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Source, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("线刷包 / 卡刷包校验", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Text("支持线刷包 (.zip / .tgz / rawprogram + firehose) / 卡刷包 (fastboot .zip / Recovery update.zip) / 单镜像 .img\n" +
                    "自动校验: 包结构 + MD5 + SHA256, 与官方/作者公布的指纹比对。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.Source, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp))
                    Text(" 选择刷机包")
                }
                pickedPath?.let {
                    Text("已选: ${File(it).name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(top = 8.dp))
                }
            }

            pickedPath?.let { path ->
                OutlinedTextField(value = expectedMd5, onValueChange = { expectedMd5 = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("预期 MD5 (可选)") })
                OutlinedTextField(value = expectedSha256, onValueChange = { expectedSha256 = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("预期 SHA-256 (可选)") })

                Button(onClick = {
                    scope.launch {
                        logs.add("──── 开始校验 ${File(path).name} ────")
                        result = ""
                        val (ok, report) = verifyPackage(path, expectedMd5, expectedSha256, logs)
                        result = if (ok) "✅ 校验通过\n$report" else "❌ 校验失败\n$report"
                        logs.add(result.replace("\n", " "))
                    }
                }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text(" 开始校验")
                }

                if (result.isNotBlank()) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                        Text(result,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (result.startsWith("✅")) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private suspend fun verifyPackage(
    path: String,
    expectedMd5: String,
    expectedSha256: String,
    logs: MutableList<String>
): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val f = File(path)
    if (!f.exists()) return@withContext Pair(false, "文件不存在")

    val sb = StringBuilder()
    sb.append("文件: ${f.name}\n")
    sb.append("大小: ${f.length()} bytes (${f.length() / 1024 / 1024} MB)\n")

    // 1. 结构校验
    val name = f.name.lowercase()
    val structure = when {
        name.endsWith(".img") -> {
            val head = f.inputStream().use { input ->
                val expected = minOf(512, f.length().toInt())
                val buf = ByteArray(expected)
                var read = 0
                while (read < expected) {
                    val n = input.read(buf, read, expected - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < expected) buf.copyOf(read) else buf
            }
            val magic = head.takeIf { it.size >= 4 }?.let { String(it, 0, minOf(4, it.size)) } ?: ""
            val isBootImg = head.size >= 8 && String(head, 0, 8) == "ANDROID!"
            val isExtImg = magic.startsWith("\u0014") || name.contains("system") || name.contains("vendor")
            sb.append("类型: 单镜像 .img\n")
            sb.append("魔数: ${magic.replace("\n", "\\n").take(8)}\n")
            sb.append(if (isBootImg) "识别: Android boot.img ✅\n"
                      else if (isExtImg) "识别: ext/sparse 文件系统镜像\n"
                      else "识别: 未知镜像格式\n")
            true
        }
        name.endsWith(".zip") || name.endsWith(".tgz") -> {
            sb.append("类型: ZIP / TGZ 压缩包\n")
            try {
                if (name.endsWith(".tgz")) {
                    val tmp = File(f.parentFile, f.nameWithoutExtension + ".tar")
                    f.inputStream().use { input ->
                        java.util.zip.GZIPInputStream(input).use { gz ->
                            tmp.outputStream().use { gz.copyTo(it) }
                        }
                    }
                    verifyZipStructure(tmp, sb, logs)
                    tmp.delete()
                } else {
                    verifyZipStructure(f, sb, logs)
                }
                true
            } catch (e: Exception) {
                sb.append("⚠ ZIP 解析失败: ${e.message}\n")
                false
            }
        }
        else -> {
            sb.append("类型: 未知\n")
            true
        }
    }

    // 2. MD5
    val md5 = hashFile(f, "MD5")
    sb.append("MD5: $md5\n")
    val md5Ok = expectedMd5.isBlank() || expectedMd5.lowercase() == md5.lowercase()
    if (expectedMd5.isNotBlank()) {
        sb.append(if (md5Ok) "MD5 比对 ✅\n" else "MD5 比对 ❌ (预期 ${expectedMd5.lowercase()})\n")
    }

    // 3. SHA-256
    val sha256 = hashFile(f, "SHA-256")
    sb.append("SHA-256: $sha256\n")
    val shaOk = expectedSha256.isBlank() || expectedSha256.lowercase() == sha256.lowercase()
    if (expectedSha256.isNotBlank()) {
        sb.append(if (shaOk) "SHA-256 比对 ✅\n" else "SHA-256 比对 ❌ (预期 ${expectedSha256.lowercase()})\n")
    }

    val ok = structure && md5Ok && shaOk
    Pair(ok, sb.toString())
}

private fun verifyZipStructure(file: File, sb: StringBuilder, logs: MutableList<String>) {
    try {
        org.apache.commons.compress.archivers.zip.ZipFile(file).use { zf ->
            val entries = zf.entries.toList().map { it.name }
            sb.append("ZIP 条目数: ${entries.size}\n")
            // 卡刷包特征
            val hasAndroidInfo = entries.any { it.equals("android-info.txt", true) }
            val hasImgs = entries.any { it.endsWith(".img", true) }
            // OTA update.zip 特征
            val hasPayload = entries.any { it.contains("payload.bin") }
            val hasMetadata = entries.any { it.contains("META-INF/com/android/metadata") }
            // 9008 线刷包特征
            val hasProg = entries.any { it.contains("prog_firehose") || it.contains("firehose") }
            val hasRaw = entries.any { it.contains("rawprogram") }

            when {
                hasAndroidInfo && hasImgs -> {
                    sb.append("结构: fastboot 卡刷包 ✅ (含 android-info.txt + ${entries.count { it.endsWith(".img") }} 个 .img)\n")
                }
                hasPayload -> {
                    sb.append("结构: OTA update.zip ✅ (含 payload.bin)\n")
                    if (hasMetadata) sb.append("  含 META-INF metadata ✅\n")
                }
                hasProg && hasRaw -> {
                    sb.append("结构: 9008 线刷包 ✅ (含 firehose + rawprogram)\n")
                }
                hasImgs && !hasAndroidInfo -> {
                    sb.append("结构: 含镜像的 ZIP (无 android-info, 可能是 TWRP 备份或单包) ⚠\n")
                }
                else -> {
                    sb.append("结构: 未知 ZIP 内容\n")
                }
            }
            // 列出关键文件
            entries.filter { it.endsWith(".img") || it == "android-info.txt" ||
                    it.contains("payload.bin") || it.contains("rawprogram") ||
                    it.contains("firehose") || it.contains("metadata") }.take(15).forEach {
                sb.append("  • $it\n")
            }
        }
    } catch (e: Exception) {
        sb.append("⚠ ZIP 读取失败: ${e.message}\n")
    }
}

private fun hashFile(file: File, algorithm: String): String {
    return try {
        val md = java.security.MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "(计算失败)" }
}

@Composable
private fun FirmwareEntryCard(
    entry: FirmwareSearcher.FirmwareEntry,
    onDownload: (FirmwareSearcher.FirmwareAsset) -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    LiquidGlassClickableCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        padding = 12.dp,
        onClick = { /* 整卡点击展开详情 (后续可加) */ }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Source, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(entry.sourceLabel.ifBlank { entry.repoName },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text(entry.displayDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${entry.releaseName} · ${entry.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))

            entry.assets.forEach { asset ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null,
                            modifier = Modifier.height(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium)
                            Text(asset.sizeFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { onDownload(asset) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null,
                                modifier = Modifier.height(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("下载", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 主页跳转 (供浏览项目说明)
            if (!entry.homepageUrl.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onOpenBrowser(entry.homepageUrl!!) },
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null,
                        modifier = Modifier.height(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查看主页 (内置浏览器)")
                }
            }
        }
    }
}

@Composable
private fun CoolapkSourceCard(
    src: FirmwareSearcher.CoolapkSource,
    onClick: () -> Unit
) {
    LiquidGlassClickableCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        padding = 12.dp,
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrowserUpdated, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(src.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
            Text("作者: ${src.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (src.description.isNotBlank()) {
                Text(src.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                src.tags.take(4).forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors()
                    )
                }
            }
            // 适配设备代号
            if (src.deviceCodes.isNotEmpty()) {
                Text("适配: ${src.deviceCodes.take(6).joinToString(", ")}${if (src.deviceCodes.size > 6) " 等" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.OpenInNew, contentDescription = null,
                    modifier = Modifier.height(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("点击用内置浏览器打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
