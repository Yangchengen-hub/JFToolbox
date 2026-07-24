package com.jifeng.toolbox.ui.tweak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard

class TweakComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TweakScreen() }
    }
}

@Composable
private fun TweakScreen() {
    val ctx = LocalContext.current
    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("玩机工具", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("一键隐藏环境", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("从酷安/GitHub 检索最新最火的 Root 隐藏模块并刷入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {}) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null,
                            modifier = Modifier.height(18.dp)); Text(" 检索隐藏模块")
                    }
                }
            }

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("月虹检测脚本", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("自动下载并执行酷安「月虹」的一键检测脚本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {}) {
                        Icon(Icons.Default.BugReport, contentDescription = null,
                            modifier = Modifier.height(18.dp)); Text(" 运行月虹检测")
                    }
                }
            }

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("系统更新禁用", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("全品牌通用 (小米/华为/OV等), 尝试堵死 OTA 与静默更新通道。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("⚠ 真实可行性因品牌而异, 部分品牌会触发回锁。本工具仅尝试禁用 OTA 服务组件。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        val serial = AdbManager.listDevices().firstOrNull()
                        // 禁用 OTA 相关包名 (常见清单, 实际效果因品牌而异)
                        val pkgs = listOf(
                            "com.android.updater",
                            "com.huawei.android.hwouc",
                            "com.xiaomi.discover",
                            "com.coloros.safecenter"
                        )
                        pkgs.forEach { p ->
                            AdbManager.shell(serial ?: "", "pm disable-user $p 2>/dev/null")
                        }
                    }) {
                        Icon(Icons.Default.Block, contentDescription = null,
                            modifier = Modifier.height(18.dp)); Text(" 尝试禁用 OTA")
                    }
                }
            }
        }
    }
}
