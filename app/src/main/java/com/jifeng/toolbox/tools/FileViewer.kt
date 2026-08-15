package com.jifeng.toolbox.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLConnection
import java.util.zip.ZipFile

/**
 * 全格式文件查看器工具集 —— 类型识别 + 文本/二进制/PDF/压缩包处理 + 语法高亮。
 *
 * 仅依赖 Android 标准库 + Compose 文本模块, 不引入第三方查看器。
 */
object FileViewer {

    /** 文件大类。 */
    enum class FileType { IMAGE, VIDEO, AUDIO, TEXT, PDF, ARCHIVE, BINARY, UNKNOWN }

    // ---- 扩展名表 ----
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "3gp", "webm", "mkv", "avi", "mov", "flv")
    private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")
    private val TEXT_EXTS = setOf(
        "txt", "md", "json", "xml", "py", "js", "ts", "sh", "bash",
        "kt", "kts", "java", "c", "cpp", "cc", "h", "hpp", "cs", "go",
        "rs", "rb", "php", "yml", "yaml", "toml", "ini", "conf", "cfg",
        "log", "csv", "html", "htm", "css", "scss", "sql", "lua", "pl",
        "swift", "dart", "gradle", "properties", "gitignore", "dockerfile"
    )
    private val PDF_EXTS = setOf("pdf")
    private val ARCHIVE_EXTS = setOf("zip", "apk", "jar", "aar", "war", "rar", "7z", "tar", "gz", "bz2", "xz")

    /**
     * 根据扩展名识别文件大类; 无扩展名时尝试读首字节判断文本/二进制。
     */
    fun detectType(file: File): FileType {
        val ext = file.extension.lowercase()
        return when (ext) {
            in IMAGE_EXTS -> FileType.IMAGE
            in VIDEO_EXTS -> FileType.VIDEO
            in AUDIO_EXTS -> FileType.AUDIO
            in TEXT_EXTS -> FileType.TEXT
            in PDF_EXTS -> FileType.PDF
            in ARCHIVE_EXTS -> FileType.ARCHIVE
            else -> if (isProbablyText(file)) FileType.TEXT else FileType.BINARY
        }
    }

    /** 推断 MIME 类型。 */
    fun detectMimeType(file: File): String {
        val guessed = URLConnection.guessContentTypeFromName(file.name)
        if (!guessed.isNullOrBlank()) return guessed
        val ext = file.extension.lowercase()
        return when (ext) {
            in IMAGE_EXTS -> "image/$ext"
            in VIDEO_EXTS -> "video/$ext"
            in AUDIO_EXTS -> "audio/$ext"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "jar" -> "application/java-archive"
            "json" -> "application/json"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * 读取文本内容, 超过 [maxBytes] 截断。
     * @return 文本内容, 失败返回 null
     */
    fun readText(file: File, maxBytes: Int = 1024 * 1024): String? {
        return try {
            file.inputStream().use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (total < maxBytes) {
                    val n = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 生成前 [maxBytes] 字节的十六进制 dump (偏移 + 十六进制 + ASCII)。
     */
    fun hexDump(file: File, maxBytes: Int = 4096): String {
        return try {
            val toRead = minOf(file.length().toInt(), maxBytes).coerceAtLeast(0)
            val bytes = ByteArray(toRead)
            file.inputStream().use { input ->
                var off = 0
                while (off < toRead) {
                    val n = input.read(bytes, off, toRead - off)
                    if (n <= 0) break
                    off += n
                }
                buildHexDump(bytes, off)
            }
        } catch (e: Exception) {
            "无法读取: ${e.message}"
        }
    }

    /**
     * 列出 ZIP/APK/JAR 等压缩包的条目名。仅 ZIP 格式 (apk/jar/aar/war 同为 ZIP)。
     */
    fun listArchiveEntries(file: File): List<String> {
        val ext = file.extension.lowercase()
        return when (ext) {
            "zip", "apk", "jar", "aar", "war" -> try {
                java.util.zip.ZipFile(file).use { zf ->
                    zf.entries().asSequence().map { entry ->
                        val size = if (entry.isDirectory) "[DIR]"
                                   else formatSize(entry.size)
                        "${entry.name}  ($size)"
                    }.sorted().toList()
                }
            } catch (e: Exception) { listOf("Error: ${e.message}") }
            "tar" -> listTarEntries(file)
            "rar" -> try {
                val head = ByteArray(8)
                file.inputStream().use { it.read(head) }
                if (head[0] == 0x52.toByte() && head[1] == 0x61.toByte() && head[2] == 0x72.toByte())
                    listOf("(RAR 格式, 需要第三方库解析)") else listOf("(非 RAR 文件)")
            } catch (e: Exception) { listOf("Error: ${e.message}") }
            "7z" -> listOf("(7z 格式, 需要第三方库解析)")
            "gz" -> listOf("(gzip 压缩文件)")
            "bz2" -> listOf("(bzip2 压缩文件)")
            "xz" -> listOf("(xz 压缩文件)")
            else -> listOf("(不支持的压缩格式)")
        }
    }

    /** 解析 APK 的 Manifest 信息 */
    fun parseApkManifest(file: File): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val ctx = android.app.ActivityThread.currentApplication()?.applicationContext
                ?: return mapOf("error" to "无法获取应用上下文")
            val pm = ctx.packageManager
            val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
            if (archiveInfo != null) {
                info["包名"] = archiveInfo.packageName ?: "未知"
                info["版本名"] = archiveInfo.versionName ?: "未知"
                info["版本号"] = archiveInfo.versionCode.toString()
                info["目标SDK"] = archiveInfo.applicationInfo?.targetSdkVersion?.toString() ?: "未知"
            }
            val entries = listArchiveEntries(file)
            info["文件数"] = entries.size.toString()
            val dexCount = entries.count { it.endsWith(".dex") }
            if (dexCount > 0) info["DEX文件数"] = dexCount.toString()
            val libs = entries.filter { it.startsWith("lib/") && it.endsWith(".so") }
            if (libs.isNotEmpty()) {
                info["Native库"] = libs.map { it.split("/").last() }.distinct().joinToString(", ")
            }
        } catch (e: Exception) {
            info["error"] = "解析失败: ${e.message}"
        }
        return info
    }

    /** 列出 TAR 文件内容 (未压缩 TAR) */
    private fun listTarEntries(file: File): List<String> {
        val entries = mutableListOf<String>()
        try {
            file.inputStream().use { input ->
                val header = ByteArray(512)
                var count = 0
                while (input.read(header) == 512 && count < 500) {
                    count++
                    if (header.all { it == 0.toByte() }) break
                    val name = String(header, 0, 100, Charsets.US_ASCII).trimEnd(0.toChar(), ' ')
                    if (name.isBlank()) break
                    val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trimEnd(0.toChar(), ' ')
                    val size = try { sizeStr.toLong(8) } catch (_: Exception) { 0L }
                    val typeFlag = header[156].toInt().toChar()
                    val typeStr = when (typeFlag) {
                        '5' -> "[DIR]"
                        '2' -> "[LINK]"
                        '1' -> "[HARDLINK]"
                        else -> formatSize(size)
                    }
                    entries.add("$name  ($typeStr)")
                    val skipBytes = ((size + 511) / 512) * 512
                    var remaining = skipBytes
                    while (remaining > 0) {
                        val skipped = input.skip(remaining)
                        if (skipped <= 0) break
                        remaining -= skipped
                    }
                }
            }
        } catch (e: Exception) {
            if (entries.isEmpty()) entries.add("Error: ${e.message}")
        }
        return entries.ifEmpty { listOf("(空 TAR 文件)") }
    }

    /** 格式化文件大小 */
    private fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024L * 1024 * 1024)} GB"
    }

    fun renderPdfFirstPage(file: File): Bitmap? {
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return@use null
                renderer.openPage(0).use { page ->
                    // 渲染到原始尺寸 (过大时按 2 倍缩放以避免太糊)
                    val scale = 2
                    val width = (page.width * scale).coerceAtMost(2048)
                    val height = (page.height * scale).coerceAtMost(2048)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    /**
     * 简单语法高亮: 按文件扩展名匹配关键字/字符串/注释/数字/注解。
     * 支持 .kt/.java/.py/.sh/.xml/.json, 其余按通用规则 (字符串 + # 注释)。
     */
    fun syntaxHighlight(text: String, fileName: String): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val tokens = collectTokens(text, ext)
        for ((start, end, style) in tokens) {
            if (start >= 0 && end <= text.length && start < end) {
                builder.addStyle(style, start, end)
            }
        }
        return builder.toAnnotatedString()
    }

    // ========================= 内部实现 =========================

    /** 读首 1KB 判断是否文本 (含 NUL 字节视为二进制)。 */
    private fun isProbablyText(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val sample = ByteArray(1024)
                val n = input.read(sample)
                if (n <= 0) return true
                for (i in 0 until n) {
                    if (sample[i] == 0.toByte()) return false
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun buildHexDump(data: ByteArray, length: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (i < length) {
            sb.append(String.format("%08x  ", i))
            val lineEnd = minOf(i + 16, length)
            for (j in i until i + 16) {
                if (j < lineEnd) {
                    sb.append(String.format("%02x ", data[j].toInt() and 0xff))
                } else {
                    sb.append("   ")
                }
                if (j == i + 7) sb.append(' ')
            }
            sb.append(' ')
            for (j in i until lineEnd) {
                val c = (data[j].toInt() and 0xff).toChar()
                sb.append(if (c.code in 32..126) c else '.')
            }
            sb.append('\n')
            i += 16
        }
        return sb.toString()
    }

    // ---- 语法高亮样式 ----
    private val KEYWORD_STYLE = SpanStyle(color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
    private val STRING_STYLE = SpanStyle(color = Color(0xFF388E3C))
    private val COMMENT_STYLE = SpanStyle(color = Color(0xFF9E9E9E), fontStyle = FontStyle.Italic)
    private val NUMBER_STYLE = SpanStyle(color = Color(0xFFEF6C00))
    private val ANNOTATION_STYLE = SpanStyle(color = Color(0xFFC2185B))
    private val TAG_STYLE = SpanStyle(color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
    private val KEY_STYLE = SpanStyle(color = Color(0xFF1565C0))

    private data class Token(val start: Int, val end: Int, val style: SpanStyle)

    private fun collectTokens(text: String, ext: String): List<Token> {
        val result = mutableListOf<Token>()
        fun addMatches(pattern: Regex, style: SpanStyle) {
            pattern.findAll(text).forEach { m ->
                result.add(Token(m.range.first, m.range.last + 1, style))
            }
        }
        fun addKeywords(keywords: List<String>, style: SpanStyle = KEYWORD_STYLE) {
            if (keywords.isEmpty()) return
            val pattern = keywords.joinToString("|") { Regex.escape(it) }
            addMatches(Regex("\\b($pattern)\\b"), style)
        }

        when (ext) {
            "kt", "kts", "java", "c", "cpp", "cc", "h", "hpp", "cs", "go", "rs", "js", "ts", "swift", "dart", "php" -> {
                addKeywords(KT_JAVA_KEYWORDS)
                addMatches(Regex("\"(\\\\.|[^\"\\\\])*\""), STRING_STYLE)
                addMatches(Regex("'(\\\\.|[^'\\\\])*'"), STRING_STYLE)
                addMatches(Regex("\\b\\d+(\\.\\d+)?[fFlLdD]?\\b"), NUMBER_STYLE)
                addMatches(Regex("@\\w+"), ANNOTATION_STYLE)
                addMatches(Regex("//[^\\n]*"), COMMENT_STYLE)
                addMatches(Regex("/\\*[\\s\\S]*?\\*/"), COMMENT_STYLE)
            }
            "py" -> {
                addKeywords(PY_KEYWORDS)
                addMatches(Regex("\"(\\\\.|[^\"\\\\])*\""), STRING_STYLE)
                addMatches(Regex("'(\\\\.|[^'\\\\])*'"), STRING_STYLE)
                addMatches(Regex("\\b\\d+(\\.\\d+)?\\b"), NUMBER_STYLE)
                addMatches(Regex("@\\w+"), ANNOTATION_STYLE)
                addMatches(Regex("#[^\\n]*"), COMMENT_STYLE)
            }
            "sh", "bash" -> {
                addKeywords(SH_KEYWORDS)
                addMatches(Regex("\"(\\\\.|[^\"\\\\])*\""), STRING_STYLE)
                addMatches(Regex("'[^']*'"), STRING_STYLE)
                addMatches(Regex("\\b\\d+\\b"), NUMBER_STYLE)
                addMatches(Regex("#[^\\n]*"), COMMENT_STYLE)
            }
            "xml", "html", "htm" -> {
                addMatches(Regex("\"[^\"]*\""), STRING_STYLE)
                addMatches(Regex("<!--[\\s\\S]*?-->"), COMMENT_STYLE)
                addMatches(Regex("</?[A-Za-z][A-Za-z0-9_:.-]*"), TAG_STYLE)
                addMatches(Regex("[A-Za-z_][A-Za-z0-9_:.-]*(?=\\s*=)"), KEY_STYLE)
            }
            "json" -> {
                addMatches(Regex("\"(\\\\.|[^\"\\\\])*\"(?=\\s*:)"), KEY_STYLE)
                addMatches(Regex(":\\s*\"(\\\\.|[^\"\\\\])*\""), STRING_STYLE)
                addMatches(Regex(":\\s*(true|false|null)\\b"), KEYWORD_STYLE)
                addMatches(Regex(":\\s*-?\\d+(\\.\\d+)?"), NUMBER_STYLE)
            }
            else -> {
                addMatches(Regex("\"(\\\\.|[^\"\\\\])*\""), STRING_STYLE)
                addMatches(Regex("#[^\\n]*"), COMMENT_STYLE)
            }
        }
        return result
    }

    private val KT_JAVA_KEYWORDS = listOf(
        "fun", "val", "var", "class", "object", "interface", "package", "import",
        "public", "private", "protected", "internal", "return", "if", "else",
        "for", "while", "when", "do", "break", "continue", "null", "true", "false",
        "this", "super", "is", "as", "try", "catch", "finally", "throw", "new",
        "abstract", "open", "final", "override", "companion", "init", "constructor",
        "suspend", "data", "enum", "sealed", "typealias", "by", "get", "set",
        "field", "it", "out", "reified", "inline", "operator", "infix", "external",
        "lateinit", "const", "vararg", "static", "void", "int", "long", "short",
        "byte", "char", "boolean", "float", "double", "String", "typeof", "yield", "where"
    )

    private val PY_KEYWORDS = listOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "for",
        "while", "return", "yield", "with", "try", "except", "finally", "raise",
        "pass", "break", "continue", "lambda", "global", "nonlocal", "True",
        "False", "None", "and", "or", "not", "is", "in", "del", "assert",
        "async", "await", "self", "print"
    )

    private val SH_KEYWORDS = listOf(
        "if", "then", "else", "elif", "fi", "for", "do", "done", "while",
        "case", "esac", "in", "function", "return", "break", "continue", "exit",
        "echo", "read", "printf", "local", "export", "unset", "shift", "test",
        "true", "false"
    )
}
