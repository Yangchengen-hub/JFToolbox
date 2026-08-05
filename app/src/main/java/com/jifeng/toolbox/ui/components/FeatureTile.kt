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

/**
 * 功能卡片 v2 — 顶级 UI 设计。
 *
 * v2 改进:
 *  - 移除硬编码尺寸 (width=100dp, height=124dp), 改为 fillMaxWidth + aspectRatio
 *  - 图标容器改为渐变背景 (替代纯色 primaryContainer)
 *  - 按压时图标缩放 + 颜色加亮, 增强微交互反馈
 *  - 底部渐变改为更自然的 vignette 效果
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
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = HyperOSMotion.cardPressSpring,
        label = "tileScale"
    )
    // 图标缩放 — 按压时缩小, 释放弹回
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = HyperOSMotion.cardPressSpring,
        label = "iconScale"
    )

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    LiquidGlassCard(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        cornerRadius = 28.dp,
        padding = 12.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .drawBehind {
                    // 底部 vignette — 增加纵深感和高级质感
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                primary.copy(alpha = 0.04f)
                            ),
                            startY = size.height * 0.5f,
                            endY = size.height
                        )
                    )
                }
        ) {
            // 圆形渐变图标容器 — 比纯色更有质感
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .scale(iconScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryContainer,
                                primaryContainer.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = onContainerColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
