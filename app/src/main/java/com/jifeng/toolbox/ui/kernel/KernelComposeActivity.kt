package com.jifeng.toolbox.ui.kernel

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.SafetyChecker
import com.jifeng.toolbox.tools.KernelFlasher
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.usb.UsbDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class KernelComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KernelScreen() }
    }
}

/** 风险卡片展示数据 (与 RiskLevel 一一对应, 供高亮匹配)。 */
private data class RiskCardInfo(
    val level: KernelFlasher.RiskLevel,
    val name: String,
    val desc: String,
    val color: Color
)

@Composable
private fun KernelScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 文件选择
    var pickedPath by remember { mutableStateOf<String?>(null) }
    var pickedName by remember { mutableStateOf("") }

    // 分区选择 (boot / init_boot / vendor_boot / dtbo / vbmeta / 自定义)
    val partitionOptions = listOf("boot", "init_boot", "vendor_boot", "dtbo", "vbmeta", "自定义")
    var selectedPartition by remember { mutableStateOf("boot") }
    var customPartition by remember { mutableStateOf("") }
    var partMenuExpanded by remember { mutableStateOf(false) }

    // 实际生效的分区名 (自定义模式取输入框; 并做白名单字符校验)
    val effectivePartition: String =
        if (selectedPartition == "自定义") customPartition.trim() else selectedPartition
    val partitionValid = effectivePartition.isNotBlank() &&
        SafetyChecker.validatePartitionName(effectivePartition) !is SafetyChecker.CheckResult.Deny

    // 风险评估 (文件 + 分区都就绪时自动计算)
    val assessedRisk: KernelFlasher.RiskLevel? = remember(pickedPath, effectivePartition) {
        if (pickedPath != null && partitionValid) {
            KernelFlasher.assessRisk(effectivePartition, File(pickedPath))
        } else null
    }
    val assessedReason: String = remember(assessedRisk, effectivePartition) {
        if (assessedRisk != null) KernelFlasher.riskReason(effectivePartition, assessedRisk) else ""
    }

    // 确认与流程状态
    var riskConfirmed by remember { mutableStateOf(false) }
    var backupPath by remember { mutableStateOf<String?>(null) }
    var isFlashing by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var showFinalConfirm by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(3) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var lastFlashWasFastboot by remember { mutableStateOf(false) }

    // 日志终端
    val logs = remember { mutableStateListOf<String>() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }

    // 文件选择器 (mime */*, 支持 .img / .bin 等任意内核镜像)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val tmp = File(ctx.cacheDir, "jf_kernel_${System.currentTimeMillis()}.img")
            ctx.contentResolver.openInputStream(it)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            pickedPath = tmp.absolutePath
            pickedName = tmp.name
            logs.add("已选择镜像: ${tmp.name} (${tmp.length() / 1024} KB)")
        }
    }

    val levels = listOf(
        RiskCardInfo(KernelFlasher.RiskLevel.LOW, "低风险", "如刷写 boot.img 同版本", JFColors.Success),
        RiskCardInfo(KernelFlasher.RiskLevel.MEDIUM, "中风险", "如替换 init.rc / 修改 sepolicy", JFColors.Warning),
        RiskCardInfo(KernelFlasher.RiskLevel.HIGH, "高风险", "如刷写不同版本的 vbmeta / dtbo", Color(0xFFFF9800)),
        RiskCardInfo(KernelFlasher.RiskLevel.FATAL, "致命风险", "如刷写 xbl / abl / modem 等底层分区", JFColors.Danger)
    )

    // 最终确认对话框倒计时 (3 秒按钮才能点)
    LaunchedEffect(showFinalConfirm) {
        if (showFinalConfirm) {
            countdown = 3
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    // 执行刷写 (检测通道: fastboot 优先, 否则 ADB+Root)
    fun executeFlash() {
        val path = pickedPath ?: return
        val part = effectivePartition
        val risk = assessedRisk ?: return
        scope.launch {
            isFlashing = true
            logs.clear()
            progress = 0f
            progressLabel = "检测连接模式..."
            val target = KernelFlasher.KernelTarget(
                partition = part,
                imagePath = path,
                riskLevel = risk,
                reason = KernelFlasher.riskReason(part, risk)
            )

            val usbMgr = UsbDeviceManager.get(ctx)
            val fastbootDevice = withContext(Dispatchers.IO) { usbMgr.findFastbootDevice() }
            val ok = when {
                fastbootDevice != null -> {
                    lastFlashWasFastboot = true
                    logs.add("检测到 fastboot 设备: ${fastbootDevice.deviceName}, 走 fastboot 通道")
                    KernelFlasher.flashKernel(ctx, fastbootDevice, target) { p ->
                        scope.launch {
                            logs.add("[${p.current}/${p.total}] ${p.partition}: ${p.message}")
                            if (p.total > 0) progress = p.current.toFloat() / p.total
                            progressLabel = "${p.partition} (${p.current}/${p.total})"
                        }
                    }
                }
                AdbManager.isConnected -> {
                    lastFlashWasFastboot = false
                    logs.add("ADB 在线 (serial=${AdbManager.currentSerial}), 尝试 Root + dd 通道")
                    KernelFlasher.flashKernelRoot(AdbManager.currentSerial ?: "", target) { p ->
                        scope.launch {
                            logs.add("[${p.current}/${p.total}] ${p.partition}: ${p.message}")
                            if (p.total > 0) progress = p.current.toFloat() / p.total
                            progressLabel = "${p.partition} (${p.current}/${p.total})"
                        }
                    }
                }
                else -> {
                    logs.add("❌ 无可用连接: 设备未进 fastboot, 也无 ADB 连接")
                    logs.add("请将设备进 bootloader (fastboot), 或保持 ADB 在线 + Root")
                    progress = null
                    isFlashing = false
                    return@launch
                }
            }

            isFlashing = false
            progress = null
            progressLabel = if (ok) "刷写完成" else "刷写失败"
            if (ok) {
                logs.add("✅ 刷写流程结束")
                // HIGH 风险不自动 reboot, 弹窗让用户确认
                if (risk == KernelFlasher.RiskLevel.HIGH) showRebootConfirm = true
            } else {
                logs.add("❌ 刷写失败, 请查看上方日志")
            }
        }
    }

    JFScaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "内核级刷写", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "高危操作检测, 按风险分级提示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ---------- 文件选择 + 分区选择 ----------
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("① 选择镜像与分区", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 选择镜像")
                        }
                        if (pickedName.isNotEmpty()) {
                            Text(pickedName, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium,
                                maxLines = 1)
                        }
                    }

                    // 分区下拉
                    Text("目标分区:", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(onClick = { partMenuExpanded = true }) {
                            Text(if (selectedPartition == "自定义") "自定义..." else selectedPartition)
                        }
                        DropdownMenu(expanded = partMenuExpanded,
                            onDismissRequest = { partMenuExpanded = false }) {
                            partitionOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        selectedPartition = opt
                                        partMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (selectedPartition == "自定义") {
                        OutlinedTextField(
                            value = customPartition,
                            onValueChange = { customPartition = it.trim() },
                            label = { Text("自定义分区名 (仅字母/数字/下划线)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = customPartition.isNotBlank() && !partitionValid
                        )
                    }

                    // 风险评估结果
                    if (assessedRisk != null) {
                        val info = levels.first { it.level == assessedRisk }
                        Text("评估: ${assessedRisk.displayName} — $assessedReason",
                            style = MaterialTheme.typography.bodyMedium,
                            color = info.color, fontWeight = FontWeight.Bold)
                    }
                    if (!partitionValid && effectivePartition.isNotBlank()) {
                        Text("⚠ 分区名含非法字符 (仅允许字母/数字/下划线)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 风险卡片 (评估命中时高亮) ----------
            levels.forEach { info ->
                RiskCard(info.name, info.desc, info.color,
                    isHighlighted = assessedRisk == info.level)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("操作前必读:", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("• 致命风险操作可能永久变砖, 需 9008 救砖能力备份\n" +
                        "• 请确保已备份当前分区 (下方「备份当前内核」)\n" +
                        "• 请确保救砖包就绪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // 备份按钮 (前置必做)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isBackingUp = true
                                    logs.add("开始备份当前 boot 分区...")
                                    val backup = withContext(Dispatchers.IO) {
                                        KernelFlasher.backupCurrentKernel(
                                            AdbManager.currentSerial ?: "",
                                            File(ctx.cacheDir, "jf_backup").apply { mkdirs() }
                                        )
                                    }
                                    isBackingUp = false
                                    if (backup != null) {
                                        backupPath = backup.absolutePath
                                        logs.add("✅ 备份完成: ${backup.name} (${backup.length() / 1024} KB)")
                                    } else {
                                        logs.add("❌ 备份失败: 需 ADB 在线 + Root 环境")
                                    }
                                }
                            },
                            enabled = AdbManager.isConnected && !isBackingUp && !isFlashing
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 备份当前内核")
                        }
                        if (isBackingUp) Text("备份中...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (backupPath != null) Text("✓ 已备份",
                            style = MaterialTheme.typography.bodySmall, color = JFColors.Success)
                    }

                    // 风险确认 Checkbox
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = riskConfirmed,
                            onCheckedChange = { riskConfirmed = it })
                        Text("我已了解风险并确认继续", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }

                    // 继续按钮 (校验: 文件 + 分区 + 确认勾选)
                    val canContinue = pickedPath != null && partitionValid &&
                        assessedRisk != null && riskConfirmed && !isFlashing
                    Button(
                        onClick = { showFinalConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = JFColors.Danger),
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null,
                            modifier = Modifier.height(18.dp))
                        Text(" 我已了解风险, 继续", fontWeight = FontWeight.Bold)
                    }
                    if (!canContinue && !isFlashing) {
                        val hint = when {
                            pickedPath == null -> "请先选择镜像文件"
                            !partitionValid -> "请填写合法分区名"
                            assessedRisk == null -> "等待风险评估"
                            !riskConfirmed -> "请勾选风险确认"
                            else -> ""
                        }
                        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 日志终端 ----------
            LogTerminal(logs, progress, progressLabel, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---------- 最终确认对话框 (红色警告 + 3 秒倒计时) ----------
    if (showFinalConfirm) {
        val risk = assessedRisk
        AlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            title = {
                Text("⚠ 致命操作最终确认", color = JFColors.Danger, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (risk != null) {
                        val info = levels.first { it.level == risk }
                        Text("目标分区: $effectivePartition",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("风险等级: ${risk.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = info.color, fontWeight = FontWeight.Bold)
                    }
                    Text("此操作可能变砖, 请确认你已具备 9008 救砖能力!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JFColors.Danger, fontWeight = FontWeight.SemiBold)
                    // backupPath 是委托属性, 无法智能转换, 先取本地 val
                    val backupPathLocal = backupPath
                    if (backupPathLocal == null) {
                        Text("⚠ 未备份当前内核, 强烈建议先备份!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JFColors.Danger)
                    } else {
                        Text("✓ 已备份: ${File(backupPathLocal).name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JFColors.Success)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinalConfirm = false
                        executeFlash()
                    },
                    enabled = countdown == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = JFColors.Danger)
                ) {
                    Text(if (countdown > 0) "请等待 ${countdown}s" else "确认刷写")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalConfirm = false }) { Text("取消") }
            }
        )
    }

    // ---------- HIGH 风险 reboot 确认对话框 ----------
    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            title = { Text("HIGH 风险: 确认重启?", color = Color(0xFFFF9800),
                fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (lastFlashWasFastboot)
                        "刷写已完成。HIGH 风险分区已写入。\n设备在 fastboot 模式, 请确认状态正常后重启:\n长按电源键可重启至系统, 或下方尝试 reboot 指令。"
                    else
                        "刷写已完成。HIGH 风险分区已写入 (Root+dd 通道)。\n请确认设备状态正常后重启。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRebootConfirm = false
                    scope.launch {
                        if (!lastFlashWasFastboot && AdbManager.isConnected) {
                            withContext(Dispatchers.IO) {
                                AdbManager.reboot(AdbManager.currentSerial ?: "")
                            }
                            logs.add("已发送 reboot 指令 (ADB)")
                        } else {
                            // fastboot 模式 client 已关闭, 提示手动重启
                            logs.add("请手动重启设备 (长按电源键)")
                        }
                    }
                }) { Text("重启设备") }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirm = false }) { Text("稍后手动重启") }
            }
        )
    }
}

@Composable
private fun RiskCard(name: String, desc: String, color: Color, isHighlighted: Boolean = false) {
    val cardModifier = if (isHighlighted) {
        Modifier.fillMaxWidth().border(2.dp, color, RoundedCornerShape(20.dp))
    } else {
        Modifier.fillMaxWidth()
    }
    LiquidGlassCard(modifier = cardModifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.padding(end = 4.dp).height(40.dp)
                .clip(RoundedCornerShape(4.dp)).background(color)) {}
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    color = color, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
