package com.jifeng.toolbox.ui.crypto

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CryptoScreen() }
    }
}

@Composable
private fun CryptoScreen() {
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("加密 / 解密工具", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text("诚实能力边界: AES-256-CBC / MD5 / SHA-256 / Base64。\n不存在「100% 解密任意加密」的算法, 也不存在「加密后任何工具无法破解且文件正常运行」的方案。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                Text(output.ifBlank { "(输出)" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun md5(s: String): String =
    MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private fun base64Encode(s: String): String =
    Base64.getEncoder().encodeToString(s.toByteArray())

private fun base64Decode(s: String): String =
    try { String(Base64.getDecoder().decode(s)) } catch (e: Exception) { "解码失败: ${e.message}" }

private fun aesEncrypt(s: String, k: String): String = try {
    val key = SecretKeySpec(k.toByteArray().copyOf(32), "AES")
    val iv = IvParameterSpec(ByteArray(16) { 0 })
    val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, key, iv) }
    Base64.getEncoder().encodeToString(c.doFinal(s.toByteArray()))
} catch (e: Exception) { "加密失败: ${e.message}" }

private fun aesDecrypt(s: String, k: String): String = try {
    val key = SecretKeySpec(k.toByteArray().copyOf(32), "AES")
    val iv = IvParameterSpec(ByteArray(16) { 0 })
    val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, key, iv) }
    String(c.doFinal(Base64.getDecoder().decode(s)))
} catch (e: Exception) { "解密失败: ${e.message}" }
