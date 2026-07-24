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
import androidx.activity.compose.rememberLauncherForActivityResult
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
        startActivity(Intent(this, MainComposeActivity::class.java))
        finish()
    }
}

/** 权限说明条目: 标题 / 用途 / 图标 */
private data class PermInfo(
    val title: String,
    val purpose: String,
    val icon: ImageVector
)

@Composable
private fun PermissionScreen(onProceed: () -> Unit) {
    val ctx = LocalContext.current

    // 根据系统版本构建需要请求的运行时权限列表
    val runtimePerms: List<String> = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // Android 12 及以下
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) { // Android 10 及以下
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // 展示用权限说明列表
    val permInfos: List<PermInfo> = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermInfo("通知权限 (POST_NOTIFICATIONS)",
                    "用于显示刷机进度、下载完成、ADB 守护服务等前台通知。",
                    Icons.Default.Notifications))
                add(PermInfo("媒体访问 (READ_MEDIA_IMAGES/VIDEO/AUDIO)",
                    "读取设备中的图片、视频、音频, 用于备份与刷写。",
                    Icons.Default.Apps))
            }
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.R..Build.VERSION_CODES.S_V2) {
                add(PermInfo("所有文件访问 (MANAGE_EXTERNAL_STORAGE)",
                    "读写刷机包、备份文件、日志到公共存储; 需在系统设置中手动授予。",
                    Icons.Default.Folder))
                add(PermInfo("存储读取 (READ_EXTERNAL_STORAGE)",
                    "读取设备存储中的刷机包与备份文件。",
                    Icons.Default.Folder))
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                add(PermInfo("存储读写 (READ/WRITE_EXTERNAL_STORAGE)",
                    "读写刷机包、备份文件到设备存储。",
                    Icons.Default.Folder))
            }
            add(PermInfo("USB 权限",
                "连接 USB OTG 设备时由系统弹窗索取, 本页面仅作提示, 无需手动授予。",
                Icons.Default.Usb))
        }
    }

    // 逐个请求运行时权限的状态机
    var permIndex by remember { mutableStateOf(-1) }
    var storageLaunched by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permIndex++ }

    // permIndex 变化时驱动请求下一个权限; 全部完成后跳转所有文件访问设置页
    LaunchedEffect(permIndex) {
        when {
            permIndex in runtimePerms.indices -> {
                permLauncher.launch(runtimePerms[permIndex])
            }
            permIndex >= runtimePerms.size && permIndex != -1 && !storageLaunched -> {
                storageLaunched = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager()
                ) {
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
            }
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
                    // 启动逐个请求链: 若无可请求权限则直接进入所有文件访问设置页
                    storageLaunched = false
                    permIndex = 0
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
