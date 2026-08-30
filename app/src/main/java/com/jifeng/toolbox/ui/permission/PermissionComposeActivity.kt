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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.disclaimer.DisclaimerComposeActivity
import com.jifeng.toolbox.ui.main.MainComposeActivity
import com.jifeng.toolbox.ui.theme.JFTheme

class PermissionComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JFTheme {
                PermissionScreen(
                    onProceed = { proceedToMain() }
                )
            }
        }
    }

    /** 跳转主页 (用户主动点击, 不再自动跳转)。 */
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

private enum class PermState { GRANTED, PENDING }

private data class SpecialPerm(
    val title: String,
    val purpose: String,
    val isGranted: () -> Boolean,
    val grant: () -> Unit
)

@Composable
private fun PermissionScreen(onProceed: () -> Unit) {
    val ctx = LocalContext.current

    // 运行时权限 (系统弹窗逐个请求)
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
        }
    }

    // 特殊权限 (需要跳设置页, 返回到本页时重新检测状态)
    fun buildSpecialPerms(): List<SpecialPerm> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(SpecialPerm(
                "所有文件访问",
                "读写刷机包、备份文件、终端文件管理 (终端默认打开内部存储)",
                isGranted = { Environment.isExternalStorageManager() },
                grant = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .apply { data = Uri.parse("package:${ctx.packageName}") })
                    } catch (_: Exception) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }
                }
            ))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(SpecialPerm(
                "悬浮窗",
                "显示 ADB 授权悬浮窗、通知栏快速配对、屏幕镜像",
                isGranted = { Settings.canDrawOverlays(ctx) },
                grant = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")))
                    }
                }
            ))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(SpecialPerm(
                "安装未知应用",
                "安装下载的 APK 刷机包",
                isGranted = { ctx.packageManager.canRequestPackageInstalls() },
                grant = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${ctx.packageName}")))
                    }
                }
            ))
        }
    }

    // 每次回到页面 (从设置页返回) 自动刷新权限状态
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val specialPerms = remember(refreshTick) { buildSpecialPerms() }
    val runtimeGranted = remember(refreshTick) {
        runtimePerms.all {
            ctx.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    var runtimeRequested by remember { mutableStateOf(false) }
    var requestTick by remember { mutableIntStateOf(0) }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 弹窗结束, 刷新状态
        runtimeRequested = true
        refreshTick++
    }

    // 点「一键授予运行时权限」时发起请求
    LaunchedEffect(requestTick) {
        if (requestTick > 0 && !runtimeGranted) {
            val pending = runtimePerms.filter {
                ctx.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            if (pending.isNotEmpty()) permLauncher.launch(pending)
        }
    }

    val allSpecialGranted = specialPerms.all { it.isGranted() }
    val allDone = runtimeGranted && allSpecialGranted

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Spacer(Modifier.height(12.dp))
            Icon(if (allDone) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (allDone) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.height(52.dp))
            Text("权限申请", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text("授予权限后功能才能完整使用。逐项点击授权, 从设置页返回后这里会自动更新状态。也可以稍后在主页随时授权。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 运行时权限
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PermStatusRow(
                        title = "通知 / 媒体读取",
                        desc = "刷机进度通知、读取图片视频音频用于备份",
                        granted = runtimeGranted
                    )
                    if (!runtimeGranted) {
                        OutlinedButton(onClick = { requestTick++ },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (runtimeRequested) "重新授予运行时权限" else "授予运行时权限")
                        }
                    }
                }
            }

            // 特殊权限 (逐项)
            specialPerms.forEach { sp ->
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PermStatusRow(title = sp.title, desc = sp.purpose, granted = sp.isGranted())
                        if (!sp.isGranted()) {
                            Button(
                                onClick = { sp.grant() },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("去授权", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (allDone) "全部完成, 进入主页" else "进入主页",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("稍后再说", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermStatusRow(title: String, desc: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title + if (granted) "  ✓ 已授予" else "",
                style = MaterialTheme.typography.titleSmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
