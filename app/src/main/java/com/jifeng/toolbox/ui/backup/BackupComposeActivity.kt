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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var includeLarge by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }

    // 订阅备份状态
    LaunchedEffect(Unit) {
        BackupManager.state.collect { state ->
            when (state) {
                is BackupManager.BackupState.Idle -> {}
                is BackupManager.BackupState.Running -> {
                    isRunning = true
                    progressLabel = "[$state.index/$state.total] ${state.current} · ${state.phase}"
                    progress = if (state.total > 0) state.index.toFloat() / state.total else 0f
                }
                is BackupManager.BackupState.Done -> {
                    isRunning = false
                    progress = 1f
                    progressLabel = "备份完成"
                    logs.add("✓ 备份完成: ${state.partitionCount} 个分区")
                    logs.add("  A 包: ${state.packA}")
                    state.packB?.let { logs.add("  B 包: $it") }
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
            Text("Root 环境下提取所有分区 (boot, system, vendor 等), 打包为 ZIP。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("前置: 被控端需 Root (Magisk / KernelSU / APatch)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A 包: boot/dtbo/vbmeta/recovery 等关键分区",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("B 包: system/vendor/product 等大分区 (可选, 耗时较长)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeLarge, onCheckedChange = { includeLarge = it })
                        Text("同时备份大分区 (B 包)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = {
                            val serial = AdbManager.currentSerial ?: ""
                            logs.clear()
                            logs.add("开始备份: serial=$serial, includeLarge=$includeLarge")
                            val dir = ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath
                            scope.launch {
                                BackupManager.backupAll(serial, dir, includeLarge)
                            }
                        },
                        enabled = !isRunning && AdbManager.isConnected,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (isRunning) "备份中..." else "开始备份全部分区", fontWeight = FontWeight.Bold)
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
