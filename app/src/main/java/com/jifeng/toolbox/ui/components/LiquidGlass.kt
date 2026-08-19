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
 * 液态玻璃容器 v5 — Liquid Glass 设计语言。
 *
 * v5 液态玻璃核心特征:
 *  - 高透明度: 浅色85%白 / 深色65%深色底 (更多壁纸色彩透出)
 *  - 强折射效果: 多层折射模拟液态流动感
 *  - 液态流动边框高光: 上边缘更亮, 两侧带虹彩流动渐变
 *  - 更圆润的圆角 (统一大圆角 24dp)
 *  - 底部阴影更柔和, 模拟液态悬浮感
 *  - 按压时液态形变 + 光效扩散
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    pressedScale: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val strokeColor = if (isDark) JFColors.GlassDarkStroke else JFColors.GlassLightStroke
    val isPressed by interactionSource.collectIsPressedAsState()

    // 液态玻璃 — 统一大圆角 (更圆润)
    val shape = RoundedCornerShape(cornerRadius)

    val scale by animateFloatAsState(
        targetValue = if (pressedScale && isPressed) 0.92f else 1f,
        animationSpec = HyperOSMotion.buttonPressSpring,
        label = "glassScale"
    )

    val pressGlow by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(
            durationMillis = HyperOSMotion.durationShort,
            easing = HyperOSMotion.lightDiffusion
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
                val radiusPx = cornerRadius.toPx()

                // 第1层: 多层极柔和阴影 (液态悬浮感)
                drawRoundRect(
                    color = JFColors.ShadowOuter,
                    cornerRadius = CornerRadius(radiusPx),
                    size = Size(w + 12f, h + 12f),
                    topLeft = Offset(-6f, -3f)
                )
                drawRoundRect(
                    color = JFColors.ShadowMiddle,
                    cornerRadius = CornerRadius(radiusPx),
                    size = Size(w + 6f, h + 6f),
                    topLeft = Offset(-3f, -1.5f)
                )

                // 第2层: 玻璃底色 — 高透明度 (液态感)
                val baseAlpha = if (isDark) 0.65f else 0.85f
                val baseColor = if (isDark) Color(0xFF0E0E12) else Color.White
                drawRoundRect(
                    color = baseColor.copy(alpha = baseAlpha),
                    cornerRadius = CornerRadius(radiusPx)
                )

                // 第3层: 强折射层 (液态流动感)
                val refractionColor = if (isDark) JFColors.GlassRefractionDark else JFColors.GlassRefractionLight
                drawRoundRect(
                    color = refractionColor,
                    cornerRadius = CornerRadius(radiusPx)
                )

                // 第4层: 顶部反射高光 (specular) — 更强更亮
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassSpecularHighlight,
                            JFColors.GlassSpecularHighlight.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.18f
                    ),
                    cornerRadius = CornerRadius(radiusPx)
                )

                // 第5层: 顶部液态柔光带
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassHighlight,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.40f
                    ),
                    cornerRadius = CornerRadius(radiusPx)
                )

                // 第6层: 液态虹彩边缘 (左右两侧 — 色散增强)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            JFColors.ChromaticAberrationR,
                            JFColors.ChromaticAberrationG.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = w * 0.06f
                    ),
                    cornerRadius = CornerRadius(radiusPx)
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            JFColors.ChromaticAberrationG.copy(alpha = 0.5f),
                            JFColors.ChromaticAberrationB
                        ),
                        startX = w * 0.94f,
                        endX = w
                    ),
                    cornerRadius = CornerRadius(radiusPx)
                )

                // 第7层: 底部液态阴影 (内投影感)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            JFColors.LiquidEdgeShadow
                        ),
                        startY = h * 0.70f,
                        endY = h
                    ),
                    cornerRadius = CornerRadius(radiusPx)
                )

                // 第8层: 按压光效扩散 (液态感 — 光感扩散)
                if (pressGlow > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = pressGlow * (if (isDark) 0.06f else 0.10f)),
                        cornerRadius = CornerRadius(radiusPx)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = pressGlow * 0.15f),
                                Color.White.copy(alpha = pressGlow * 0.05f),
                                Color.Transparent
                            ),
                            radius = size.minDimension * 0.8f
                        ),
                        center = Offset(w / 2f, h / 2f),
                        radius = size.minDimension * 0.8f
                    )
                }
            }
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = if (isPressed) 1.0f else 0.85f),
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
    cornerRadius: Dp = 24.dp,
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
    cornerRadius: Dp = 24.dp,
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
