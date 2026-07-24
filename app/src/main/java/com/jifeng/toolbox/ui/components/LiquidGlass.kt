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
 * 修复方案: 使用分层架构
 *   1. 底层: 半透明背景 + 可选 blur (仅影响背景)
 *   2. 上层: 内容层 (无 blur, 保持清晰)
 *   3. 边框: 1px 渐变 stroke 模拟玻璃边缘高光
 *
 * 关键修复: blur 只应用在背景层, 不影响内容
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 12.dp,
    tintAlpha: Float = 0.55f,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val baseTint = if (isDark) JFColors.GlassDarkTint else JFColors.GlassLightTint
    val strokeColor = if (isDark) JFColors.GlassDarkStroke else JFColors.GlassLightStroke
    val shape = RoundedCornerShape(cornerRadius)

    Box(modifier = modifier.clip(shape)) {
        // ===== 背景层 (模糊效果只在这里) =====
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius)
                .drawBehind {
                    drawRect(color = baseTint.copy(alpha = tintAlpha))
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.06f else 0.3f),
                                Color.White.copy(alpha = 0.0f)
                            ),
                            startY = 0f,
                            endY = size.height * 0.5f
                        )
                    )
                }
        )

        // ===== 边框层 =====
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 1.dp, brush = Brush.verticalGradient(
                    colors = listOf(strokeColor, Color.Transparent, strokeColor)
                ), shape = shape)
        )

        // ===== 内容层 (无 blur, 保持清晰) =====
        Box(modifier = Modifier.matchParentSize()) {
            content()
        }
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
        modifier = modifier,
        cornerRadius = cornerRadius,
        tintAlpha = 0.6f
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
