package com.jifeng.toolbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 真·液态玻璃 v4 — HyperOS 4 风格, 清澈透底。
 *
 * 设计要点:
 *  - 表面主色 alpha 0.08~0.12 (几乎透明, 让背景强烈透出)
 *  - 顶部 1px 白色高光线 (玻璃上边缘反光)
 *  - 内部对角极淡折射渐变 (模拟玻璃厚度)
 *  - 边框: 上/左亮, 下/右暗 (玻璃边缘光差)
 *  - 左上角径向高光 (光源反射)
 *  - 不依赖任何 blur API, 纯 Compose draw, minSdk 21 兼容
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    padding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.red > 0.5f

    // 清澈透底: 主表面 alpha 极低
    val surfaceAlpha = if (isLight) 0.10f else 0.08f
    val surfaceColor = if (isLight) Color.White else Color.White
    val topHighlight = Color.White.copy(alpha = if (isLight) 0.7f else 0.25f)
    val edgeGlow = Color.White.copy(alpha = if (isLight) 0.5f else 0.12f)
    val refractionTop = if (isLight) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)
    val refractionBottom = if (isLight) Color.Black.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.12f)
    val radialGlow = if (isLight) Color.White.copy(alpha = 0.18f) else Color(0xFF6FA8FF).copy(alpha = 0.10f)
    val borderColor = if (isLight) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f)

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().drawBehind {
                val r = cornerRadius.toPx()
                val cr = androidx.compose.ui.geometry.CornerRadius(r, r)

                // 1. 极淡对角折射渐变 (玻璃厚度感)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(refractionTop, refractionBottom),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = cr
                )

                // 2. 顶部高光线 (上边缘反光, 1.2px)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to topHighlight,
                        0.4f to topHighlight.copy(alpha = topHighlight.alpha * 0.5f),
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.2f.dp.toPx()),
                    cornerRadius = cr
                )

                // 3. 左侧高光线
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to edgeGlow,
                        0.5f to edgeGlow.copy(alpha = edgeGlow.alpha * 0.3f),
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(0.8f.dp.toPx(), size.height),
                    cornerRadius = cr
                )

                // 4. 左上角径向高光 (光源反射点)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(radialGlow, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.minDimension * 0.7f
                    ),
                    cornerRadius = cr
                )

                // 5. 底部暗边 (玻璃厚度阴影)
                val bottomShadow = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.18f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to bottomShadow,
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, size.height - 0.8f.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 0.8f.dp.toPx()),
                    cornerRadius = cr
                )
            },
            shape = shape,
            color = surfaceColor.copy(alpha = surfaceAlpha),
            border = BorderStroke(0.8f.dp, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding),
                content = content
            )
        }
    }
}

/**
 * 全局液态玻璃背景 v4 — HyperOS 4 风格。
 *
 * 深色基底 + 多色径向光斑, 让玻璃卡片"透"出色彩。
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.red > 0.5f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isLight) listOf(
                        Color(0xFFE8ECF5),
                        Color(0xFFDDE3F0),
                        Color(0xFFD5DCEC)
                    ) else listOf(
                        Color(0xFF06070C),
                        Color(0xFF0A0D1A),
                        Color(0xFF0E1224)
                    )
                )
            )
            .drawBehind {
                fun glow(c: Color, x: Float, y: Float, r: Float, alpha: Float) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(c.copy(alpha = alpha), Color.Transparent),
                            center = Offset(size.width * x, size.height * y),
                            radius = size.minDimension * r
                        )
                    )
                }
                if (isLight) {
                    glow(Color(0xFF6B7BFF), 0.12f, 0.08f, 1.0f, 0.28f)
                    glow(Color(0xFF8B9CFF), 0.92f, 0.88f, 1.0f, 0.22f)
                    glow(Color(0xFFFFB88C), 0.55f, 0.45f, 0.8f, 0.15f)
                    glow(Color(0xFF8CE0FF), 0.8f, 0.2f, 0.7f, 0.12f)
                } else {
                    glow(Color(0xFF4B5BFF), 0.15f, 0.12f, 1.1f, 0.35f)
                    glow(Color(0xFF7B6BFF), 0.88f, 0.85f, 1.0f, 0.30f)
                    glow(Color(0xFFB06BFF), 0.5f, 0.5f, 0.8f, 0.20f)
                    glow(Color(0xFF4B9BFF), 0.82f, 0.25f, 0.7f, 0.15f)
                    glow(Color(0xFFFF7BA8), 0.18f, 0.8f, 0.6f, 0.12f)
                }
            }
    ) {
        content()
    }
}

/**
 * 玻璃胶囊按钮 v4 — HyperOS 4 风格, 清澈通透。
 */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.red > 0.5f
    val bgAlpha = if (isLight) 0.18f else 0.12f
    val strokeAlpha = if (isLight) 0.4f else 0.20f

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = JFColors.Brand.copy(alpha = bgAlpha),
        border = BorderStroke(0.8f.dp, JFColors.Brand.copy(alpha = strokeAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text,
                style = MaterialTheme.typography.labelLarge,
                color = JFColors.Brand,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 可点击的液态玻璃卡片 v4。
 */
@Composable
fun LiquidGlassClickableCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    padding: Dp = 0.dp,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.red > 0.5f

    val surfaceAlpha = if (isLight) 0.10f else 0.08f
    val surfaceColor = Color.White
    val topHighlight = Color.White.copy(alpha = if (isLight) 0.7f else 0.25f)
    val edgeGlow = Color.White.copy(alpha = if (isLight) 0.5f else 0.12f)
    val refractionTop = if (isLight) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)
    val refractionBottom = if (isLight) Color.Black.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.12f)
    val radialGlow = if (isLight) Color.White.copy(alpha = 0.18f) else Color(0xFF6FA8FF).copy(alpha = 0.10f)
    val borderColor = if (isLight) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f)

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().drawBehind {
                val r = cornerRadius.toPx()
                val cr = androidx.compose.ui.geometry.CornerRadius(r, r)
                drawRoundRect(
                    Brush.linearGradient(
                        colors = listOf(refractionTop, refractionBottom),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = cr
                )
                drawRoundRect(
                    Brush.horizontalGradient(
                        0f to topHighlight,
                        0.4f to topHighlight.copy(alpha = topHighlight.alpha * 0.5f),
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.2f.dp.toPx()),
                    cornerRadius = cr
                )
                drawRoundRect(
                    Brush.verticalGradient(
                        0f to edgeGlow,
                        0.5f to edgeGlow.copy(alpha = edgeGlow.alpha * 0.3f),
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(0.8f.dp.toPx(), size.height),
                    cornerRadius = cr
                )
                drawRoundRect(
                    Brush.radialGradient(
                        colors = listOf(radialGlow, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.minDimension * 0.7f
                    ),
                    cornerRadius = cr
                )
                val bottomShadow = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.18f)
                drawRoundRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to bottomShadow,
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, size.height - 0.8f.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 0.8f.dp.toPx()),
                    cornerRadius = cr
                )
            },
            shape = shape,
            color = surfaceColor.copy(alpha = surfaceAlpha),
            border = BorderStroke(0.8f.dp, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding),
                content = content
            )
        }
    }
}
