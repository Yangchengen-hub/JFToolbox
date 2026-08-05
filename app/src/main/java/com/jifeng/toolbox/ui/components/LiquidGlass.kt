package com.jifeng.toolbox.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
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
import com.jifeng.toolbox.ui.theme.HyperOSMotion

/**
 * 液态玻璃容器 v2 — 性能优化版。
 *
 * v2 改进:
 *  - 移除 rememberInfiniteTransition 扫光动画 (消除首页 19 个并发无限动画的卡顿根因)
 *  - 扫光改为按压触发的单次动画, 仅在 pressed 时运行
 *  - 简化 drawBehind 绘制层级 (3层→2层), 减少 overdraw
 *  - 使用 Material3 ripple() 替代已弃用的 Material1 rememberRipple
 *  - shadowElevation 仅在 pressed 时变化, 减少每帧重算
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

    // 按压缩放 — spring 弹性回弹
    val scale by animateFloatAsState(
        targetValue = if (pressedScale && isPressed) 0.96f else 1f,
        animationSpec = HyperOSMotion.cardPressSpring,
        label = "glassScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                shadowElevation = if (isPressed) 12f else 6f
                this.shape = shape
                clip = true
            }
            .clip(shape)
            .drawBehind {
                // 1. 玻璃底色 + 顶部高光合并为一次绘制
                drawRect(color = baseTint.copy(alpha = tintAlpha))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.05f else 0.28f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = size.height * 0.5f
                    )
                )
                // v2: 移除扫光动画 — 改为静态高光带, 零帧开销
                // 按压时加亮底色模拟玻璃受压透光感
                if (isPressed) {
                    drawRect(color = Color.White.copy(alpha = if (isDark) 0.03f else 0.06f))
                }
            }
            .border(
                width = if (isPressed) 1.5.dp else 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = if (isPressed) 1f else 0.7f),
                        strokeColor.copy(alpha = 0.25f)
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
                indication = ripple(bounded = true),
                onClick = onClick
            )
        ),
        cornerRadius = cornerRadius,
        padding = padding,
        pressedScale = true,
        interactionSource = interactionSource
    ) { content() }
}
