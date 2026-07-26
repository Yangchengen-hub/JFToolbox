package com.jifeng.toolbox.ui.tools

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.tools.ShellHub
import com.jifeng.toolbox.tools.ShellHubService
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.delay

/**
 * ShellHub 悬浮窗授权 Activity。
 *
 * 触发流程:
 * 1. 第三方 APP 通过 127.0.0.1:8848 请求执行 shell 命令
 * 2. ShellHub 检测到该调用方 UID 未授权 → 设置 pendingAuth
 * 3. ShellHubService 监听到 pendingAuth 变化 → 拉起本 Activity
 * 4. 本 Activity 显示调用方信息 + 「允许 / 拒绝」按钮
 * 5. 用户点击后调用 [ShellHub.resolvePendingAuth] 恢复调用方协程
 * 6. 2 秒后自动 finish, 让出屏幕
 *
 * 视觉: 透明背景 + 居中液态玻璃卡片, 整体位于其他 APP 之上 (悬浮窗效果)。
 */
class ShellHubAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 悬浮窗样式: 透明背景 + 居中
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT)
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT)

        val pkg = intent?.getStringExtra(ShellHubService.EXTRA_PACKAGE) ?: "unknown"
        val label = intent?.getStringExtra(ShellHubService.EXTRA_LABEL) ?: pkg
        val uid = intent?.getIntExtra(ShellHubService.EXTRA_UID, -1) ?: -1

        setContent {
            ShellHubAuthScreen(
                packageName = pkg,
                appLabel = label,
                uid = uid,
                onApprove = {
                    ShellHub.resolvePendingAuth(true)
                    finishAfterDelay()
                },
                onDeny = {
                    ShellHub.resolvePendingAuth(false)
                    finishAfterDelay()
                }
            )
        }
    }

    /** 给用户 1.5 秒视觉反馈再关闭 (避免按钮点完立刻消失有 bug 感)。 */
    private fun finishAfterDelay() {
        // 用单独的线程延迟 finish, 不能阻塞主线程
        Thread {
            try {
                Thread.sleep(1200)
            } catch (_: InterruptedException) {}
            runOnUiThread { if (!isFinishing) finish() }
        }.apply { isDaemon = true; start() }
    }

    @Composable
    private fun ShellHubAuthScreen(
        packageName: String,
        appLabel: String,
        uid: Int,
        onApprove: () -> Unit,
        onDeny: () -> Unit
    ) {
        var resolved by remember { mutableStateOf(false) }
        var approved by remember { mutableStateOf<Boolean?>(null) }

        // 30 秒无操作自动拒绝
        LaunchedEffect(Unit) {
            delay(30_000)
            if (!resolved) {
                resolved = true
                approved = false
                ShellHub.resolvePendingAuth(false)
                delay(800)
                if (!isFinishing) finish()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.55f)  // 半透明遮罩, 突出悬浮窗
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    padding = 20.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 顶部图标 + 标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(28.dp)
                            )
                            Text(
                                "Shell 中枢授权",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "以下应用正在请求 Shell 执行权限",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        // 调用方信息卡片
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.height(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "应用名称",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    appLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "包名: $packageName",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "UID: $uid",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            "授权后该应用可通过 Shell 中枢执行 Shell 命令 (uid=2000 权限)。\n请仅对你信任的应用授权。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        // 决策按钮
                        if (!resolved) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        resolved = true
                                        approved = false
                                        onDeny()
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null,
                                        modifier = Modifier.height(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("拒绝")
                                }
                                Button(
                                    onClick = {
                                        resolved = true
                                        approved = true
                                        onApprove()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                                        modifier = Modifier.height(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("允许")
                                }
                            }
                        } else {
                            // 已决策, 显示反馈
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (approved == true) Icons.Default.CheckCircle else Icons.Default.Block,
                                    contentDescription = null,
                                    tint = if (approved == true)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.height(20.dp)
                                )
                                Text(
                                    if (approved == true) "已允许, 稍后关闭..." else "已拒绝, 稍后关闭...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (approved == true)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
