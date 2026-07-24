package com.jifeng.toolbox.ui.freeze

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.AppFreezer
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.launch

class FreezeComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreezeScreen() }
    }
}

@Composable
private fun FreezeScreen() {
    val scope = rememberCoroutineScope()
    val apps = remember { mutableStateListOf<AppFreezer.AppEntry>() }
    val selected = remember { mutableStateListOf<String>() }
    val logs = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    // 订阅状态
    LaunchedEffect(Unit) {
        AppFreezer.state.collect { state ->
            when (state) {
                is AppFreezer.FreezeState.Loading -> {
                    isBusy = true
                    logs.add(state.message)
                }
                is AppFreezer.FreezeState.AppsLoaded -> {
                    isBusy = false
                    apps.clear()
                    apps.addAll(state.apps)
                    val frozen = state.apps.count { it.isFrozen }
                    logs.add("✓ 加载 ${state.apps.size} 个应用 (已冻结 $frozen)")
                }
                is AppFreezer.FreezeState.ActionDone -> {
                    isBusy = false
                    logs.add("✓ ${state.action}完成: 成功 ${state.success} · 失败 ${state.failed}")
                }
                is AppFreezer.FreezeState.Failed -> {
                    isBusy = false
                    logs.add("✗ ${state.message}")
                }
                else -> {}
            }
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps.toList()
        else apps.filter { it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("智能冻结", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("通过 pm disable-user 冻结应用 (免 Root, ADB 权限即可)。",
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
                                scope.launch { AppFreezer.listThirdPartyApps(serial) }
                            },
                            enabled = !isBusy && AdbManager.isConnected
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 列出应用")
                        }
                        OutlinedButton(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                if (selected.isEmpty()) { logs.add("⚠ 未选择任何应用"); return@OutlinedButton }
                                scope.launch {
                                    val (ok, fail) = AppFreezer.batchFreeze(serial, selected.toList())
                                    selected.clear()
                                    AppFreezer.listThirdPartyApps(serial) // 刷新
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
                                if (selected.isEmpty()) { logs.add("⚠ 未选择任何应用"); return@OutlinedButton }
                                scope.launch {
                                    val (ok, fail) = AppFreezer.batchUnfreeze(serial, selected.toList())
                                    selected.clear()
                                    AppFreezer.listThirdPartyApps(serial) // 刷新
                                }
                            },
                            enabled = !isBusy && selected.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 解冻选中")
                        }
                    }

                    if (!AdbManager.isConnected) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠ 请先连接设备", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索包名") }, singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("共 ${filteredApps.size} 个应用" +
                        if (filteredApps.any { it.isFrozen }) " (${filteredApps.count { it.isFrozen }} 已冻结)" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(filteredApps) { app ->
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
                                Text(
                                    app.packageName +
                                        if (app.isFrozen) "  ❄ 已冻结" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (app.isFrozen) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (app.isFrozen) FontWeight.Medium else FontWeight.Normal
                                )
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
