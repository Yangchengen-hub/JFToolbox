package com.jifeng.toolbox.ui.tweak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassClickableCard
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TweakComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TweakScreen() }
    }
}

/** 快捷命令数据类 */
private data class QuickCommand(
    val title: String,
    val command: String,
    val icon: ImageVector,
    val category: String,
    val desc: String = ""
)

/** 快捷命令库 */
private val QUICK_COMMANDS = listOf(
    // 设备信息
    QuickCommand("设备信息", "getprop ro.product.model && getprop ro.product.brand && getprop ro.build.version.release",
        Icons.Default.Info, "设备信息", "查看设备型号/品牌/系统版本"),
    QuickCommand("芯片信息", "cat /proc/cpuinfo | head -20",
        Icons.Default.Memory, "设备信息", "查看CPU详细信息"),
    QuickCommand("内存信息", "cat /proc/meminfo | head -10",
        Icons.Default.Storage, "设备信息", "查看RAM使用情况"),
    QuickCommand("存储空间", "df -h",
        Icons.Default.Storage, "设备信息", "查看各分区存储占用"),

    // 电池
    QuickCommand("电池状态", "dumpsys battery",
        Icons.Default.BatteryFull, "电池", "查看电量/电压/温度/充电状态"),
    QuickCommand("电池健康", "dumpsys battery | grep -i health",
        Icons.Default.BatteryFull, "电池", "查看电池健康状态"),

    // 系统
    QuickCommand("系统运行时间", "uptime",
        Icons.Default.Refresh, "系统", "查看系统已运行时间"),
    QuickCommand("内核版本", "uname -a",
        Icons.Default.Memory, "系统", "查看内核版本信息"),
    QuickCommand("已安装应用", "pm list packages -3",
        Icons.Default.DeviceHub, "系统", "列出第三方已安装应用"),
    QuickCommand("SELinux 状态", "getenforce",
        Icons.Default.Security, "系统", "查看SELinux当前状态"),

    // 重启
    QuickCommand("重启系统", "reboot",
        Icons.Default.RestartAlt, "重启", "正常重启设备"),
    QuickCommand("重启 Recovery", "reboot recovery",
        Icons.Default.RestartAlt, "重启", "进入Recovery模式"),
    QuickCommand("重启 Bootloader", "reboot bootloader",
        Icons.Default.RestartAlt, "重启", "进入Fastboot模式"),
    QuickCommand("重启 9008(EDL)", "reboot edl",
        Icons.Default.RestartAlt, "重启", "进入高通9008救砖模式"),
    QuickCommand("软重启", "setprop ctl.restart zygote",
        Icons.Default.Refresh, "重启", "仅重启UI (不关机)"),

    // 调试
    QuickCommand("WiFi 信息", "dumpsys wifi | grep 'mWifiInfo'",
        Icons.Default.DeviceHub, "调试", "查看当前WiFi连接信息"),
    QuickCommand("网络接口", "ifconfig",
        Icons.Default.DeviceHub, "调试", "查看所有网络接口"),
    QuickCommand("进程列表", "ps -A | head -30",
        Icons.Default.BugReport, "调试", "查看前30个运行进程"),
)

@Composable
private fun TweakScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }
    var currentResult by remember { mutableStateOf<String?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    // 按分类分组
    val categories = QUICK_COMMANDS.groupBy { it.category }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("玩机工具",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("点击命令卡片自动执行, 结果实时显示在下方终端。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // 终端输出区
            if (logs.isNotEmpty() || currentResult != null) {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 12.dp
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("执行结果", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { logs.clear(); currentResult = null }) {
                                Icon(Icons.Default.Refresh, contentDescription = "清除",
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                        if (isExecuting) {
                            Text("执行中...", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        LogTerminal(logs, null, null, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 命令卡片列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (category, commands) ->
                    item {
                        Text(category,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(commands) { cmd ->
                        CommandCard(
                            command = cmd,
                            onExecute = {
                                if (!AdbManager.isConnected) {
                                    logs.add("⚠ 设备未连接")
                                    return@CommandCard
                                }
                                isExecuting = true
                                logs.add("▸ $ ${cmd.command}")
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val serial = AdbManager.currentSerial ?: ""
                                        AdbManager.shell(serial, cmd.command)
                                    }
                                    isExecuting = false
                                    if (result.isNullOrBlank()) {
                                        logs.add("(无输出)")
                                    } else {
                                        result.lines().take(50).forEach { line ->
                                            logs.add(line)
                                        }
                                        if (result.lines().size > 50) {
                                            logs.add("... (输出过长, 仅显示前50行)")
                                        }
                                    }
                                    logs.add("")
                                }
                            },
                            onCopy = {
                                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("command", cmd.command)
                                )
                                Toast.makeText(ctx, "已复制: ${cmd.command}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: QuickCommand,
    onExecute: () -> Unit,
    onCopy: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onExecute
            ),
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标
            Icon(
                imageVector = command.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            // 文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = command.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (command.desc.isNotEmpty()) {
                    Text(
                        text = command.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$ ${command.command}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            // 复制按钮
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制命令",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
            }
            // 执行指示
            Icon(Icons.Default.PlayArrow, contentDescription = "执行",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
        }
    }
}
