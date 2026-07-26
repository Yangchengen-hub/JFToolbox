package com.jifeng.toolbox.ui.wireless

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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.AppFreezer
import com.jifeng.toolbox.tools.RogueAppRegistry
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WirelessDebugComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WirelessDebugScreen() }
    }
}

@Composable
private fun WirelessDebugScreen() {
    val scope = rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var status by remember { mutableStateOf("未连接") }

    // 智能冻结检索状态
    val rogues = remember { mutableStateListOf<RogueAppRegistry.RogueApp>() }
    val selected = remember { mutableStateListOf<String>() }
    val logs = remember { mutableStateListOf<String>() }
    var isBusy by remember { mutableStateOf(false) }
    // 当前生效的清单 (远端刷新后会覆盖)
    var activeRegistry by remember { mutableStateOf(RogueAppRegistry.BUILTIN_REGISTRY) }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("无线调试", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("通过无线 ADB 连接, 无需 OTG 线。需被控端已开启无线调试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Wifi, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("被控端地址", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = ip, onValueChange = { ip = it },
                            modifier = Modifier.weight(2f), label = { Text("IP 地址") },
                            singleLine = true)
                        OutlinedTextField(value = port, onValueChange = { port = it },
                            modifier = Modifier.weight(1f), label = { Text("端口") },
                            singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val p = port.toIntOrNull() ?: 5555
                            scope.launch {
                                status = "连接中 $ip:$p ..."
                                try {
                                    val ok = withContext(Dispatchers.IO) {
                                        AdbManager.connectTcp(ip, p)
                                    }
                                    status = if (ok) {
                                        "✅ 已连接: $ip:$p (serial=${AdbManager.currentSerial})"
                                    } else {
                                        "❌ 连接失败: $ip:$p"
                                    }
                                } catch (e: Exception) {
                                    status = "❌ 连接失败: ${e.message}"
                                }
                            }
                        }) { Text("连接") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AdbManager.disconnect() }
                                status = "已断开"
                            }
                        }) { Text("断开") }
                    }
                    Text("状态: $status", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------- 智能冻结检索卡片 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("智能冻结检索", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("从内置清单 + GitHub 远端刷新匹配被控端已安装包, 生成一键冻结列表。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("当前清单: ${activeRegistry.size} 条 (内置 ${RogueAppRegistry.BUILTIN_REGISTRY.size} + 缓存/远端)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                if (!AdbManager.isConnected) {
                                    logs.add("⚠ ADB 未连接"); return@Button
                                }
                                logs.clear()
                                logs.add("→ 拉取被控端第三方包列表...")
                                scope.launch {
                                    isBusy = true
                                    val installed = withContext(Dispatchers.IO) {
                                        AdbManager.shell(serial, "pm list packages -3")
                                            ?.lines()
                                            ?.filter { it.startsWith("package:") }
                                            ?.map { it.removePrefix("package:").trim() }
                                            ?.filter { it.isNotBlank() }
                                            ?: emptyList()
                                    }
                                    logs.add("  发现 ${installed.size} 个第三方包")
                                    val matched = RogueAppRegistry.match(installed, activeRegistry)
                                    rogues.clear()
                                    rogues.addAll(matched)
                                    selected.clear()
                                    logs.add("✓ 匹配到 ${matched.size} 个建议冻结项")
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && AdbManager.isConnected
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 检索冻结列表")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    logs.add("→ 从 GitHub 刷新在线清单...")
                                    val remote = withContext(Dispatchers.IO) {
                                        RogueAppRegistry.refreshFromRemote()
                                    }
                                    activeRegistry = remote
                                    logs.add("✓ 清单已更新: ${remote.size} 条")
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 刷新在线清单")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                if (selected.isEmpty()) {
                                    logs.add("⚠ 未选择任何项"); return@Button
                                }
                                val targets = rogues.filter { it.packageName in selected }
                                scope.launch {
                                    isBusy = true
                                    logs.add("→ 冻结 ${targets.size} 个应用...")
                                    val (ok, fail) = AppFreezer.batchFreezeRogue(serial, targets)
                                    logs.add("✓ 冻结完成: 成功 $ok · 失败 $fail")
                                    selected.clear()
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && selected.isNotEmpty()
                        ) {
                            Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 冻结选中 (${selected.size})")
                        }
                        OutlinedButton(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                if (selected.isEmpty()) {
                                    logs.add("⚠ 未选择任何项"); return@OutlinedButton
                                }
                                scope.launch {
                                    isBusy = true
                                    logs.add("→ 解冻 ${selected.size} 个应用...")
                                    val (ok, fail) = AppFreezer.batchUnfreeze(serial, selected.toList())
                                    logs.add("✓ 解冻完成: 成功 $ok · 失败 $fail")
                                    selected.clear()
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && selected.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 解冻选中")
                        }
                    }

                    if (!AdbManager.isConnected) {
                        Text("⚠ 请先连接被控端", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    Text("共 ${rogues.size} 个建议冻结项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(rogues) { app ->
                            val isSelected = app.packageName in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selected.remove(app.packageName)
                                        else selected.add(app.packageName)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selected.add(app.packageName) else selected.remove(app.packageName)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("[${app.category}] · [${app.severity}] · ${app.reason}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = severityColor(app.severity))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LogTerminal(logs, null, null, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 严重度对应的展示色。 */
@Composable
private fun severityColor(severity: RogueAppRegistry.Severity) = when (severity) {
    RogueAppRegistry.Severity.CRITICAL -> MaterialTheme.colorScheme.error
    RogueAppRegistry.Severity.HIGH -> MaterialTheme.colorScheme.error
    RogueAppRegistry.Severity.MEDIUM -> MaterialTheme.colorScheme.tertiary
    RogueAppRegistry.Severity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}
