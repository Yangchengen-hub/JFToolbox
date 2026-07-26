package com.jifeng.toolbox.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.FileViewer
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.terminal.TerminalComposeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrowserComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent?.getStringExtra("initial_url")
        setContent { BrowserScreen(initialUrl = initialUrl) }
    }
}

@Composable
private fun BrowserScreen(initialUrl: String? = null) {
    // 0=网页浏览, 1=文件查看器, 2=代码编辑器
    var mode by remember { mutableStateOf(0) }
    val tabs = listOf("网页浏览", "文件查看器", "代码编辑器")

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("内置浏览器", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("WebView + 全格式查看器 + 代码编辑 + 终端联动。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            TabRow(selectedTabIndex = mode) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = mode == i,
                        onClick = { mode = i },
                        text = { Text(title) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // 用 weighted Box 撑满剩余高度, 使各 Tab 内部的 weight(1f) 卡片可正确填充
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (mode) {
                    0 -> WebBrowseTab(initialUrl = initialUrl)
                    1 -> FileViewerTab()
                    2 -> CodeEditorTab()
                }
            }
        }
    }
}

// ========================= 网页浏览 =========================

@Composable
private fun WebBrowseTab(initialUrl: String? = null) {
    var url by remember { mutableStateOf(initialUrl ?: "https://www.baidu.com") }
    var loadTrigger by remember { mutableStateOf(0) }
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // 如果从外部传入了 initial_url, 首次进入立即触发一次加载
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && loadTrigger == 0) {
            loadTrigger++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = url, onValueChange = { url = it },
                    modifier = Modifier.weight(1f), label = { Text("URL") }, singleLine = true)
                Button(onClick = { loadTrigger++ }) { Text("打开") }
                Button(onClick = {
                    // 终端联动: 复制 URL 到剪贴板, 提示在终端 paste 打开
                    val clip = ClipData.newPlainText("JF URL", url)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(ctx, "已复制, 可在终端 paste 打开", Toast.LENGTH_SHORT).show()
                }) { Text("终端联动") }
            }
        }
        Spacer(Modifier.height(12.dp))

        LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 4.dp) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(url)
                    }
                },
                update = { web ->
                    // 仅在 loadTrigger 变化时显式加载, 避免 url 输入过程中频繁重载
                    if (loadTrigger > 0) web.loadUrl(url)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ========================= 文件查看器 =========================

private data class FileInfo(val name: String, val size: Long, val mime: String, val modified: Long)

@Composable
private fun FileViewerTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var uri by remember { mutableStateOf<Uri?>(null) }
    var displayName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var mime by remember { mutableStateOf("") }
    var modified by remember { mutableStateOf(0L) }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var fileType by remember { mutableStateOf(FileViewer.FileType.UNKNOWN) }
    var loading by remember { mutableStateOf(false) }

    // 各类型渲染态
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var textContent by remember { mutableStateOf("") }
    var hexContent by remember { mutableStateOf("") }
    var archiveEntries by remember { mutableStateOf<List<String>>(emptyList()) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            uri = result
            loading = true
            scope.launch {
                val info = queryFileInfo(ctx, result)
                val tmp = copyUriToTemp(ctx, result, info.name)
                val type = tmp?.let { FileViewer.detectType(it) } ?: FileViewer.FileType.UNKNOWN
                withContext(Dispatchers.Main) {
                    displayName = info.name
                    fileSize = info.size
                    mime = info.mime
                    modified = info.modified
                    tempFile = tmp
                    fileType = type
                    // 重置渲染态
                    imageBitmap = null
                    textContent = ""
                    hexContent = ""
                    archiveEntries = emptyList()
                    pdfBitmap = null
                    loading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(arrayOf("*/*")) }) { Text("选择文件") }
                if (uri != null) {
                    Text("已选: $displayName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (uri != null) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("文件名: $displayName", style = infoStyle())
                    Text("大小: ${formatSize(fileSize)}", style = infoStyle())
                    Text("MIME: $mime", style = infoStyle())
                    Text("修改时间: ${formatTime(modified)}", style = infoStyle())
                    Text("类型: $fileType", style = infoStyle())
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (tempFile != null && !loading) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 8.dp) {
                val f = tempFile!!
                when (fileType) {
                    FileViewer.FileType.IMAGE -> {
                        LaunchedEffect(f) {
                            imageBitmap = withContext(Dispatchers.IO) {
                                try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Exception) { null }
                            }
                        }
                        val bmp = imageBitmap
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = displayName,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Text("无法解码图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FileViewer.FileType.VIDEO -> {
                        val mediaUri = uri
                        if (mediaUri != null) {
                            AndroidView(factory = { c ->
                                VideoView(c).apply {
                                    setVideoURI(mediaUri)
                                    setMediaController(MediaController(c))
                                    requestFocus()
                                    start()
                                }
                            }, modifier = Modifier.fillMaxSize())
                        }
                    }
                    FileViewer.FileType.AUDIO -> {
                        AudioPlayer(uri = uri, ctx = ctx)
                    }
                    FileViewer.FileType.TEXT -> {
                        LaunchedEffect(f) {
                            textContent = withContext(Dispatchers.IO) {
                                FileViewer.readText(f) ?: "无法读取文本"
                            }
                        }
                        Text(textContent,
                            modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    FileViewer.FileType.PDF -> {
                        LaunchedEffect(f) {
                            pdfBitmap = withContext(Dispatchers.IO) { FileViewer.renderPdfFirstPage(f) }
                        }
                        val bmp = pdfBitmap
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "PDF 首页",
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Text("无法渲染 PDF (仅支持 API 21+)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FileViewer.FileType.ARCHIVE -> {
                        LaunchedEffect(f) {
                            archiveEntries = withContext(Dispatchers.IO) { FileViewer.listArchiveEntries(f) }
                        }
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize()) {
                            Text("条目数: ${archiveEntries.size}",
                                style = infoStyle(), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            archiveEntries.forEach { entry ->
                                Text(entry,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (archiveEntries.isEmpty()) {
                                Text("无法解析压缩包 (仅支持 ZIP/APK/JAR)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    FileViewer.FileType.BINARY, FileViewer.FileType.UNKNOWN -> {
                        LaunchedEffect(f) {
                            hexContent = withContext(Dispatchers.IO) { FileViewer.hexDump(f) }
                        }
                        Text(hexContent,
                            modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** 简易音频播放控件: MediaPlayer + 播放/停止。 */
@Composable
private fun AudioPlayer(uri: Uri?, ctx: Context) {
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }

    LaunchedEffect(uri) {
        if (uri != null) {
            try {
                player.reset()
                player.setDataSource(ctx, uri)
                player.prepare()
                prepared = true
                playing = false
            } catch (_: Exception) {
                prepared = false
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { player.release() } catch (_: Exception) {} }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (prepared) {
                    try {
                        if (playing) player.pause() else player.start()
                        playing = !playing
                    } catch (_: Exception) {}
                }
            }, enabled = prepared) { Text(if (playing) "暂停" else "播放") }
            Button(onClick = {
                if (prepared) {
                    try { player.pause(); player.seekTo(0) } catch (_: Exception) {}
                    playing = false
                }
            }, enabled = prepared) { Text("停止") }
        }
        Spacer(Modifier.height(8.dp))
        if (prepared) {
            Text("时长: ${player.duration} ms | 当前: ${player.currentPosition} ms",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface)
        } else {
            Text("音频准备中或失败...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ========================= 代码编辑器 =========================

@Composable
private fun CodeEditorTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var uri by remember { mutableStateOf<Uri?>(null) }
    var displayName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var statusMsg by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            uri = result
            statusMsg = "加载中..."
            scope.launch {
                val name = queryFileInfo(ctx, result).name
                val tmp = copyUriToTemp(ctx, result, name)
                val text = tmp?.let { FileViewer.readText(it) } ?: ""
                withContext(Dispatchers.Main) {
                    displayName = name
                    tempFile = tmp
                    content = text
                    statusMsg = if (tmp == null) "加载失败" else "已加载 ${text.length} 字符"
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(arrayOf("*/*")) }) { Text("选择文件") }
                if (uri != null) {
                    Button(onClick = {
                        val u = uri ?: return@Button
                        val ok = writeContentToUri(ctx, u, content)
                        // 同步临时文件以便终端推送最新内容
                        try { tempFile?.writeText(content) } catch (_: Exception) {}
                        statusMsg = if (ok) "已保存" else "保存失败"
                    }) { Text("保存") }
                    Button(onClick = {
                        val f = tempFile
                        if (f == null) {
                            statusMsg = "无文件"
                        } else {
                            // 保存最新内容到临时文件后再推送
                            try { f.writeText(content) } catch (_: Exception) {}
                            openInTerminal(ctx, f)
                        }
                    }) { Text("在终端打开") }
                }
                if (displayName.isNotBlank()) {
                    Text(displayName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (uri != null) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 8.dp) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxSize(),
                    label = { Text(displayName) },
                    visualTransformation = SyntaxHighlightTransformation(displayName),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )
            }
        } else {
            Text("请选择一个文本/代码文件进行编辑",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (statusMsg.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(statusMsg, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 语法高亮 VisualTransformation: 把编辑框文本按文件名着色显示。 */
private class SyntaxHighlightTransformation(private val fileName: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = FileViewer.syntaxHighlight(text.text, fileName)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

// ========================= 工具函数 =========================

@Composable
private fun infoStyle() = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = FontFamily.Monospace, fontSize = 12.sp)

/** 通过 SAF 查询文件名/大小/MIME/修改时间。 */
private fun queryFileInfo(ctx: Context, uri: Uri): FileInfo {
    val cr = ctx.contentResolver
    var name = uri.lastPathSegment ?: "unknown"
    var size = 0L
    var mime = cr.getType(uri) ?: "application/octet-stream"
    var modified = 0L
    try {
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                val modIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (modIdx >= 0) modified = c.getLong(modIdx)
            }
        }
    } catch (_: Exception) {}
    return FileInfo(name, size, mime, modified)
}

/** 将 Uri 内容复制到缓存目录临时文件 (便于交给 FileViewer 处理 + 终端推送)。 */
private fun copyUriToTemp(ctx: Context, uri: Uri, name: String): File? {
    return try {
        val safeName = name.ifBlank { "jf_temp" }.replace('/', '_')
        val tmp = File(ctx.cacheDir, "fv_${System.currentTimeMillis()}_$safeName")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: return null
        tmp
    } catch (e: Exception) {
        null
    }
}

/** 把编辑后的内容写回原 Uri (覆盖写)。 */
private fun writeContentToUri(ctx: Context, uri: Uri, content: String): Boolean {
    return try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } ?: return false
        true
    } catch (e: Exception) {
        false
    }
}

/** 推送文件到被控设备并启动 TerminalComposeActivity 自动执行。 */
private fun openInTerminal(ctx: Context, file: File) {
    val serial = AdbManager.currentSerial
    if (serial.isNullOrBlank()) {
        Toast.makeText(ctx, "ADB 未连接, 无法推送文件", Toast.LENGTH_SHORT).show()
        return
    }
    val remote = "/data/local/tmp/jf_${file.name}"
    val ok = AdbManager.push(serial, file.absolutePath, remote)
    if (!ok) {
        Toast.makeText(ctx, "推送文件失败", Toast.LENGTH_SHORT).show()
        return
    }
    val cmd = buildRunCommand(file.name, remote)
    val intent = Intent(ctx, TerminalComposeActivity::class.java).apply {
        putExtra("auto_command", cmd)
        putExtra("auto_lang", "shell")
    }
    ctx.startActivity(intent)
}

/** 按扩展名生成执行命令 (shell 模式下交给被控端对应解释器运行)。 */
private fun buildRunCommand(fileName: String, remote: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val r = "'$remote'"
    return when (ext) {
        "sh", "bash" -> "sh $r"
        "py" -> "python3 $r"
        "js" -> "node $r"
        "lua" -> "lua $r"
        "rb" -> "ruby $r"
        "pl" -> "perl $r"
        else -> "cat $r"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
}
