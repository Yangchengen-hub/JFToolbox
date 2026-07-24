package com.jifeng.toolbox.ui.terminal

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TerminalScreen() }
    }
}

@Composable
private fun TerminalScreen() {
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }
    var langExpanded by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf("shell") }
    val langs = listOf("shell", "python", "javascript", "lua", "c/c++", "ai-llm", "ssh")

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("超级终端", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("多语言 + 本地 AI 模型 + SSH + Shell 权限中枢",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // 语言选择器
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Code, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("运行环境: ", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { langExpanded = true }) {
                        Text(lang)
                    }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        langs.forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { lang = it; langExpanded = false })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("ADB: ${if (AdbManager.isConnected) "✅" else "❌"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 输出区
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (logs.isEmpty()) {
                        Text("等待输入命令...\n提示: shell 模式直接走 ADB, python/js/lua 需被控端装有对应解释器,\nai-llm 需本地量化模型, ssh 需要远程主机信息。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        logs.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 输入区
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入命令并回车") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        val cmd = input; input = ""
                        logs.add("\$ [$lang] $cmd")
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { executeCmd(lang, cmd) }
                            logs.add(out)
                        }
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "执行",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun executeCmd(lang: String, cmd: String): String {
    val serial = AdbManager.listDevices().firstOrNull() ?: return "错误: 无设备连接"
    return when (lang) {
        "shell" -> {
            AdbManager.shell(serial, cmd) ?: "(无输出或执行失败)"
        }
        "python" -> AdbManager.shell(serial, "python3 -c '$cmd' 2>&1 || python -c '$cmd' 2>&1")
            ?: "(需被控端装有 Python)"
        "javascript" -> AdbManager.shell(serial, "node -e '$cmd' 2>&1") ?: "(需被控端装有 node)"
        "lua" -> AdbManager.shell(serial, "lua -e '$cmd' 2>&1") ?: "(需被控端装有 lua)"
        "c/c++" -> "(C/C++ 需先 push 源码到设备, gcc/clang 编译后运行 - Phase 7 集成)"
        "ai-llm" -> "(本地 LLM 推理需集成 llama.cpp Android binding - Phase 7 集成)"
        "ssh" -> "(SSH 需填主机/账号/密钥 - Phase 7 集成)"
        else -> "未知语言"
    }
}
