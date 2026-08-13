package com.jifeng.toolbox.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.HyperOSMotion

/**
 * 玻璃瓷砖按钮 v3 — 澎湃OS 4 柔光玻璃风格。
 *
 * v3 改进:
 *  - 按钮改为半透明玻璃瓷砖风格
 *  - 活跃态: 柔和内发光 (品牌色 30% 透明度填充)
 *  - 按压: 缩放 0.95 + 光效收缩
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // 按压缩放 — crispBounce 弹簧
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.crispBounce,
        label = "btnScale"
    )

    // 光效收缩
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            pressed -> 0f
            isActive -> 0.3f
            else -> 0f
        },
        animationSpec = HyperOSMotion.standardEasing.let {
            androidx.compose.animation.core.tween(
                durationMillis = HyperOSMotion.durationShort,
                easing = it
            )
        },
        label = "btnGlow"
    )

    val shape = RoundedCornerShape(16.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .drawBehind {
                // 玻璃底色
                val baseColor = if (isDark) {
                    JFColors.GlassDarkTint.copy(alpha = 0.65f)
                } else {
                    JFColors.GlassLightTint.copy(alpha = 0.7f)
                }
                drawRect(color = baseColor)

                // 顶部柔光
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.06f else 0.2f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f
                    )
                )

                // 活跃态内发光 — 品牌色 30% 透明度
                if (glowAlpha > 0f) {
                    drawRect(
                        color = JFColors.Brand.copy(alpha = glowAlpha)
                    )
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        JFColors.GlassLightStroke.copy(alpha = 0.5f),
                        JFColors.GlassLightStroke.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                enabled = enabled
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) JFColors.Brand
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 玻璃胶囊按钮 — 用于重启按钮等小型操作。
 */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.crispBounce,
        label = "capsuleScale"
    )

    val shape = RoundedCornerShape(24.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .drawBehind {
                val baseColor = if (isDark) {
                    JFColors.GlassDarkTint.copy(alpha = 0.5f)
                } else {
                    JFColors.GlassLightTint.copy(alpha = 0.6f)
                }
                drawRect(color = baseColor)

                // 顶部微光
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.04f else 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.5f
                    )
                )

                if (pressed) {
                    drawRect(
                        color = JFColors.Brand.copy(alpha = 0.15f)
                    )
                }
            }
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        JFColors.GlassLightStroke.copy(alpha = 0.4f),
                        JFColors.GlassLightStroke.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                enabled = enabled
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
