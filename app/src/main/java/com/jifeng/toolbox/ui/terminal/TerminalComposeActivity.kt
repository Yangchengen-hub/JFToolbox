package com.jifeng.toolbox.ui.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.terminal.TerminalEngine
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.launch

/**
 * 超级终端 (Compose) v5 —— 单一本地模式。
 *
 * 合并为单一本地模式, 去掉远程ADB和SSH tab。
 * 本机直接执行命令 (通过 Runtime.getRuntime().exec() / ProcessBuilder)。
 * 支持所有语法/语言 (shell / python / node / lua / c++ 等)。
 * 预装概念: 提示用户可通过内置包管理器安装工具。
 */
class TerminalComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoCommand = intent?.getStringExtra("auto_command")
        val autoLang = intent?.getStringExtra("auto_lang") ?: "shell"
        setContent { TerminalScreen(autoCommand = autoCommand, autoLang = autoLang) }
    }
}

@Composable
private fun TerminalScreen(autoCommand: String? = null, autoLang: String = "shell") {
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(autoLang) }
    var langExpanded by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var showPackageHint by remember { mutableStateOf(false) }

    // 历史日志
    val logs = remember { mutableStateListOf<String>() }

    // 当前输入
    var input by remember { mutableStateOf(autoCommand ?: "") }

    /** 统一执行入口。 */
    fun runCommand(cmd: String, language: String) {
        if (cmd.isBlank() || isRunning) return
        logs.add("$ [$language] $cmd")
        isRunning = true
        statusText = "执行中..."
        scope.launch {
            val result = TerminalEngine.execute(language, cmd)
            logs.add(result.output)
            if (result.durationMs > 0) logs.add("(${result.durationMs}ms)")
            isRunning = false
            statusText = ""
            // 如果提示未安装某工具, 展示包管理器提示
            if (result.output.contains("未安装") || result.output.contains("pkg install")) {
                showPackageHint = true
            }
        }
    }

    // auto_command 联动
    LaunchedEffect(autoCommand) {
        if (!autoCommand.isNullOrBlank()) {
            runCommand(autoCommand, autoLang)
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("超级终端", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("本地执行 · 多语言支持 (shell/python/js/lua/c++/ai-llm)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // 语言选择 + 状态栏
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Code, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("环境:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { langExpanded = true }) { Text(lang) }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        TerminalEngine.LANGUAGES.forEach {
                            DropdownMenuItem(text = { Text(it) },
                                onClick = { lang = it; langExpanded = false })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "本地模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { showPackageHint = true }) {
                        Icon(Icons.Default.Computer, contentDescription = "包管理器",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 输出区
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (logs.isEmpty()) {
                        val tip = """
                            超级终端 (本地模式):
                            • shell: 本机 Shell (sh/bash)
                            • python/js/lua: 需安装对应解释器
                            • c/c++: 本地 gcc/clang 编译运行
                            • ai-llm: 本地 LLM 推理

                            提示: 可通过内置包管理器安装工具
                                  (点击右上角 💻 查看)
                        """.trimIndent()
                        Text(tip,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        logs.forEach { line ->
                            Text(line,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(statusText, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }

            // 输入区
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入命令/代码/prompt") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions.Default,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace))
                IconButton(
                    onClick = {
                        if (input.isNotBlank() && !isRunning) {
                            val cmd = input; input = ""
                            runCommand(cmd, lang)
                        }
                    },
                    enabled = !isRunning && input.isNotBlank()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.outline)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "执行",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // 包管理器提示对话框
    if (showPackageHint) {
        AlertDialog(
            onDismissRequest = { showPackageHint = false },
            title = { Text("内置包管理器") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "如果缺少某些工具 (python/node/lua/gcc 等),\n" +
                        "可通过以下方式安装:\n\n" +
                        "• Termux: pkg install <包名>\n" +
                        "• 系统自带: apt / yum / pacman\n" +
                        "• 直接下载二进制: 访问官方网站\n\n" +
                        "后续版本将集成一键安装功能。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPackageHint = false }) { Text("知道了") }
            }
        )
    }
}
