package com.jifeng.toolbox.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.HyperOSMotion

/**
 * 柔光玻璃容器 v4 — 澎湃OS 4 柔光玻璃设计语言。
 *
 * v4 核心改进:
 *  - 折射层 (refraction): 模拟光线穿过玻璃时的折射偏移
 *  - 反射高光 (specular highlight): 顶部边缘的薄亮线条
 *  - 色散效果 (chromatic aberration): 边缘微弱彩虹偏移
 *  - 边缘高光 (edge highlight): 上边缘更亮, 模拟真实玻璃顶部受光
 *  - 非等圆角: 上略大下略小, 模拟重力感
 *  - 环境自适应: 中性质感, 不绑定固定色相
 *  - 透明度降低: 浅色70%, 深色50%, 更多壁纸透出
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    pressedScale: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val strokeColor = if (isDark) JFColors.GlassDarkStroke else JFColors.GlassLightStroke
    val isPressed by interactionSource.collectIsPressedAsState()

    // 非等圆角 — 上略大下略小模拟重力感
    val topRadius = cornerRadius + 2.dp
    val bottomRadius = cornerRadius - 2.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomEnd = bottomRadius,
        bottomStart = bottomRadius
    )

    val scale by animateFloatAsState(
        targetValue = if (pressedScale && isPressed) 0.96f else 1f,
        animationSpec = HyperOSMotion.glassPressSpring,
        label = "glassScale"
    )

    val pressGlow by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(
            durationMillis = HyperOSMotion.durationShort,
            easing = HyperOSMotion.emphasizedDecelerate
        ),
        label = "pressGlow"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .drawBehind {
                val w = size.width
                val h = size.height

                // 第1层: 多层极柔和阴影
                drawRoundRect(
                    color = JFColors.ShadowOuter,
                    cornerRadius = CornerRadius(bottomRadius.toPx()),
                    size = Size(w + 8f, h + 8f),
                    topLeft = Offset(-4f, -2f)
                )
                drawRoundRect(
                    color = JFColors.ShadowMiddle,
                    cornerRadius = CornerRadius(bottomRadius.toPx()),
                    size = Size(w + 4f, h + 4f),
                    topLeft = Offset(-2f, -1f)
                )

                // 第2层: 玻璃底色
                val baseAlpha = if (isDark) 0.50f else 0.70f
                val baseColor = if (isDark) Color(0xFF0E0E12) else Color.White
                drawRoundRect(
                    color = baseColor.copy(alpha = baseAlpha),
                    cornerRadius = CornerRadius(topRadius.toPx())
                )

                // 第3层: 折射层
                val refractionColor = if (isDark) JFColors.GlassRefractionDark else JFColors.GlassRefractionLight
                drawRoundRect(
                    color = refractionColor,
                    cornerRadius = CornerRadius(topRadius.toPx())
                )

                // 第4层: 顶部反射高光 (specular)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassSpecularHighlight,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.12f
                    ),
                    cornerRadius = CornerRadius(topRadius.toPx())
                )

                // 第5层: 顶部柔光带
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassHighlight,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.35f
                    ),
                    cornerRadius = CornerRadius(topRadius.toPx())
                )

                // 第6层: 色散效果
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(JFColors.ChromaticAberrationR, Color.Transparent),
                        startX = 0f,
                        endX = w * 0.05f
                    ),
                    cornerRadius = CornerRadius(topRadius.toPx())
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, JFColors.ChromaticAberrationB),
                        startX = w * 0.95f,
                        endX = w
                    ),
                    cornerRadius = CornerRadius(topRadius.toPx())
                )

                // 第7层: 按压光效
                if (pressGlow > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = pressGlow * (if (isDark) 0.04f else 0.08f)),
                        cornerRadius = CornerRadius(topRadius.toPx())
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = pressGlow * 0.10f),
                                Color.Transparent
                            ),
                            radius = size.minDimension * 0.7f
                        ),
                        center = Offset(w / 2f, h / 2f),
                        radius = size.minDimension * 0.7f
                    )
                }
            }
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = if (isPressed) 1.0f else 0.8f),
                        strokeColor.copy(alpha = 0.2f)
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
        pressedScale = pressedScale,
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

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
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
        ),
        cornerRadius = cornerRadius,
        padding = padding,
        pressedScale = true,
        interactionSource = interactionSource
    ) { content() }
}
