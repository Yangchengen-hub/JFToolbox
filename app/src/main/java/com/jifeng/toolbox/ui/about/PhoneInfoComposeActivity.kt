package com.jifeng.toolbox.ui.about

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.core.DeviceInfo
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassBackground
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.JFTheme

/**
 * 全屏"关于手机"页 — 显示全量设备信息。
 *
 * 从主页点击设备卡片进入。DeviceInfo 通过静态 companion 传递
 * (简单可靠, 避免 Parcelable 大对象序列化开销)。
 */
class PhoneInfoComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JFTheme {
                LiquidGlassBackground {
                    PhoneInfoScreen(device = device, onBack = { finish() })
                }
            }
        }
    }

    companion object {
        @JvmStatic var device: DeviceInfo? = null
    }
}

@Composable
private fun PhoneInfoScreen(device: DeviceInfo?, onBack: () -> Unit) {
    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text("关于手机", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            }

            if (device == null) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("未连接设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // 顶部大卡片: 设备型号 + 头像
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp, padding = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                            .background(Brush.linearGradient(colors = listOf(
                                JFColors.BrandGradientStart, JFColors.BrandGradientEnd))),
                            contentAlignment = Alignment.Center) {
                            Text(device.brand.take(2).ifBlank { "JF" }.uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.displayName, style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text("Android ${device.androidVersion} · SDK ${device.sdkInt}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(device.chipset, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 分组卡片
                device.grouped().forEach { (groupName, items) ->
                    InfoGroupCard(title = groupName, items = items)
                }

                // 分区表
                if (device.partitions.isNotEmpty()) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text("分区表 (${device.partitions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        device.partitions.forEach { p ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = if (p.isProtected) JFColors.Warning else MaterialTheme.colorScheme.onSurface)
                                Text(formatSize(p.size),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InfoGroupCard(title: String, items: List<Pair<String, String>>) {
    val clipboard = LocalClipboardManager.current
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {
                val text = items.joinToString("\n") { (k, v) -> "$k: $v" }
                clipboard.setText(AnnotatedString(text))
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        items.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top) {
                Text(k, modifier = Modifier.weight(0.45f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v.ifBlank { "—" }, modifier = Modifier.weight(0.55f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
