package com.jifeng.toolbox.ui.flash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFColors

data class FlashPartition(
    val name: String,
    val sizeBytes: Long = 0,
    val status: FlashStatus = FlashStatus.PENDING,
    val progress: Float = 0f
)

enum class FlashStatus {
    PENDING, FLASHING, SUCCESS, FAILED, SKIPPED
}

/**
 * HyperOS 4 风格刷写进度卡片。
 *
 * - 顶部: 圆形进度环 (百分比中心)
 * - 中部: 正在刷写的分区名 + 速度/状态
 * - 底部: 分区清单, 每个分区独立进度条 + 状态图标
 */
@Composable
fun FlashProgressCard(
    partitions: List<FlashPartition>,
    totalProgress: Float,
    currentPartition: String?,
    modifier: Modifier = Modifier,
    speedText: String? = null,
    isRunning: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    LiquidGlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 24.dp, padding = 20.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 圆形进度环 + 百分比
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp).padding(8.dp)) {
                // 光晕
                Canvas(modifier = Modifier.size(140.dp)) {
                    val pct = totalProgress.coerceIn(0f, 1f)
                    // 背景轨道
                    drawCircle(
                        color = JFColors.Brand.copy(alpha = 0.12f),
                        radius = size.minDimension / 2f - 8f,
                        style = Stroke(width = 12f.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // 进度弧
                    if (pct > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    JFColors.BrandGradientStart,
                                    JFColors.BrandGradientEnd,
                                    JFColors.AiSenseBlue,
                                    JFColors.BrandGradientStart
                                ),
                                center = center
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * pct,
                            useCenter = false,
                            style = Stroke(width = 12f.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                // 中心文字
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(totalProgress * 100).toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurface)
                    if (isRunning && currentPartition != null) {
                        Text("刷写中",
                            fontSize = 11.sp,
                            color = JFColors.Brand,
                            fontWeight = FontWeight.Medium)
                    } else if (totalProgress >= 1f) {
                        Text("已完成",
                            fontSize = 11.sp,
                            color = JFColors.Success,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 当前分区 + 速度
            currentPartition?.let {
                Text(it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface)
                if (!speedText.isNullOrBlank()) {
                    Text(speedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 分区清单
            if (partitions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    partitions.forEach { p ->
                        PartitionProgressRow(p)
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionProgressRow(partition: FlashPartition) {
    val scheme = MaterialTheme.colorScheme
    val pct by animateFloatAsState(
        targetValue = partition.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "part_pct"
    )
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surface.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (partition.status) {
            FlashStatus.SUCCESS -> Icons.Default.CheckCircle to JFColors.Success
            FlashStatus.FAILED -> Icons.Default.Error to JFColors.Danger
            FlashStatus.FLASHING -> Icons.Default.PlayArrow to JFColors.Brand
            FlashStatus.SKIPPED -> Icons.Default.Pending to scheme.onSurfaceVariant
            FlashStatus.PENDING -> Icons.Default.Pending to scheme.onSurfaceVariant.copy(alpha = 0.5f)
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(partition.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (partition.status == FlashStatus.FLASHING)
                        FontWeight.SemiBold else FontWeight.Normal,
                    color = scheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (partition.sizeBytes > 0) {
                    Text(formatSize(partition.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(8.dp))
                Text("${(partition.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (partition.status) {
                        FlashStatus.SUCCESS -> JFColors.Success
                        FlashStatus.FAILED -> JFColors.Danger
                        FlashStatus.FLASHING -> JFColors.Brand
                        else -> scheme.onSurfaceVariant
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold)
            }
            // 独立进度条
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when (partition.status) {
                                FlashStatus.FAILED -> Brush.horizontalGradient(
                                    listOf(JFColors.Danger, JFColors.Danger.copy(alpha = 0.6f)))
                                FlashStatus.SUCCESS -> Brush.horizontalGradient(
                                    listOf(JFColors.Success, JFColors.Success.copy(alpha = 0.6f)))
                                else -> Brush.horizontalGradient(
                                    listOf(JFColors.BrandGradientStart, JFColors.BrandGradientEnd))
                            }
                        )
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
