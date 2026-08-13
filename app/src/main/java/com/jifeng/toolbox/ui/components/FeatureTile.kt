package com.jifeng.toolbox.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.HyperOSMotion
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 功能卡片 v3 — 澎湃OS 4 柔光玻璃风格。
 *
 * v3 改进:
 *  - 功能入口改为 2.5D 图标效果 (多层阴影 + 微视差)
 *  - 图标背景改为柔光玻璃圆形
 *  - 按压时图标微缩放 + 标签弹性动画
 */
@Composable
fun FeatureTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 整体缩放 — softBounce 弹簧
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.softBounce,
        label = "tileScale"
    )

    // 图标缩放 — crispBounce 弹性
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = HyperOSMotion.crispBounce,
        label = "iconScale"
    )

    // 图标 2.5D 视差位移
    val iconOffsetY by animateFloatAsState(
        targetValue = if (pressed) 2f else 0f,
        animationSpec = HyperOSMotion.floatSpring,
        label = "iconParallax"
    )

    // 标签弹性 — 按压时微缩放
    val labelScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = HyperOSMotion.softBounce,
        label = "labelScale"
    )

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    LiquidGlassCard(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        cornerRadius = 20.dp,
        padding = 12.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .drawBehind {
                    // 底部 vignette — 蓝色系渐变
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                primary.copy(alpha = 0.03f)
                            ),
                            startY = size.height * 0.5f,
                            endY = size.height
                        )
                    )
                }
        ) {
            // 2.5D 图标容器 — 多层阴影 + 柔光玻璃圆形背景
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .scale(iconScale)
                    .drawBehind {
                        // 多层柔和阴影 — 2.5D 效果
                        // 外层大阴影
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.06f),
                            radius = this.size.minDimension / 2f + 6f
                        )
                        // 中层阴影
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.04f),
                            radius = this.size.minDimension / 2f + 3f
                        )
                    }
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryContainer.copy(alpha = 0.8f),
                                primaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    ),
            ) {
                // 图标带视差
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = onContainerColor,
                    modifier = Modifier
                        .size(32.dp)
                        .drawBehind {
                            // 图标底部微光
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = size.minDimension * 0.6f
                            )
                        }
                )
            }

            // 标签 — 弹性缩放动画
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .scale(labelScale)
            )
        }
    }
}
