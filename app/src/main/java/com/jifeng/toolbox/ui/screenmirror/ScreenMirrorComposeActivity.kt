package com.jifeng.toolbox.ui.screenmirror

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.tools.ScreenMirrorEngine
import com.jifeng.toolbox.tools.ShellHub
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val engineState by ScreenMirrorEngine.state.collectAsState()
    var mode by remember { mutableStateOf(ScreenMirrorEngine.Mode.SCREENCAP_POLL) }
    var currentBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var frameW by remember { mutableStateOf(0) }
    var frameH by remember { mutableStateOf(0) }
    var fps by remember { mutableStateOf(0) }
    var startedAtMs by remember { mutableStateOf(0L) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var tapInfo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未启动") }
    val frameTimes = remember { mutableListOf<Long>() }

    // 启动时拉一次设备真实屏幕尺寸 (wm size), 用于把 UI 点击坐标缩放到设备坐标
    LaunchedEffect(Unit) {
        val serial = AdbManager.listDevices().firstOrNull() ?: return@LaunchedEffect
        val size = fetchDeviceSize(serial)
        if (size != null) ScreenMirrorEngine.setDeviceSize(size.first, size.second)
    }

    // 收帧 → 解码 Bitmap → 更新 UI
    LaunchedEffect(Unit) {
        ScreenMirrorEngine.frames.collect { frame ->
            when (frame) {
                is ScreenMirrorEngine.Frame.Jpeg -> {
                    val t = System.currentTimeMillis()
                    val bmp = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size)
                    }
                    if (bmp != null) {
                        currentBitmap = bmp
                        frameW = frame.w
                        frameH = frame.h
                        if (startedAtMs == 0L) startedAtMs = t
                        // 滑动窗口估算实时帧率 (最近 30 帧)
                        frameTimes.add(t)
                        while (frameTimes.size > 30) frameTimes.removeAt(0)
                        if (frameTimes.size >= 2) {
                            val spanMs = frameTimes.last() - frameTimes.first()
                            if (spanMs > 0) {
                                fps = ((frameTimes.size - 1) * 1000.0 / spanMs).toInt()
                            }
                        }
                    }
                }
                ScreenMirrorEngine.Frame.End -> { /* 流结束 */ }
            }
        }
    }

    val isStreaming = engineState is ScreenMirrorEngine.State.Streaming
    // 已运行时长 ticker
    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            elapsedMs = if (startedAtMs > 0) System.currentTimeMillis() - startedAtMs else 0
            delay(500)
        }
    }

    // Activity 销毁时停止引擎, 避免后台继续轮询
    DisposableEffect(Unit) {
        onDispose { ScreenMirrorEngine.stop() }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("屏幕远程控制", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("实时镜像被控端屏幕, 支持触控/滑动/按键。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            // 模式切换 + 启动/停止
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = mode == ScreenMirrorEngine.Mode.SCREENCAP_POLL,
                        onClick = { mode = ScreenMirrorEngine.Mode.SCREENCAP_POLL },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("截屏轮询") }
                    SegmentedButton(
                        selected = mode == ScreenMirrorEngine.Mode.SCREENRECORD_H264,
                        onClick = { mode = ScreenMirrorEngine.Mode.SCREENRECORD_H264 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("实验性H264") }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val serial = AdbManager.listDevices().firstOrNull()
                        if (serial == null) { status = "无设备"; return@Button }
                        status = "启动中..."
                        scope.launch {
                            val ok = ScreenMirrorEngine.start(serial, mode, targetFps = 5)
                            status = if (ok) "镜像中" else "启动失败: ${(ScreenMirrorEngine.state.value as? ScreenMirrorEngine.State.Failed)?.msg.orEmpty()}"
                            if (ok) {
                                startedAtMs = 0L
                                frameTimes.clear()
                                fps = 0
                            }
                        }
                    },
                    enabled = !isStreaming
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text(" 启动")
                }
                OutlinedButton(
                    onClick = {
                        ScreenMirrorEngine.stop()
                        status = "已停止"
                        currentBitmap = null
                        frameTimes.clear()
                        fps = 0
                        startedAtMs = 0L
                        elapsedMs = 0L
                    },
                    enabled = isStreaming
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text(" 停止")
                }
            }
            Spacer(Modifier.height(8.dp))

            // 状态栏: 当前帧率 / 分辨率 / 已运行时长
            val resText = if (frameW > 0) "${frameW}x${frameH}" else "-"
            Text("状态: $status | 分辨率: $resText | 帧率: $fps FPS | 时长: ${formatElapsed(elapsedMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            // 屏幕镜像区: 显示真实帧, 按帧尺寸动态调整宽高比
            val aspect = if (frameW > 0 && frameH > 0) frameW.toFloat() / frameH else 9f / 16f
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
                    .clip(RoundedCornerShape(16.dp)).background(Color.Black)
            ) {
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()
                val bmp = currentBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "屏幕镜像",
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val (dx, dy) = mapToDevice(offset.x, offset.y, containerW, containerH)
                                    tapInfo = "点击 ($dx, $dy)"
                                    val s = AdbManager.listDevices().firstOrNull()
                                    if (s != null) scope.launch {
                                        withContext(Dispatchers.IO) { AdbManager.shell(s, "input tap $dx $dy") }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                var startX = 0f; var startY = 0f
                                var lastX = 0f; var lastY = 0f
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        startX = offset.x; startY = offset.y
                                        lastX = offset.x; lastY = offset.y
                                    },
                                    onDrag = { change, _ ->
                                        lastX = change.position.x; lastY = change.position.y
                                    },
                                    onDragEnd = {
                                        val (sx, sy) = mapToDevice(startX, startY, containerW, containerH)
                                        val (ex, ey) = mapToDevice(lastX, lastY, containerW, containerH)
                                        tapInfo = "滑动 ($sx,$sy)→($ex,$ey)"
                                        val s = AdbManager.listDevices().firstOrNull()
                                        if (s != null) scope.launch {
                                            withContext(Dispatchers.IO) { AdbManager.shell(s, "input swipe $sx $sy $ex $ey 300") }
                                        }
                                    }
                                )
                            },
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Column(modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ScreenShare, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.height(48.dp))
                        val placeholder = when (engineState) {
                            is ScreenMirrorEngine.State.Failed -> (engineState as ScreenMirrorEngine.State.Failed).msg
                            ScreenMirrorEngine.State.Idle -> "等待启动"
                            is ScreenMirrorEngine.State.Streaming -> "加载中..."
                        }
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp))
                        if (tapInfo.isNotEmpty()) {
                            Text(tapInfo, style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                // Shell 权限中枢: deploy + start + stop + exec (实化 IPC 链路)
                val shellState by ShellHub.state.collectAsState()
                var cmdInput by remember { mutableStateOf("id") }
                var cmdOutput by remember { mutableStateOf("") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shell 权限中枢", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("通过 ADB Shell 越权获取最高权限, 作为中枢给其他应用授权 (类 Shizuku)。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 状态行: daemon 状态 + 中继端口
                    val stateText = when (val s = shellState) {
                        ShellHub.State.Stopped -> "已停止"
                        ShellHub.State.Starting -> "启动中..."
                        is ShellHub.State.Running -> "运行中 pid=${s.pid} uptime=${s.uptime}s"
                        is ShellHub.State.Failed -> "失败: ${s.msg}"
                    }
                    Text("状态: $stateText | 中继: ${if (ShellHub.isForwarded()) "127.0.0.1:8848 ✓" else "未开"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 启动 / 停止
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            cmdOutput = "部署 daemon..."
                            scope.launch {
                                val serial = AdbManager.listDevices().firstOrNull()
                                if (serial == null) { cmdOutput = "无设备"; return@launch }
                                if (!ShellHub.deploy(serial)) {
                                    cmdOutput = "部署失败"
                                    return@launch
                                }
                                cmdOutput = "启动 daemon..."
                                val ok = ShellHub.start(serial)
                                cmdOutput = if (ok) "daemon 已启动" else "启动失败 (见状态)"
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 启动权限服务")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val serial = AdbManager.listDevices().firstOrNull()
                                if (serial != null) ShellHub.stop(serial)
                                cmdOutput = "已停止"
                            }
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 停止服务")
                        }
                    }
                    // 执行命令 (验证 IPC 链路)
                    OutlinedTextField(
                        value = cmdInput,
                        onValueChange = { cmdInput = it },
                        label = { Text("通过 daemon 执行 (例: id / getprop ro.build.model)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        scope.launch {
                            val result = ShellHub.exec(cmdInput.ifBlank { "id" })
                            cmdOutput = result ?: "(失败, 无输出)"
                        }
                    }) {
                        Icon(Icons.Default.TouchApp, contentDescription = null,
                            modifier = Modifier.height(18.dp))
                        Text(" 执行命令")
                    }
                    if (cmdOutput.isNotEmpty()) {
                        Text("输出: $cmdOutput",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 把 UI 上的点击坐标 (uiX, uiY) 按容器尺寸 → 设备屏幕真实坐标缩放。 */
private fun mapToDevice(uiX: Float, uiY: Float, containerW: Float, containerH: Float): Pair<Int, Int> {
    val (dw, dh) = ScreenMirrorEngine.deviceSize()
    val dx = if (containerW > 0 && dw > 0) (uiX / containerW * dw).toInt() else uiX.toInt()
    val dy = if (containerH > 0 && dh > 0) (uiY / containerH * dh).toInt() else uiY.toInt()
    return Pair(dx.coerceAtLeast(0), dy.coerceAtLeast(0))
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** 从 `wm size` 输出解析设备真实屏幕尺寸, 优先 Override size, 其次 Physical size。 */
private suspend fun fetchDeviceSize(serial: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
    val out = AdbManager.shell(serial, "wm size") ?: return@withContext null
    val override = Regex("""Override size:\s*(\d+)x(\d+)""").find(out)
    val physical = Regex("""Physical size:\s*(\d+)x(\d+)""").find(out)
    val m = override ?: physical ?: return@withContext null
    val w = m.groupValues[1].toIntOrNull() ?: return@withContext null
    val h = m.groupValues[2].toIntOrNull() ?: return@withContext null
    if (w > 0 && h > 0) Pair(w, h) else null
}
