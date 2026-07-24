package com.jifeng.toolbox.ui.installer

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
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.ApkScanner
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.launch

class InstallerComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { InstallerScreen() }
    }
}

@Composable
private fun InstallerScreen() {
    val scope = rememberCoroutineScope()
    val apks = remember { mutableStateListOf<ApkScanner.ApkEntry>() }
    val selectedPaths = remember { mutableStateListOf<String>() }
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }

    // 订阅扫描状态
    LaunchedEffect(Unit) {
        ApkScanner.state.collect { state ->
            when (state) {
                is ApkScanner.ScanState.Idle -> {}
                is ApkScanner.ScanState.Scanning -> {
                    isScanning = true
                    progressLabel = "扫描中: ${state.currentDir}"
                    progress = null
                }
                is ApkScanner.ScanState.Done -> {
                    isScanning = false
                    progress = null
                    progressLabel = null
                    apks.clear()
                    apks.addAll(state.apks)
                    logs.add("✓ 扫描完成: ${state.apks.size} 个 APK")
                }
                is ApkScanner.ScanState.Failed -> {
                    isScanning = false
                    progressLabel = null
                    logs.add("✗ ${state.message}")
                }
            }
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("全能安装器", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("扫描被控设备全量 APK, 列表展示并一键批量安装。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                logs.clear()
                                logs.add("扫描 /sdcard 全目录 APK ...")
                                scope.launch {
                                    isScanning = true
                                    apks.clear()
                                    val result = ApkScanner.scanRemote(serial, "/sdcard")
                                    logs.add("发现 ${result.size} 个 APK 文件")
                                }
                            },
                            enabled = !isScanning && AdbManager.isConnected
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 扫描设备 APK")
                        }
                        Button(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                val toInstall = apks.filter { it.path in selectedPaths }
                                if (toInstall.isEmpty()) {
                                    logs.add("⚠ 未选择任何 APK")
                                    return@Button
                                }
                                logs.clear()
                                logs.add("批量安装 ${toInstall.size} 个 APK ...")
                                scope.launch {
                                    isInstalling = true
                                    progress = 0f
                                    val (ok, fail) = ApkScanner.batchInstallRemote(serial, toInstall.map { it.path })
                                    progress = 1f
                                    progressLabel = "完成"
                                    logs.add("✓ 安装成功: $ok · 失败: $fail")
                                    isInstalling = false
                                }
                            },
                            enabled = !isInstalling && selectedPaths.isNotEmpty() && AdbManager.isConnected
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 安装选中 (${selectedPaths.size})")
                        }
                    }
                    if (!AdbManager.isConnected) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠ 请先连接设备", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))

                    if (apks.isEmpty() && !isScanning) {
                        Text("点击「扫描设备 APK」开始全量扫描 /sdcard 目录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp))
                    }

                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(apks) { apk ->
                            val isSelected = apk.path in selectedPaths
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedPaths.remove(apk.path)
                                        else selectedPaths.add(apk.path)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedPaths.add(apk.path) else selectedPaths.remove(apk.path)
                                    }
                                )
                                Column {
                                    Text(apk.fileName, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium)
                                    Text("${apk.sizeFormatted} · ${apk.path}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
        }
    }
}
