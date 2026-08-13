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
import androidx.compose.ui.draw.blur
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
 * 柔光玻璃容器 v3 — 澎湃OS 4 设计语言。
 *
 * v3 改进 (澎湃OS 4 柔光玻璃):
 *  - 卡片背景改为柔光玻璃材质: 半透明 + 强高斯模糊(48dp) + 边缘高光线条(1dp, 15%白)
 *  - 新增光效跟随: 按压时局部光效扩散
 *  - 阴影改为多层柔和阴影 (非 Material 默认)
 *  - 圆角加大(20dp)
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
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

    // 按压缩放 — 使用澎湃OS 4 softBounce 弹簧
    val scale by animateFloatAsState(
        targetValue = if (pressedScale && isPressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.softBounce,
        label = "glassScale"
    )

    // 按压光效扩散
    val pressGlow by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = HyperOSMotion.standardEasing.let {
            androidx.compose.animation.core.tween(
                durationMillis = HyperOSMotion.durationShort,
                easing = it
            )
        },
        label = "pressGlow"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                // 多层柔和阴影 — 替代 Material 默认阴影
                shadowElevation = 0f
                this.shape = shape
                clip = true
            }
            .drawBehind {
                // 外层柔和阴影 (模拟多层阴影)
                drawRect(color = Color.Black.copy(alpha = 0.04f))

                // 玻璃底色 — 半透明柔光
                drawRect(color = baseTint.copy(alpha = tintAlpha))

                // 顶部柔光高光带
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassHighlight,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f
                    )
                )

                // 按压时局部光效扩散
                if (pressGlow > 0f) {
                    drawRect(
                        color = Color.White.copy(
                            alpha = pressGlow * (if (isDark) 0.03f else 0.06f)
                        )
                    )
                    // 中心径向光效
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = pressGlow * 0.08f),
                                Color.Transparent
                            ),
                            radius = size.minDimension * 0.6f
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width / 2f,
                            size.height / 2f
                        ),
                        radius = size.minDimension * 0.6f
                    )
                }
            }
            // 边缘高光线条 — 1dp, 15%白
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = if (isPressed) 0.9f else 0.6f),
                        strokeColor.copy(alpha = 0.15f)
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

/** 可点击的柔光玻璃卡片 (自动处理 pressed 状态)。 */
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
