package com.jifeng.toolbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 液态玻璃 (Liquid Glass) 容器。
 *
 * 实现策略 (兼容 minSdk 24):
 *   1. 半透明色调叠加 (Compose 渲染, 不依赖 RenderEffect which requires API 31)
 *   2. blur 作为可选增强 (API 31+ 才有效, 低版本降级为半透明)
 *   3. 1px 渐变 stroke 模拟玻璃边缘高光
 *   4. 圆角 + 内层 padding 模拟深度
 *
 * 真正的 backdrop blur 在 API < 31 无法实现, 这里用半透明 + 渐变描边达到视觉等效。
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 24.dp,
    tintAlpha: Float = 0.65f,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val baseTint = if (isDark) JFColors.GlassDarkTint else JFColors.GlassLightTint
    val strokeColor = if (isDark) JFColors.GlassDarkStroke else JFColors.GlassLightStroke
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .blur(blurRadius)
            .drawBehind {
                // 半透明背景
                drawRect(color = baseTint.copy(alpha = tintAlpha))
                // 顶部高光渐变 (模拟玻璃反光)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.4f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = size.height * 0.5f
                    )
                )
            }
            .border(width = 1.dp, brush = Brush.verticalGradient(
                colors = listOf(strokeColor, Color.Transparent, strokeColor)
            ), shape = shape)
            .padding(0.dp)
    ) {
        content()
    }
}

/**
 * 液态玻璃卡片 (带默认 padding)。
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    LiquidGlassBox(
        modifier = modifier.padding(padding.dp * 0),
        cornerRadius = cornerRadius,
        tintAlpha = 0.7f
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
