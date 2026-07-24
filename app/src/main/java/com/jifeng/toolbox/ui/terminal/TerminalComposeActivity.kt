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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jifeng.toolbox.terminal.SshClient
import com.jifeng.toolbox.terminal.TerminalEngine
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.launch

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
    var showSshConfig by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }

    // SSH 配置
    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("") }
    var sshPass by remember { mutableStateOf("") }
    var sshKeyPath by remember { mutableStateOf("") }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("超级终端", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("多语言 + 本地 AI + SSH + Shell 权限中枢",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // 语言选择器 + SSH 配置
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Code, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("环境: ", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { langExpanded = true }) { Text(lang) }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        TerminalEngine.LANGUAGES.forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { lang = it; langExpanded = false })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("ADB: ${if (AdbManager.isConnected) "✅" else "❌"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showSshConfig = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "SSH 配置",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 输出区
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (logs.isEmpty()) {
                        Text(
                            "等待输入命令...\n\n" +
                            "• shell: ADB shell 直接执行\n" +
                            "• python/js/lua: 需被控端装有对应解释器 (Termux)\n" +
                            "• c/c++: 推送源码 → 设备端 gcc/clang 编译 → 运行\n" +
                            "• ai-llm: 检测并调用本地 LLM (ollama/llama.cpp)\n" +
                            "• ssh: 远程主机执行 (需先点⚙配置)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    placeholder = { Text("输入命令/代码/prompt") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions.Default,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                IconButton(
                    onClick = {
                        if (input.isNotBlank() && !isRunning) {
                            val cmd = input; input = ""
                            logs.add("\$ [$lang] $cmd")
                            isRunning = true
                            scope.launch {
                                val result = TerminalEngine.execute(lang, cmd)
                                logs.add(result.output)
                                if (result.durationMs > 0) {
                                    logs.add("(${result.durationMs}ms)")
                                }
                                isRunning = false
                            }
                        }
                    },
                    enabled = !isRunning && input.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "执行",
                        tint = if (isRunning) MaterialTheme.colorScheme.outline
                               else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // SSH 配置对话框
    if (showSshConfig) {
        AlertDialog(
            onDismissRequest = { showSshConfig = false },
            title = { Text("SSH 配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sshHost, onValueChange = { sshHost = it },
                        label = { Text("主机地址") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sshPort, onValueChange = { sshPort = it },
                        label = { Text("端口") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sshUser, onValueChange = { sshUser = it },
                        label = { Text("用户名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sshPass, onValueChange = { sshPass = it },
                        label = { Text("密码 (可选)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sshKeyPath, onValueChange = { sshKeyPath = it },
                        label = { Text("私钥路径 (可选)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TerminalEngine.configureSsh(SshClient.SshConfig(
                        host = sshHost,
                        port = sshPort.toIntOrNull() ?: 22,
                        username = sshUser,
                        password = sshPass.ifBlank { null },
                        privateKeyPath = sshKeyPath.ifBlank { null }
                    ))
                    showSshConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSshConfig = false }) { Text("取消") }
            }
        )
    }
}
