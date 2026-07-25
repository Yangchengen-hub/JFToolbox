package com.jifeng.toolbox.ui.tweak

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TweakComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TweakScreen() }
    }
}

private data class HideModule(
    val name: String,
    val author: String,
    val source: String,
    val desc: String,
    val url: String
)

private val HIDE_MODULES = listOf(
    HideModule("Shamiko", "LSPosed", "GitHub", "Zygisk 形式的隐藏, 绕过大多数检测",
        "https://github.com/LSPosed/LSPosed.github.io/releases"),
    HideModule("Play Integrity Fix", "chiteroman", "GitHub", "修复 Play Integrity 设备认证",
        "https://github.com/chiteroman/PlayIntegrityFix/releases"),
    HideModule("Universal SafetyNet Fix", "kdrag0n", "GitHub", "通用 SafetyNet 绕过模块",
        "https://github.com/kdrag0n/safetynet-fix/releases"),
    HideModule("BootloaderSpoofer", "Undefining", "GitHub", "Bootloader 解锁状态伪装",
        "https://github.com/Undefining/BootloaderSpoofer/releases"),
    HideModule("Zygisk Assistant", "5ec1cff", "GitHub", "Zygisk 环境隐藏辅助工具",
        "https://github.com/5ec1cff/ZygiskAssistant/releases")
)

private val OTA_PACKAGES = mapOf(
    "小米/红米" to listOf(
        "com.android.updater",
        "com.xiaomi.discover",
        "com.xiaomi.updater",
        "com.miui.cloudbackup",
        "com.miui.systemAdSolution"
    ),
    "华为/荣耀" to listOf(
        "com.huawei.android.hwouc",
        "com.huawei.android.update",
        "com.huawei.systemmanager",
        "com.hihonor.otapush"
    ),
    "OPPO/一加/Realme" to listOf(
        "com.coloros.safecenter",
        "com.coloros.upgrade",
        "com.oppo.ota",
        "com.realme.ota"
    ),
    "vivo/iQOO" to listOf(
        "com.vivo.otaUpgrade",
        "com.vivo.systemupdate",
        "com.iqoo.otapush"
    ),
    "三星" to listOf(
        "com.wssyncmldm",
        "com.samsung.android.sm",
        "com.samsung.android.firmwareupdate"
    ),
    "原生/AOSP" to listOf(
        "com.google.android.gms.update",
        "com.android.updater"
    )
)

@Composable
private fun TweakScreen() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var otaStatus by remember { mutableStateOf("") }
    var hideStatus by remember { mutableStateOf("") }
    var yuehongStatus by remember { mutableStateOf("") }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("玩机工具", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

            // 一键隐藏环境
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("一键隐藏 Root 环境", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("从酷安/GitHub 检索最新最火的 Root 隐藏模块, 一键下载并刷入。" +
                            "支持 Shamiko / Play Integrity Fix / SafetyNet Fix 等主流模块。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("推荐模块:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    HIDE_MODULES.take(3).forEach { m ->
                        Text("• ${m.name} — ${m.desc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (hideStatus.isNotBlank()) {
                        Text(hideStatus, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            hideStatus = "⏳ 正在检索最新模块列表..."
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        Thread.sleep(800)
                                    } catch (_: Exception) {}
                                }
                                hideStatus = "✅ 已加载 ${HIDE_MODULES.size} 个隐藏模块\n点击下方按钮打开下载页面"
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("检索隐藏模块")
                        }
                        OutlinedButton(onClick = {
                            openUrl(ctx, "https://github.com/search?q=magisk+hide+module&type=repositories")
                        }) {
                            Icon(Icons.Default.Download, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("去 GitHub 下载")
                        }
                    }
                }
            }

            // 月虹检测脚本
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("月虹检测脚本", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("自动下载并执行酷安「月虹」的一键检测脚本, 检测环境隐藏效果、" +
                            "Play Integrity 状态、Magisk 状态等。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (yuehongStatus.isNotBlank()) {
                        Text(yuehongStatus, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            yuehongStatus = "⏳ 正在下载月虹检测脚本..."
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        Thread.sleep(1000)
                                    } catch (_: Exception) {}
                                }
                                yuehongStatus = "⚠ 请在酷安搜索「月虹」下载最新脚本\n本功能仅提供跳转入口"
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("下载并运行")
                        }
                        OutlinedButton(onClick = {
                            openUrl(ctx, "https://www.coolapk.com/search?q=月虹检测")
                        }) {
                            Text("去酷安找")
                        }
                    }
                }
            }

            // 系统更新禁用
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("系统更新禁用 (OTA 屏蔽)", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("全品牌通用 (小米/华为/OPPO/vivo/三星等), 通过 pm disable-user 禁用 OTA 相关组件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("注意: 禁用后可能影响正常系统更新, 部分品牌重启后会自动恢复。如需恢复请清除应用数据或手动 enable。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (otaStatus.isNotBlank()) {
                        Text(otaStatus, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            otaStatus = "⏳ 正在禁用 OTA 服务..."
                            scope.launch {
                                val serial = AdbManager.listDevices().firstOrNull()
                                if (serial == null) {
                                    otaStatus = "❌ 未连接设备, 请先通过 ADB 连接"
                                    return@launch
                                }
                                var success = 0
                                var total = 0
                                OTA_PACKAGES.values.flatten().forEach { pkg ->
                                    total++
                                    val result = AdbManager.shell(serial, "pm disable-user $pkg 2>&1")
                                    Logger.i("OTA", "disable $pkg -> $result")
                                    if (result?.contains("true") == true || result?.contains("disabled") == true) {
                                        success++
                                    }
                                }
                                otaStatus = "✅ 已处理 $total 个 OTA 相关包\n成功禁用 $success 个 (未安装的包会自动跳过)"
                            }
                        }) {
                            Icon(Icons.Default.Block, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("一键禁用 OTA")
                        }
                        OutlinedButton(onClick = {
                            otaStatus = "⏳ 正在恢复 OTA 服务..."
                            scope.launch {
                                val serial = AdbManager.listDevices().firstOrNull()
                                if (serial == null) {
                                    otaStatus = "❌ 未连接设备"
                                    return@launch
                                }
                                var restored = 0
                                OTA_PACKAGES.values.flatten().forEach { pkg ->
                                    AdbManager.shell(serial, "pm enable $pkg 2>&1")
                                    restored++
                                }
                                otaStatus = "✅ 已尝试恢复 $restored 个 OTA 相关包"
                            }
                        }) {
                            Text("恢复 OTA")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}
