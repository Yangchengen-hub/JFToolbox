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
 * 功能卡片 v4 — 柔光玻璃风格。
 *
 * v4 改进:
 *  - 图标背景改为更透明的柔光圆形
 *  - 按压时整体微缩放 + 图标弹性反馈
 *  - 去除固定蓝色底部渐变
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
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.softBounce,
        label = "tileScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = HyperOSMotion.crispBounce,
        label = "iconScale"
    )

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
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            // 图标容器 — 柔光玻璃圆形
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .scale(iconScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryContainer.copy(alpha = 0.6f),
                                primaryContainer.copy(alpha = 0.3f)
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
                modifier = Modifier
                    .padding(top = 8.dp)
                    .scale(labelScale)
            )
        }
    }
}
