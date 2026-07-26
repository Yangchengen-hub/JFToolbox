package com.jifeng.toolbox.ui.crypto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.tools.FileCrypto
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CryptoScreen() }
    }
}

@Composable
private fun CryptoScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("文本加解密", "文件加解密")
    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("加密 / 解密工具", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text("诚实能力边界: AES-256-CBC 文件加密 (PBKDF2 密钥派生) / MD5 / SHA-256 / Base64。\n不存在「100% 解密任意加密」的算法, 也不存在「加密后任何工具无法破解且文件正常运行」的方案。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t, style = MaterialTheme.typography.labelLarge) })
                }
            }
            when (tab) {
                0 -> TextCryptoTab()
                1 -> FileCryptoTab()
            }
        }
    }
}

@Composable
private fun TextCryptoTab() {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("输入文本") },
                minLines = 3)
            OutlinedTextField(value = key, onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("AES 密钥 (16/24/32 字节)") },
                singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { output = md5(input) }) { Text("MD5") }
                Button(onClick = { output = sha256(input) }) { Text("SHA-256") }
                Button(onClick = { output = base64Encode(input) }) { Text("Base64 编码") }
                Button(onClick = { output = base64Decode(input) }) { Text("Base64 解码") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { output = aesEncrypt(input, key) }) { Text("AES 加密") }
                Button(onClick = { output = aesDecrypt(input, key) }) { Text("AES 解密") }
            }
        }
    }
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输出",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (output.isNotBlank()) {
                    // 复制到剪贴板
                    IconButton(onClick = {
                        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("JF Crypto", output))
                        Toast.makeText(ctx, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    // 系统分享
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, output)
                            putExtra(Intent.EXTRA_TITLE, "JFToolbox 加密结果")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(Intent.createChooser(send, "分享加密结果"))
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(output.ifBlank { "(输出)" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FileCryptoTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var format by remember { mutableStateOf<FileCrypto.FileFormat?>(null) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var hashResult by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_crypto_in_${System.currentTimeMillis()}")
            ctx.contentResolver.openInputStream(it)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            pickedFile = tmp
            pickedName = it.lastPathSegment ?: tmp.name
            format = FileCrypto.detectFormat(tmp)
            status = null
            progress = null
            outputPath = null
            hashResult = null
        }
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                Text("选择文件 (支持 .sh/.py/.js/.lua/.bin/.jar/.dex/.so 等)")
            }
            pickedFile?.let { f ->
                Text("文件名: ${pickedName ?: f.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("大小: ${humanSize(f.length())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("格式: ${format?.displayName ?: "未知"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedTextField(value = password, onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("密码 (用于 AES-256-CBC 加密)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val src = pickedFile ?: return@Button
                    if (password.isBlank()) { status = "请输入密码"; return@Button }
                    val baseName = pickedName ?: src.name
                    val out = File(ctx.cacheDir, "$baseName.jfc1")
                    status = "加密中…"; progress = 0f; outputPath = null; hashResult = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            FileCrypto.encryptFile(src, out, password) { p -> progress = p }
                        }
                        progress = null
                        status = r.message + " (耗时 ${r.durationMs} ms)"
                        if (r.ok) outputPath = r.outputFile?.absolutePath
                    }
                }) { Text("加密") }
                Button(onClick = {
                    val src = pickedFile ?: return@Button
                    if (password.isBlank()) { status = "请输入密码"; return@Button }
                    val baseName = pickedName ?: src.name
                    val out = File(ctx.cacheDir, "$baseName.decrypted")
                    status = "解密中…"; progress = 0f; outputPath = null; hashResult = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            FileCrypto.decryptFile(src, out, password) { p -> progress = p }
                        }
                        progress = null
                        status = r.message + " (耗时 ${r.durationMs} ms)"
                        if (r.ok) outputPath = r.outputFile?.absolutePath
                    }
                }) { Text("解密") }
                Button(onClick = {
                    val src = pickedFile ?: return@Button
                    status = "计算哈希中…"; progress = 0f; hashResult = null; outputPath = null
                    scope.launch {
                        val h = withContext(Dispatchers.IO) { FileCrypto.fileHash(src) }
                        progress = null
                        status = "SHA-256 计算完成"
                        hashResult = h
                    }
                }) { Text("计算哈希") }
            }
            progress?.let { p ->
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface) }
            hashResult?.let {
                Text("SHA-256: $it",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface)
            }
            outputPath?.let { path ->
                Text("输出文件: $path",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // 系统分享 (与其他 APP 联动)
                    OutlinedButton(onClick = {
                        val file = File(path)
                        if (!file.exists()) {
                            Toast.makeText(ctx, "文件不存在", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            ctx, "${ctx.packageName}.fileprovider", file
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(Intent.createChooser(send, "分享 ${file.name}"))
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = null,
                            modifier = Modifier.height(16.dp))
                        Spacer(Modifier.height(0.dp))
                        Text(" 分享")
                    }
                    // 保存到下载目录
                    OutlinedButton(onClick = {
                        val src = File(path)
                        if (!src.exists()) {
                            Toast.makeText(ctx, "源文件不存在", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                try {
                                    val downloads = android.os.Environment
                                        .getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS
                                        )
                                    val dst = File(downloads, src.name)
                                    src.inputStream().use { input ->
                                        FileOutputStream(dst).use { input.copyTo(it) }
                                    }
                                    true
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "保存失败: ${e.message}",
                                        Toast.LENGTH_SHORT).show()
                                    false
                                }
                            }
                            if (ok) Toast.makeText(ctx, "已保存到 Download/${src.name}",
                                Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Default.SaveAs, contentDescription = null,
                            modifier = Modifier.height(16.dp))
                        Spacer(Modifier.height(0.dp))
                        Text(" 保存到 Download")
                    }
                }
            }
            Text("本工具采用 AES-256-CBC 标准算法, 不存在 100% 解密任意加密的能力, 也不存在加密后绝对不可破解的方案。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun md5(s: String): String =
    MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private fun base64Encode(s: String): String =
    Base64.getEncoder().encodeToString(s.toByteArray())

private fun base64Decode(s: String): String =
    try { String(Base64.getDecoder().decode(s)) } catch (e: Exception) { "解码失败: ${e.message}" }

private fun aesEncrypt(s: String, k: String): String {
    return try {
        if (k.isBlank()) return "密钥不能为空"
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(k, salt)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv)) }
        val ciphertext = c.doFinal(s.toByteArray())
        val result = ByteArray(salt.size + iv.size + ciphertext.size).apply {
            System.arraycopy(salt, 0, this, 0, salt.size)
            System.arraycopy(iv, 0, this, salt.size, iv.size)
            System.arraycopy(ciphertext, 0, this, salt.size + iv.size, ciphertext.size)
        }
        Base64.getEncoder().encodeToString(result)
    } catch (e: Exception) { "加密失败: ${e.message}" }
}

private fun aesDecrypt(s: String, k: String): String {
    return try {
        if (k.isBlank()) return "密钥不能为空"
        val raw = Base64.getDecoder().decode(s)
        if (raw.size < 32) return "数据过短"
        val salt = raw.copyOf(16)
        val iv = raw.copyOfRange(16, 32)
        val ciphertext = raw.copyOfRange(32, raw.size)
        val key = deriveKey(k, salt)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv)) }
        String(c.doFinal(ciphertext))
    } catch (e: Exception) { "解密失败: ${e.message}" }
}

private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
}
