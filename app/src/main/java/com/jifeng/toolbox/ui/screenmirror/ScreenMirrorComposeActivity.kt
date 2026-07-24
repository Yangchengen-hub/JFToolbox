package com.jifeng.toolbox.ui.screenmirror

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenMirrorComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScreenMirrorScreen() }
    }
}

@Composable
private fun ScreenMirrorScreen() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("未启动") }
    var tapInfo by remember { mutableStateOf("") }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("屏幕远程控制", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("实时镜像被控端屏幕, 支持触控/滑动/按键。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            // 屏幕镜像区 (16:9 占位)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val x = offset.x.toInt(); val y = offset.y.toInt()
                        tapInfo = "点击 ($x, $y)"
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // 通过 ADB input tap 模拟点击
                                AdbManager.listDevices().firstOrNull()?.let {
                                    AdbManager.shell(it, "input tap $x $y")
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> tapInfo = "拖动起点 ${offset.x.toInt()},${offset.y.toInt()}" },
                        onDrag = { change, drag ->
                            tapInfo = "滑动 Δ${drag.x.toInt()},${drag.y.toInt()}"
                        },
                        onDragEnd = {
                            // 通过 ADB input swipe 模拟滑动 (简化)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AdbManager.listDevices().firstOrNull()?.let {
                                        AdbManager.shell(it, "input swipe 500 1000 500 500 300")
                                    }
                                }
                            }
                        }
                    )
                }) {
                Column(modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ScreenShare, contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.height(48.dp))
                    Text("屏幕镜像区 (Phase 7 接入 minicap/scrcpy 数据流)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp))
                    Text(tapInfo, style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shell 权限中枢", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("通过 ADB Shell 越权获取最高权限, 作为中枢给其他应用授权 (类 Shizuku)。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            status = "启动权限服务中..."
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    AdbManager.listDevices().firstOrNull()?.let {
                                        AdbManager.shell(it, "sh /sdcard/Android/data/com.jifeng.toolbox/files/start_daemon.sh")
                                    } ?: "无设备"
                                }
                                status = "权限服务: $r"
                            }
                        }) {
                            Icon(Icons.Default.TouchApp, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 启动权限服务")
                        }
                    }
                    Text("状态: $status", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
