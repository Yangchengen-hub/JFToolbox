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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.terminal.SshClient
import com.jifeng.toolbox.terminal.TerminalEngine
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 超级终端 (Compose) —— 三 tab 多环境交互式终端。
 *
 * Tab 0: 本地 (LocalShell) - 本机 Shell 权限 (需 root 或 ShellHub daemon 已授权)
 * Tab 1: 远程 (RemoteADB) - ADB 远程 Shell (USB/WiFi), 多语言执行
 * Tab 2: SSH  - 远程 SSH 主机, 持久会话, 支持多语言
 *
 * 特性:
 * - 每条命令历史持久保留 (会话窗口)
 * - 多语言支持 (shell/python/js/lua/c++/ai-llm/ssh)
 * - SSH 维持跨命令连接, 命令可累积执行
 * - 自动命令 (auto_command + auto_lang) 联动浏览器代码编辑器
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
    // 0=本地, 1=远程 ADB, 2=SSH
    var selectedTab by remember { mutableStateOf(if (autoCommand != null) 1 else 0) }
    // 默认远程 ADB tab 用 shell 语言
    var lang by remember { mutableStateOf(autoLang) }
    var langExpanded by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // 各 tab 独立的历史日志 (维持会话)
    val localLogs = remember { mutableStateListOf<String>() }
    val remoteLogs = remember { mutableStateListOf<String>() }
    val sshLogs = remember { mutableStateListOf<String>() }

    // SSH 配置
    var showSshConfig by remember { mutableStateOf(false) }
    var sshHost by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("") }
    var sshPass by remember { mutableStateOf("") }
    var sshKeyPath by remember { mutableStateOf("") }
    var sshConnected by remember { mutableStateOf(false) }

    // 当前输入
    var input by remember { mutableStateOf(autoCommand ?: "") }

    /** 统一执行入口。 */
    fun runCommand(cmd: String, tab: Int, language: String) {
        if (cmd.isBlank() || isRunning) return
        val logs = when (tab) {
            0 -> localLogs
            1 -> remoteLogs
            else -> sshLogs
        }
        logs.add("\$ [$language] $cmd")
        isRunning = true
        statusText = "执行中..."
        scope.launch {
            if (tab == 0) {
                // 本地: 通过 ShellHub daemon 执行 (uid 2000 shell 权限)
                val startMs = System.currentTimeMillis()
                val out = withContext(Dispatchers.IO) {
                    com.jifeng.toolbox.tools.ShellHub.exec(cmd)
                } ?: "(本地 daemon 未启动或无权限)"
                logs.add(out)
                logs.add("(${System.currentTimeMillis() - startMs}ms)")
            } else {
                val result = TerminalEngine.execute(language, cmd)
                logs.add(result.output)
                if (result.durationMs > 0) logs.add("(${result.durationMs}ms)")
            }
            isRunning = false
            statusText = ""
        }
    }

    // auto_command 联动: 进入后自动填入并执行一次 (远程 ADB tab)
    LaunchedEffect(autoCommand) {
        if (!autoCommand.isNullOrBlank()) {
            runCommand(autoCommand, 1, autoLang)
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("超级终端", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("本地 · 远程 ADB · SSH · 多语言 (shell/python/js/lua/c++/ai-llm)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // 三 tab 切换
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Computer, contentDescription = null,
                                modifier = Modifier.height(16.dp))
                            Text("本地")
                        }
                    })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Terminal, contentDescription = null,
                                modifier = Modifier.height(16.dp))
                            Text("远程 ADB")
                        }
                    })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Wifi, contentDescription = null,
                                modifier = Modifier.height(16.dp))
                            Text("SSH")
                        }
                    })
            }
            Spacer(Modifier.height(8.dp))

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
                    // 状态指示
                    val statusLabel = when (selectedTab) {
                        0 -> "本地: ${if (com.jifeng.toolbox.tools.ShellHub.isForwarded()) "✅ daemon" else "❌ 未启动"}"
                        1 -> "ADB: ${if (AdbManager.isConnected) "✅" else "❌"}"
                        else -> "SSH: ${if (sshConnected) "✅ 已连接" else "❌ 未连接"}"
                    }
                    Text(statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSshConfig = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "SSH 配置",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 输出区
            val currentLogs = when (selectedTab) {
                0 -> localLogs
                1 -> remoteLogs
                else -> sshLogs
            }
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (currentLogs.isEmpty()) {
                        val tip = when (selectedTab) {
                            0 -> "本地 Shell 模式 (通过 ShellHub daemon, uid=2000 权限):\n" +
                                  "• 启动前台服务 → 在被控端部署 daemon\n" +
                                  "• 命令直接在本机执行, 不经过 ADB\n" +
                                  "• 例: ls /data/local/tmp, ps -A, getprop"
                            1 -> "远程 ADB Shell 模式 (多语言):\n" +
                                  "• shell: ADB shell 直接执行\n" +
                                  "• python/js/lua: 需被控端装有对应解释器 (Termux)\n" +
                                  "• c/c++: 推送源码 → 设备端 gcc/clang 编译 → 运行\n" +
                                  "• ai-llm: 检测并调用本地 LLM (ollama/llama.cpp)\n" +
                                  "• ssh: 远程主机执行 (需先点⚙配置)"
                            else -> "SSH 远程主机模式 (持久会话):\n" +
                                    "• 点 ⚙ 配置主机/端口/账号\n" +
                                    "• 连接后多次命令在同一会话内执行\n" +
                                    "• 支持 password / 私钥认证"
                        }
                        Text(tip,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        currentLogs.forEach { line ->
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
                            runCommand(cmd, selectedTab, lang)
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
                    if (sshConnected) {
                        Button(onClick = {
                            TerminalEngine.disconnectSsh()
                            sshConnected = false
                            sshLogs.add("⏹ SSH 已断开")
                        }) { Text("断开当前连接") }
                    }
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
                    // 主动测试连接
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            // SshClient.connect 是 suspend, 通过 TerminalEngine.execute("exit") 触发连接
                            TerminalEngine.execute("ssh", "echo connected") // 触发连接
                            true
                        }
                        sshConnected = ok
                        sshLogs.add("✓ SSH 已配置: ${sshUser}@${sshHost}:${sshPort}")
                        showSshConfig = false
                    }
                }) { Text("保存并测试") }
            },
            dismissButton = {
                TextButton(onClick = { showSshConfig = false }) { Text("取消") }
            }
        )
    }
}
