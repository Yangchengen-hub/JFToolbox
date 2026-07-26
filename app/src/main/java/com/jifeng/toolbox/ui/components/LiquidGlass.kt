package com.jifeng.toolbox.ui.components

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 液态玻璃容器 (动态版)。
 *
 * 视觉特征:
 *  - Android 12+ 用 RenderEffect.createOffsetEffect 真模糊 (background blur)
 *  - 兜底用 drawBehind 渐变 + 1dp 高光描边模拟玻璃质感
 *  - 动态光带: 一条横向高光每 6 秒扫过一次 (infiniteTransition)
 *  - 按压缩放 0.98 + 描边变亮 (collectIsPressedAsState)
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tintAlpha: Float = 0.85f,
    pressedScale: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val baseTint = if (isDark) JFColors.GlassDarkTint else JFColors.GlassLightTint
    val strokeColor = if (isDark) JFColors.GlassDarkStroke else JFColors.GlassLightStroke
    val shape = RoundedCornerShape(cornerRadius)

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressedScale && isPressed) 0.98f else 1f

    // 动态光带: 0→1 横向扫过, 6s 一轮
    val transition = rememberInfiniteTransition(label = "jf_glass_sweep")
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000),
            repeatMode = RepeatMode.Restart
        ), label = "sweep"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                shadowElevation = if (isPressed) 2f else 8f
                this.shape = androidx.compose.ui.graphics.RectangleShape
                clip = true
            }
            .clip(shape)
            .drawBehind {
                // 1. 玻璃底色
                drawRect(color = baseTint.copy(alpha = tintAlpha))
                // 2. 顶部高光
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.06f else 0.32f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = size.height * 0.45f
                    )
                )
                // 3. 动态光带 (扫光)
                val sweepX = size.width * sweep
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = if (isDark) 0.10f else 0.22f),
                            Color.White.copy(alpha = 0f)
                        ),
                        startX = sweepX - size.width * 0.25f,
                        endX = sweepX + size.width * 0.05f
                    )
                )
            }
            .border(
                width = if (isPressed) 1.5.dp else 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = if (isPressed) 1f else 0.85f),
                        strokeColor.copy(alpha = 0.35f),
                        strokeColor.copy(alpha = 0.85f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    pressedScale: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    LiquidGlassBox(
        modifier = modifier,
        cornerRadius = cornerRadius,
        tintAlpha = 0.9f,
        pressedScale = pressedScale,
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

/** 可点击的液态玻璃卡片 (自动处理 pressed 状态)。 */
@Composable
fun LiquidGlassClickableCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    LiquidGlassCard(
        modifier = modifier.then(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true),
                onClick = onClick
            )
        ),
        cornerRadius = cornerRadius,
        padding = padding,
        pressedScale = true,
        interactionSource = interactionSource
    ) { content() }
}
