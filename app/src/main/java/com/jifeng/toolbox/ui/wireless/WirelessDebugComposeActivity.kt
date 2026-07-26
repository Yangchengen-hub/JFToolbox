package com.jifeng.toolbox.ui.wireless

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.AppFreezer
import com.jifeng.toolbox.tools.RogueAppRegistry
import com.jifeng.toolbox.tools.ShellHub
import com.jifeng.toolbox.tools.ShellHubService
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LiquidGlassClickableCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WirelessDebugComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WirelessDebugScreen() }
    }
}

/** 已配对设备条目 (内存维护, 进程生命周期内有效)。 */
data class PairedDevice(
    val host: String,
    val port: Int,
    val name: String,
    val pairedAt: Long = System.currentTimeMillis()
)

@Composable
private fun WirelessDebugScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 主动连接
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var status by remember { mutableStateOf("未连接") }
    var isBusy by remember { mutableStateOf(false) }

    // 配对 (Android 11+)
    var pairIp by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }

    // 已配对设备列表 (持久化到 SharedPreferences 简单存储)
    val prefs = remember { ctx.getSharedPreferences("jf_paired", android.content.Context.MODE_PRIVATE) }
    val pairedDevices = remember { mutableStateListOf<PairedDevice>() }
    LaunchedEffect(Unit) { loadPaired(prefs, pairedDevices) }

    // 智能冻结检索
    val rogues = remember { mutableStateListOf<RogueAppRegistry.RogueApp>() }
    val selected = remember { mutableStateListOf<String>() }
    val logs = remember { mutableStateListOf<String>() }
    var activeRegistry by remember { mutableStateOf(RogueAppRegistry.BUILTIN_REGISTRY) }

    // ShellHub 中枢状态
    var shellHubRunning by remember { mutableStateOf(false) }
    var shellHubStatusText by remember { mutableStateOf("未启动") }
    var shellHubAuthTick by remember { mutableStateOf(0) }  // 撤销授权后 ++ 触发刷新

    // 监听 ShellHub 状态变化
    LaunchedEffect(Unit) {
        ShellHub.state.collect { st ->
            when (st) {
                is ShellHub.State.Stopped -> {
                    shellHubRunning = false
                    shellHubStatusText = "未启动"
                }
                is ShellHub.State.Starting -> {
                    shellHubRunning = false
                    shellHubStatusText = "启动中..."
                }
                is ShellHub.State.Running -> {
                    shellHubRunning = true
                    shellHubStatusText = "运行中 (pid=${st.pid})"
                }
                is ShellHub.State.Failed -> {
                    shellHubRunning = false
                    shellHubStatusText = "失败: ${st.msg}"
                }
            }
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("无线调试", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Android 11+ 配对码配对 / active 端口直连 / Shell 中枢 / 智能冻结检索",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            // ---------- 配对卡片 (Android 11+) ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Link, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("配对 (Android 11+ 配对码)", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("被控端: 设置 → 系统 → 开发者选项 → 无线调试 → 「使用配对码配对设备」",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = pairIp, onValueChange = { pairIp = it },
                            modifier = Modifier.weight(2f), label = { Text("IP 地址") },
                            singleLine = true)
                        OutlinedTextField(value = pairPort, onValueChange = { pairPort = it },
                            modifier = Modifier.weight(1f), label = { Text("配对端口") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(value = pairCode, onValueChange = { pairCode = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("6 位配对码") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                    Button(
                        onClick = {
                            if (pairIp.isBlank() || pairPort.isBlank() || pairCode.length != 6) return@Button
                            scope.launch {
                                isPairing = true
                                logs.add("→ 配对 ${pairIp}:${pairPort} (code=${pairCode})")
                                val (ok, msg) = withContext(Dispatchers.IO) {
                                    AdbManager.pair(pairIp, pairPort.toInt(), pairCode)
                                }
                                logs.add(if (ok) "✓ $msg" else "✗ $msg")
                                if (ok) {
                                    pairedDevices.add(PairedDevice(pairIp, pairPort.toInt(), "paired"))
                                    savePaired(prefs, pairedDevices)
                                }
                                isPairing = false
                            }
                        },
                        enabled = !isPairing && pairIp.isNotBlank() && pairPort.isNotBlank() && pairCode.length == 6,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isPairing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text("配对")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 已配对设备列表 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Phonelink, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("已配对设备 (${pairedDevices.size})", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    if (pairedDevices.isEmpty()) {
                        Text("尚无已配对设备, 完成配对后会自动出现在此处",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        pairedDevices.forEach { dev ->
                            LiquidGlassClickableCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 14.dp,
                                padding = 12.dp,
                                onClick = {
                                    ip = dev.host
                                    port = dev.port.toString()
                                    logs.add("已填入 ${dev.host}:${dev.port}, 点击「连接」")
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Wifi, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${dev.host}:${dev.port}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                        Text("名称: ${dev.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = {
                                        pairedDevices.remove(dev)
                                        savePaired(prefs, pairedDevices)
                                    }) { Text("移除") }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- ShellHub 中枢 (Shizuku 风格) ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Hub, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Shell 中枢 (ADB Hub)", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Shizuku 风格: 在被控端部署 daemon (uid 2000 shell), 其他 APP 通过 127.0.0.1:8848 请求 shell 权限时弹出悬浮窗授权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("当前状态: ${shellHubStatusText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (shellHubRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                if (!AdbManager.isConnected) {
                                    logs.add("⚠ ADB 未连接, 无法部署 daemon"); return@Button
                                }
                                scope.launch {
                                    isBusy = true
                                    logs.add("→ 部署并启动 ShellHub daemon...")
                                    val deployed = withContext(Dispatchers.IO) { ShellHub.deploy(serial) }
                                    if (!deployed) {
                                        logs.add("✗ daemon 部署失败"); isBusy = false; return@launch
                                    }
                                    val started = withContext(Dispatchers.IO) { ShellHub.start(serial) }
                                    logs.add(if (started) "✓ Shell 中枢已启动 (监听 127.0.0.1:8848)"
                                              else "✗ daemon 启动超时, oneshot 仍可用")
                                    ctx.startForegroundService(Intent(ctx, ShellHubService::class.java))
                                    logs.add("✓ 通知栏配对入口已挂载")
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && AdbManager.isConnected && !shellHubRunning,
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text(if (isBusy) "启动中..." else "启动中枢")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val serial = AdbManager.currentSerial ?: ""
                                scope.launch {
                                    isBusy = true
                                    withContext(Dispatchers.IO) { ShellHub.stop(serial) }
                                    ctx.stopService(Intent(ctx, ShellHubService::class.java))
                                    logs.add("⏹ Shell 中枢已停止, 通知栏入口已移除")
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && shellHubRunning,
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text(if (isBusy) "停止中..." else "停止中枢")
                            }
                        }
                    }

                    // 通知栏配对入口提示
                    LiquidGlassClickableCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        padding = 10.dp,
                        onClick = {
                            // 重新拉起前台服务, 确保通知栏入口存在
                            ctx.startForegroundService(Intent(ctx, ShellHubService::class.java))
                            logs.add("✓ 通知栏配对入口已重新挂载 (下拉通知栏可见)")
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("通知栏配对入口", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                Text("下拉通知栏 → 「极风工具箱 · Shell 中枢」点击可回到此页",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 已授权应用列表 (可撤销)
                    val authorizedUids = remember(shellHubRunning, shellHubAuthTick) { ShellHub.listAuthorized() }
                    if (authorizedUids.isNotEmpty()) {
                        Text("已授权应用 (${authorizedUids.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        authorizedUids.forEach { uid ->
                            val pkg = remember(uid) { ShellHub.resolvePackage(uid) ?: "uid=$uid" }
                            val label = remember(uid) { ShellHub.resolveLabel(pkg) }
                            LiquidGlassClickableCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 10.dp,
                                padding = 8.dp,
                                onClick = {
                                    ShellHub.revoke(uid)
                                    logs.add("✗ 已撤销 $label ($pkg) 的 shell 授权")
                                    shellHubAuthTick++  // 触发刷新
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.height(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium)
                                        Text(pkg, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("点击撤销",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 直连 active 端口 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Link, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Text("连接 active 端口 (已配对设备)", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = ip, onValueChange = { ip = it },
                            modifier = Modifier.weight(2f), label = { Text("IP 地址") },
                            singleLine = true)
                        OutlinedTextField(value = port, onValueChange = { port = it },
                            modifier = Modifier.weight(1f), label = { Text("active 端口") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val p = port.toIntOrNull() ?: 5555
                                scope.launch {
                                    isBusy = true
                                    status = "连接中 $ip:$p ..."
                                    try {
                                        val ok = withContext(Dispatchers.IO) {
                                            AdbManager.connectTcp(ip, p)
                                        }
                                        status = if (ok) {
                                            "✅ 已连接: $ip:$p (serial=${AdbManager.currentSerial})"
                                        } else "❌ 连接失败: $ip:$p"
                                    } catch (e: Exception) {
                                        status = "❌ 连接失败: ${e.message}"
                                    }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && ip.isNotBlank(),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text("连接")
                            }
                        }
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
            Spacer(Modifier.height(12.dp))

            // ---------- 智能冻结检索 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("智能冻结检索", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("从内置清单 + GitHub 远端刷新匹配被控端已安装包, 生成一键冻结列表。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("当前清单: ${activeRegistry.size} 条",
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
                            enabled = !isBusy && AdbManager.isConnected,
                            modifier = Modifier.height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text(if (isBusy) "检索中..." else "检索冻结列表")
                            }
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
                            enabled = !isBusy,
                            modifier = Modifier.height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text(if (isBusy) "刷新中..." else "刷新在线清单")
                            }
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
                            enabled = !isBusy && selected.isNotEmpty(),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text(if (isBusy) "冻结中..." else "冻结选中 (${selected.size})")
                            }
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
                            enabled = !isBusy && selected.isNotEmpty(),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text(if (isBusy) "解冻中..." else "解冻选中")
                            }
                        }
                    }

                    if (!AdbManager.isConnected) {
                        Text("⚠ 请先连接被控端", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    Text("共 ${rogues.size} 个建议冻结项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    LazyColumn(modifier = Modifier.height(220.dp)) {
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

// ---------- 已配对设备持久化 ----------

private fun loadPaired(prefs: android.content.SharedPreferences, list: SnapshotStateList<PairedDevice>) {
    val set = prefs.getStringSet("paired", emptySet()) ?: emptySet()
    set.forEach { s ->
        val parts = s.split("|")
        if (parts.size >= 4) {
            list.add(PairedDevice(
                host = parts[0],
                port = parts[1].toIntOrNull() ?: 5555,
                name = parts[2],
                pairedAt = parts[3].toLongOrNull() ?: 0L
            ))
        }
    }
}

private fun savePaired(prefs: android.content.SharedPreferences, list: List<PairedDevice>) {
    val set = list.map { "${it.host}|${it.port}|${it.name}|${it.pairedAt}" }.toSet()
    prefs.edit().putStringSet("paired", set).apply()
}
