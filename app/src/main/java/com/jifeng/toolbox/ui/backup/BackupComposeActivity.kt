package com.jifeng.toolbox.ui.backup

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.BackupManager
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.launch

class BackupComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BackupScreen() }
    }
}

@Composable
private fun BackupScreen() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var allPartitions by remember { mutableStateOf<List<String>>(emptyList()) }

    // 用户选择的分区
    val selectedPartitions = remember { mutableStateListOf<String>() }

    // 扫描可用分区
    LaunchedEffect(Unit) {
        if (AdbManager.isConnected) {
            val serial = AdbManager.currentSerial ?: ""
            allPartitions = BackupManager.scanPartitions(serial)
            // 默认选中重要分区
            selectedPartitions.clear()
            selectedPartitions.addAll(allPartitions.filter { it in BackupManager.DEFAULT_PARTITIONS })
        }
    }

    // 订阅备份状态
    LaunchedEffect(Unit) {
        BackupManager.state.collect { state ->
            when (state) {
                is BackupManager.BackupState.Idle -> {}
                is BackupManager.BackupState.Running -> {
                    isRunning = true
                    progressLabel = "[${state.index}/${state.total}] ${state.current} · ${state.phase}"
                    progress = if (state.total > 0) state.index.toFloat() / state.total else 0f
                }
                is BackupManager.BackupState.Done -> {
                    isRunning = false
                    progress = 1f
                    progressLabel = "备份完成"
                    logs.add("✓ 备份完成: ${state.partitionCount} 个分区")
                    logs.add("  文件: ${state.packA}")
                }
                is BackupManager.BackupState.Failed -> {
                    isRunning = false
                    progress = null
                    progressLabel = "备份失败"
                    logs.add("✗ ${state.message}")
                }
            }
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("一键备份分区", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("选择要备份的分区, 默认备份重要分区 (boot/recovery 等)。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 全选/取消全选
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("选择备份分区 (${selectedPartitions.size}/${allPartitions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = {
                                selectedPartitions.clear()
                                selectedPartitions.addAll(allPartitions)
                            }) { Text("全选") }
                            TextButton(onClick = {
                                selectedPartitions.clear()
                            }) { Text("取消全选") }
                            TextButton(onClick = {
                                selectedPartitions.clear()
                                selectedPartitions.addAll(allPartitions.filter { it in BackupManager.DEFAULT_PARTITIONS })
                            }) { Text("默认") }
                        }
                    }

                    // 分区列表
                    if (allPartitions.isEmpty()) {
                        Text("未检测到可用分区, 请确认已连接设备且具有 Root 权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(allPartitions) { part ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = part in selectedPartitions,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedPartitions.add(part)
                                            else selectedPartitions.remove(part)
                                        }
                                    )
                                    Text(
                                        part,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (part in BackupManager.DEFAULT_PARTITIONS)
                                            FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (part in BackupManager.DEFAULT_PARTITIONS) {
                                        Text(" (默认)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val serial = AdbManager.currentSerial ?: ""
                            logs.clear()
                            logs.add("开始备份: serial=$serial, 分区数=${selectedPartitions.size}")
                            val dir = ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath
                            scope.launch {
                                BackupManager.backup(serial, dir, selectedPartitions.toSet())
                            }
                        },
                        enabled = !isRunning && AdbManager.isConnected && selectedPartitions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (isRunning) "备份中..." else "开始备份 (${selectedPartitions.size} 个分区)",
                            fontWeight = FontWeight.Bold)
                    }

                    if (!AdbManager.isConnected) {
                        Text("⚠ 请先连接设备", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
        }
    }
}
