package com.jifeng.toolbox.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.disclaimer.DisclaimerComposeActivity
import com.jifeng.toolbox.ui.main.MainComposeActivity
import com.jifeng.toolbox.ui.theme.JFTheme

class PermissionComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JFTheme { PermissionScreen(onProceed = { proceedToMain() }) } }
    }

    /** 标记权限已询问过, 跳转主页。 */
    private fun proceedToMain() {
        getSharedPreferences(DisclaimerComposeActivity.PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(DisclaimerComposeActivity.KEY_PERMISSION_ASKED, true)
            .apply()
        startActivity(Intent(this, MainComposeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}

private data class PermInfo(
    val title: String,
    val purpose: String,
    val icon: ImageVector
)

@Composable
private fun PermissionScreen(onProceed: () -> Unit) {
    val ctx = LocalContext.current

    // 运行时权限列表 (需要弹窗请求的)
    val runtimePerms: List<String> = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            // INTERNET / NETWORK / WIFI 是 normal 权限, 安装时自动授予, 不需请求
        }
    }

    val permInfos: List<PermInfo> = remember {
        buildList {
            add(PermInfo("网络权限 (INTERNET)",
                "用于固件下载、GitHub API 访问、酷安社区 ROM 源检索。",
                Icons.Default.Notifications))
            add(PermInfo("网络状态",
                "检测网络连接状态, 优化下载策略。",
                Icons.Default.Notifications))
            add(PermInfo("WiFi 状态",
                "获取 WiFi 信息, 支持无线调试配对。",
                Icons.Default.Notifications))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermInfo("通知权限 (POST_NOTIFICATIONS)",
                    "用于显示刷机进度、下载完成、ADB 守护服务等前台通知。",
                    Icons.Default.Notifications))
                add(PermInfo("媒体访问 (READ_MEDIA_*)",
                    "读取设备中的图片、视频、音频, 用于备份与刷写。",
                    Icons.Default.Apps))
            }
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.R..Build.VERSION_CODES.S_V2) {
                add(PermInfo("所有文件访问 (MANAGE_EXTERNAL_STORAGE)",
                    "读写刷机包、备份文件、日志到公共存储。",
                    Icons.Default.Folder))
                add(PermInfo("存储读取",
                    "读取设备存储中的刷机包与备份文件。",
                    Icons.Default.Folder))
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                add(PermInfo("存储读写",
                    "读写刷机包、备份文件到设备存储。",
                    Icons.Default.Folder))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(PermInfo("悬浮窗权限 (SYSTEM_ALERT_WINDOW)",
                    "用于显示 ADB 授权悬浮窗、通知栏快速配对、屏幕镜像。",
                    Icons.Default.Window))
            }
            add(PermInfo("USB 权限",
                "连接 USB OTG 设备时由系统弹窗索取, 本页面仅作提示。",
                Icons.Default.Usb))
            add(PermInfo("应用安装权限",
                "用于安装下载的 APK 刷机包。",
                Icons.Default.Apps))
        }
    }

    // 权限请求状态机
    var permIndex by remember { mutableStateOf(-1) }
    var step by remember { mutableStateOf(0) } // 0=未开始, 1=运行时权限中, 2=特殊权限中, 3=完成
    var hasNextStep by remember { mutableStateOf(true) }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permIndex++
    }

    // 运行时权限链式请求
    LaunchedEffect(permIndex) {
        when {
            permIndex in runtimePerms.indices -> {
                permLauncher.launch(runtimePerms[permIndex])
            }
            permIndex >= runtimePerms.size && permIndex != -1 && step == 1 -> {
                // 运行时权限完成, 进入特殊权限阶段
                step = 2
            }
        }
    }

    // 特殊权限阶段: 依次引导存储 → 悬浮窗 → 安装未知应用
    LaunchedEffect(step) {
        if (step == 2) {
            var pending = false
            // 存储管理权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
            ) {
                pending = true
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    ).apply { data = Uri.parse("package:${ctx.packageName}") }
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    } catch (_: Exception) {}
                }
            }
            // 悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(ctx)
            ) {
                pending = true
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                    ctx.startActivity(intent)
                } catch (_: Exception) {}
            }
            // 安装未知应用权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !ctx.packageManager.canRequestPackageInstalls()
            ) {
                pending = true
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    )
                    ctx.startActivity(intent)
                } catch (_: Exception) {}
            }
            // 全部完成或无需要引导的权限 → 自动跳转
            kotlinx.coroutines.delay(if (pending) 2000 else 500)
            step = 3
            onProceed()
        }
    }

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Spacer(Modifier.height(20.dp))
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
            Text("权限申请", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text("极风工具箱需要以下权限以提供完整功能。可点击「全部授予」逐项授权, 也可「稍后」跳过直接进入主页。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 权限说明列表
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    permInfos.forEach { info -> PermInfoRow(info) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (runtimePerms.isNotEmpty()) {
                        step = 1
                        permIndex = 0
                    } else {
                        step = 2
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("全部授予", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermInfoRow(info: PermInfo) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(info.icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(info.title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(info.purpose, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
