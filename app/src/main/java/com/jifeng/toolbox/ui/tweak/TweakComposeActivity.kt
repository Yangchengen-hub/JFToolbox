package com.jifeng.toolbox.ui.tweak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.core.RootDetector
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }
    var rootStatus by remember { mutableStateOf<RootDetector.RootStatus?>(null) }
    var pendingModuleName by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    // 隐藏模块安装包选择器 (.zip)
    val moduleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val moduleName = pendingModuleName
        if (uri == null || moduleName == null) {
            pendingModuleName = null
            return@rememberLauncherForActivityResult
        }
        val tmp = File(ctx.cacheDir, "jf_module_${System.currentTimeMillis()}.zip")
        val ok = try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.exists() && tmp.length() > 0
        } catch (e: Exception) {
            logs.add("✗ 读取模块文件失败: ${e.message}")
            false
        }
        if (!ok) {
            logs.add("✗ 模块文件复制失败")
            pendingModuleName = null
            return@rememberLauncherForActivityResult
        }
        logs.add("已选择 ${moduleName} 模块包: ${tmp.name} (${tmp.length() / 1024} KB)")
        scope.launch {
            isBusy = true
            // 若未检测过 Root, 自动检测一次
            val rs = rootStatus ?: detectRoot(logs).also { rootStatus = it }
            installHideModule(moduleName, tmp.absolutePath, rs, logs)
            isBusy = false
            pendingModuleName = null
        }
    }

    // 月虹脚本选择器 (用 */* 因 SAF 中 .sh mime 不稳定)
    val scriptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tmp = File(ctx.cacheDir, "jf_yuehong_${System.currentTimeMillis()}.sh")
        val ok = try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.exists() && tmp.length() > 0
        } catch (e: Exception) {
            logs.add("✗ 读取脚本失败: ${e.message}")
            false
        }
        if (!ok) {
            logs.add("✗ 脚本文件复制失败")
            return@rememberLauncherForActivityResult
        }
        logs.add("已选择脚本: ${tmp.name} (${tmp.length()} bytes)")
        scope.launch {
            isBusy = true
            runYuehongScript(tmp.absolutePath, rootStatus, logs)
            isBusy = false
        }
    }

    JFScaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("玩机工具", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

            // ---- Root 状态 ----
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("Root 状态:", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    val rs = rootStatus
                    Text(
                        if (!AdbManager.isConnected) "未连接设备"
                        else if (rs == null) "未检测"
                        else if (rs.hasRoot) "${rs.manager.displayName}${if (rs.version.isNotBlank()) " v${rs.version}" else ""}"
                        else "无 Root",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (rs?.hasRoot == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            isBusy = true
                            rootStatus = detectRoot(logs)
                            isBusy = false
                        }
                    }, enabled = !isBusy && AdbManager.isConnected) {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("检测")
                    }
                }
            }

            // ---- 一键隐藏环境 ----
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("一键隐藏 Root 环境", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("选择本地已下载的 .zip 模块包, 自动推送到 /data/adb/modules_install/ " +
                            "并调用对应管理器 (Magisk / KernelSU / APatch) 安装。需先检测到 Root。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("推荐模块 (点击「安装」选择本地 zip):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    HIDE_MODULES.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("• ${m.name} — ${m.desc}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("  作者: ${m.author} · 来源: ${m.source}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = {
                                if (!AdbManager.isConnected) {
                                    logs.add("⚠ 请先连接设备")
                                    return@OutlinedButton
                                }
                                pendingModuleName = m.name
                                moduleLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }, enabled = !isBusy) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null,
                                    modifier = Modifier.height(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("安装")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ---- 月虹检测脚本 ----
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("月虹检测脚本", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("选择本地已下载的「月虹」检测 .sh 脚本, 推送到 /data/local/tmp/yuehong_check.sh, " +
                            "chmod 755 后执行, 输出写入下方日志。有 Root 时以 su 0 运行以便读取 /data/adb。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("诚实说明: 酷安「月虹」脚本下载需酷安账号, 无公开 API, 本工具不代下载, 仅提供执行入口。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (!AdbManager.isConnected) {
                                logs.add("⚠ 请先连接设备")
                                return@Button
                            }
                            scriptLauncher.launch(arrayOf("*/*"))
                        }, enabled = !isBusy) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("选择并运行脚本")
                        }
                        OutlinedButton(onClick = {
                            openUrl(ctx, "https://www.coolapk.com/search?q=月虹检测")
                        }) {
                            Text("去酷安下载最新脚本")
                        }
                    }
                }
            }

            // ---- 系统更新禁用 ----
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("系统更新禁用 (OTA 屏蔽)", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Text("全品牌通用 (小米/华为/OPPO/vivo/三星等), 通过 pm disable-user 禁用 OTA 相关组件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val rs = rootStatus
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (rs?.hasRoot == true)
                                "已 Root: 将写入 /data/adb/service.d/jf_ota_block.sh, 每次开机自动重新禁用 (永久)。"
                            else if (rs != null)
                                "未 Root: 仅临时禁用, 重启后会恢复, 建议获取 Root 后使用永久禁用。"
                            else
                                "请先检测 Root 状态。未 Root 时仅 pm disable-user 临时禁用, 重启后恢复。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("诚实说明: 「永久禁用」指每次开机自动重新执行 pm disable-user, 不是修改系统分区。" +
                                "禁用后可能影响正常系统更新, 如需恢复请点击「恢复 OTA」。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (!AdbManager.isConnected) {
                                logs.add("⚠ 请先连接设备")
                                return@Button
                            }
                            scope.launch {
                                isBusy = true
                                // 若未检测过 Root, 自动检测一次
                                val rs2 = rootStatus ?: detectRoot(logs).also { rootStatus = it }
                                disableOta(rs2, ctx.cacheDir, logs)
                                isBusy = false
                            }
                        }, enabled = !isBusy) {
                            Icon(Icons.Default.Block, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("一键禁用 OTA")
                        }
                        OutlinedButton(onClick = {
                            if (!AdbManager.isConnected) {
                                logs.add("⚠ 请先连接设备")
                                return@OutlinedButton
                            }
                            scope.launch {
                                isBusy = true
                                restoreOta(logs)
                                isBusy = false
                            }
                        }, enabled = !isBusy) {
                            Text("恢复 OTA")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            LogTerminal(logs, null, null, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------- 实现函数 ----------

/** 检测设备 Root 状态, 返回 RootStatus 或 null (未连接时)。 */
private suspend fun detectRoot(logs: MutableList<String>): RootDetector.RootStatus? {
    if (!AdbManager.isConnected) {
        logs.add("✗ 未连接设备, 请先通过 ADB 连接")
        return null
    }
    val serial = AdbManager.currentSerial ?: run {
        logs.add("✗ 未获取到设备序列号")
        return null
    }
    logs.add("⏳ 检测设备 Root 状态...")
    val rs = withContext(Dispatchers.IO) { RootDetector.detect(serial) }
    if (rs.hasRoot) {
        logs.add("✓ 检测到 ${rs.manager.displayName}${if (rs.version.isNotBlank()) " v${rs.version}" else ""}")
        logs.add("  模块路径: ${rs.moduleBasePath}")
    } else {
        logs.add("✓ 设备未 Root")
    }
    return rs
}

/** 安装 Root 隐藏模块: push 到 /data/adb/modules_install/ 后调用对应管理器安装。 */
private suspend fun installHideModule(
    moduleName: String,
    localZip: String,
    rootStatus: RootDetector.RootStatus?,
    logs: MutableList<String>
) {
    val serial = AdbManager.currentSerial ?: run {
        logs.add("✗ 未连接设备"); File(localZip).delete(); return
    }
    if (!AdbManager.isConnected) {
        logs.add("✗ 未连接设备"); File(localZip).delete(); return
    }

    val rs = rootStatus ?: run {
        logs.add("✗ 未检测到 Root 状态, 请先点击「检测」")
        File(localZip).delete()
        return
    }
    if (!rs.hasRoot) {
        logs.add("✗ 设备未 Root, 无法安装隐藏模块")
        logs.add("  Root 隐藏模块需要 Magisk / KernelSU / APatch 环境支持")
        File(localZip).delete()
        return
    }

    // 文件名净化, 避免特殊字符
    val safeName = moduleName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val remoteZip = "/data/adb/modules_install/${safeName}.zip"

    logs.add("⏳ 准备模块安装目录 /data/adb/modules_install ...")
    withContext(Dispatchers.IO) {
        AdbManager.shell(serial, "su 0 mkdir -p /data/adb/modules_install")
    }
    logs.add("⏳ 推送模块包到 $remoteZip ...")
    val pushed = withContext(Dispatchers.IO) { AdbManager.push(serial, localZip, remoteZip) }
    if (!pushed) {
        logs.add("✗ 推送失败, 请检查 ADB 连接和文件")
        File(localZip).delete()
        return
    }
    logs.add("✓ 推送完成")

    val installCmd = when (rs.manager) {
        RootDetector.RootManager.MAGISK -> "su -c 'magisk --install-module $remoteZip'"
        RootDetector.RootManager.KERNELSU -> "su -c 'ksud module install $remoteZip'"
        RootDetector.RootManager.APATCH -> "su -c 'apd module install $remoteZip'"
        RootDetector.RootManager.GENERIC_ROOT -> "su -c 'magisk --install-module $remoteZip'"
        RootDetector.RootManager.NONE -> {
            logs.add("✗ 未 Root, 无法安装"); File(localZip).delete(); return
        }
    }
    logs.add("⏳ 执行安装: $installCmd")
    val result = withContext(Dispatchers.IO) { AdbManager.shell(serial, installCmd) }
    Logger.i("HideModule", "install $moduleName -> $result")
    if (result.isNullOrBlank()) {
        logs.add("✓ 安装命令已执行 (无输出, 通常表示成功)")
    } else {
        logs.add("── 安装输出 ──")
        val lines = result.lineSequence().toList()
        lines.take(20).forEach { logs.add("  $it") }
        if (lines.size > 20) logs.add("  ... (输出已截断, 共 ${lines.size} 行)")
        logs.add("── 输出结束 ──")
        // 简单错误关键字判断
        if (result.contains("No such file", ignoreCase = true) ||
            result.contains("not found", ignoreCase = true) ||
            result.contains("Permission denied", ignoreCase = true)) {
            logs.add("⚠ 安装可能失败, 请检查模块与管理器兼容性")
        } else {
            logs.add("✓ $moduleName 安装完成, 重启后生效")
        }
    }

    // 清理临时安装包和本地缓存
    withContext(Dispatchers.IO) { AdbManager.shell(serial, "su 0 rm -f $remoteZip") }
    File(localZip).delete()
}

/** 推送并执行月虹检测脚本, 输出写入日志。 */
private suspend fun runYuehongScript(
    localScript: String,
    rootStatus: RootDetector.RootStatus?,
    logs: MutableList<String>
) {
    val serial = AdbManager.currentSerial ?: run {
        logs.add("✗ 未连接设备"); File(localScript).delete(); return
    }
    if (!AdbManager.isConnected) {
        logs.add("✗ 未连接设备"); File(localScript).delete(); return
    }

    val remote = "/data/local/tmp/yuehong_check.sh"
    logs.add("⏳ 推送脚本到 $remote ...")
    val pushed = withContext(Dispatchers.IO) { AdbManager.push(serial, localScript, remote) }
    if (!pushed) {
        logs.add("✗ 脚本推送失败")
        File(localScript).delete()
        return
    }
    logs.add("✓ 推送完成, 设置可执行权限 (chmod 755)")
    withContext(Dispatchers.IO) { AdbManager.shell(serial, "chmod 755 $remote") }

    // 有 Root 时用 su 0 执行, 以便读取 /data/adb 等受限路径
    val rs = rootStatus ?: detectRoot(logs).also { /* 不更新 UI 状态, 仅本地用 */ }
    val cmd = if (rs?.hasRoot == true) "su 0 sh $remote 2>&1" else "sh $remote 2>&1"
    logs.add("⏳ 执行: $cmd")
    val result = withContext(Dispatchers.IO) { AdbManager.shell(serial, cmd) }
    Logger.i("Yuehong", "result -> $result")
    if (result.isNullOrBlank()) {
        logs.add("⚠ 脚本无输出 (可能执行失败或脚本为空)")
    } else {
        logs.add("── 脚本输出 ──")
        val lines = result.lineSequence().toList()
        lines.take(50).forEach { logs.add(it) }
        if (lines.size > 50) logs.add("... (输出已截断, 共 ${lines.size} 行)")
        logs.add("── 输出结束 ──")
    }

    // 清理本地缓存 (远端脚本保留, 方便下次重跑)
    File(localScript).delete()
}

/** 禁用 OTA: 无 Root 仅 pm disable-user 临时禁用; 有 Root 额外写入 /data/adb/service.d/ 开机自启脚本。 */
private suspend fun disableOta(
    rootStatus: RootDetector.RootStatus?,
    cacheDir: File,
    logs: MutableList<String>
) {
    val serial = AdbManager.currentSerial ?: run {
        logs.add("✗ 未连接设备"); return
    }
    if (!AdbManager.isConnected) {
        logs.add("✗ 未连接设备"); return
    }

    // 去重后的全量包列表
    val allPkgs = OTA_PACKAGES.values.flatten().distinct()
    logs.add("⏳ 禁用 ${allPkgs.size} 个 OTA 相关包 (pm disable-user)...")
    var success = 0
    allPkgs.forEach { pkg ->
        val r = withContext(Dispatchers.IO) { AdbManager.shell(serial, "pm disable-user $pkg 2>&1") }
        if (r?.contains("true") == true || r?.contains("disabled") == true) success++
    }
    logs.add("✓ pm disable-user 完成, 成功 $success / ${allPkgs.size} (未安装的包自动跳过)")

    // 有 Root: 写入永久禁用脚本到 /data/adb/service.d/
    // 该目录被 Magisk / KernelSU / APatch 兼容, 开机 service 阶段执行
    if (rootStatus?.hasRoot == true) {
        logs.add("⏳ 写入永久禁用脚本 /data/adb/service.d/jf_ota_block.sh ...")
        val scriptContent = buildString {
            append("#!/system/bin/sh\n")
            append("# 极风工具箱 - OTA 永久禁用服务 (开机自动执行)\n")
            append("# 生成时间: ").append(System.currentTimeMillis()).append("\n")
            append("for pkg in ${allPkgs.joinToString(" ")}; do\n")
            append("    pm disable-user \$pkg 2>/dev/null\n")
            append("    pm disable \$pkg 2>/dev/null\n")
            append("done\n")
        }

        val tmp = File(cacheDir, "jf_ota_block_${System.currentTimeMillis()}.sh")
        try {
            tmp.writeText(scriptContent)
        } catch (e: Exception) {
            logs.add("✗ 生成脚本失败: ${e.message}")
            return
        }

        val tmpRemote = "/data/local/tmp/jf_ota_block.sh"
        val remote = "/data/adb/service.d/jf_ota_block.sh"

        // 推送到 /data/local/tmp (shell 用户可写), 再用 su 拷贝到 service.d
        val pushed = withContext(Dispatchers.IO) { AdbManager.push(serial, tmp.absolutePath, tmpRemote) }
        tmp.delete()
        if (!pushed) {
            logs.add("✗ 推送脚本失败, 仅本次禁用生效")
            return
        }

        withContext(Dispatchers.IO) {
            AdbManager.shell(serial, "su 0 mkdir -p /data/adb/service.d")
        }
        withContext(Dispatchers.IO) {
            AdbManager.shell(serial, "su 0 cp $tmpRemote $remote")
        }
        withContext(Dispatchers.IO) {
            AdbManager.shell(serial, "su 0 chmod 755 $remote")
        }
        withContext(Dispatchers.IO) {
            AdbManager.shell(serial, "rm -f $tmpRemote")
        }

        // 验证脚本写入成功
        val verify = withContext(Dispatchers.IO) {
            AdbManager.shell(serial, "su 0 ls -la $remote 2>/dev/null")
        }
        if (verify?.contains(remote) == true) {
            logs.add("✓ 永久禁用脚本写入成功")
            logs.add("  $remote")
            verify.lineSequence().firstOrNull { it.contains(remote) }?.let { logs.add("  $it") }
            logs.add("✓ 下次开机将自动重新禁用 OTA 相关包")
        } else {
            logs.add("⚠ 永久禁用脚本写入失败 (su 权限拒绝?), 仅本次禁用生效")
            Logger.w("OTA", "verify failed: $verify")
        }
    } else {
        logs.add("⚠ 未 Root, 仅临时禁用, 重启后会恢复")
        logs.add("  建议获取 Root 后使用永久禁用 (写入 /data/adb/service.d/ 开机自启脚本)")
    }
}

/** 恢复 OTA: 删除永久禁用脚本 + pm enable 全部包。 */
private suspend fun restoreOta(logs: MutableList<String>) {
    val serial = AdbManager.currentSerial ?: run {
        logs.add("✗ 未连接设备"); return
    }
    if (!AdbManager.isConnected) {
        logs.add("✗ 未连接设备"); return
    }

    val remote = "/data/adb/service.d/jf_ota_block.sh"
    logs.add("⏳ 删除永久禁用脚本 $remote ...")
    withContext(Dispatchers.IO) {
        AdbManager.shell(serial, "su 0 rm -f $remote")
    }
    val verify = withContext(Dispatchers.IO) {
        AdbManager.shell(serial, "su 0 ls $remote 2>/dev/null")
    }
    if (verify.isNullOrBlank()) {
        logs.add("✓ 永久禁用脚本已删除 (或本来就不存在)")
    } else {
        logs.add("⚠ 脚本删除失败, 请手动 su rm $remote")
    }

    val allPkgs = OTA_PACKAGES.values.flatten().distinct()
    logs.add("⏳ 恢复 ${allPkgs.size} 个 OTA 相关包 (pm enable)...")
    var restored = 0
    allPkgs.forEach { pkg ->
        val r = withContext(Dispatchers.IO) { AdbManager.shell(serial, "pm enable $pkg 2>&1") }
        if (r?.contains("true") == true || r?.contains("enabled") == true) restored++
    }
    logs.add("✓ 已尝试恢复 $restored / ${allPkgs.size} 个包 (未安装的包自动跳过)")
}

private fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}
