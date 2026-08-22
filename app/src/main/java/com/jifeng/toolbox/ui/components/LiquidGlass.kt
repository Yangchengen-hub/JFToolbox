package com.jifeng.toolbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * 真·液态玻璃卡片 v3。
 *
 * 多层组合:
 *  1. 底层: 对角渐变 (左上微亮 → 右下微暗), 模拟玻璃折射
 *  2. 中层: 主题 surface 半透明, 形成玻璃厚度
 *  3. 顶部 1px 高光线 (从左到右渐隐), 模拟上边缘反光
 *  4. 外边框: 极细半透明白边 + 内描边感 (通过 border 完成)
 *
 * 全部使用纯 Compose draw 实现, 不依赖 RenderEffect (兼容 API 21+)。
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    padding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.red > 0.5f
    // 根据明暗模式调整玻璃层颜色
    val baseTop = if (isLight) Color.White.copy(alpha = 0.55f) else Color(0xFF2A2D3A).copy(alpha = 0.55f)
    val baseBottom = if (isLight) Color.White.copy(alpha = 0.25f) else Color(0xFF14161F).copy(alpha = 0.55f)
    val highlight = if (isLight) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f)
    val borderColor = if (isLight) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f)

    Box(modifier = modifier) {
        // 底层: 对角折射渐变 (通过 drawBehind 自己画)
        Surface(
            modifier = Modifier.fillMaxWidth().drawBehind {
                val r = cornerRadius.toPx()
                // 边缘环境光: 左上亮、右下暗
                val glassBrush = Brush.linearGradient(
                    colors = listOf(baseTop, baseBottom),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRoundRect(glassBrush, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                // 顶部 1.5px 高光线
                val hLine = Brush.horizontalGradient(
                    0f to highlight,
                    0.5f to highlight.copy(alpha = highlight.alpha * 0.4f),
                    1f to Color.Transparent
                )
                drawRoundRect(
                    hLine,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.4f.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
                // 左上角高光团
                val glow = Brush.radialGradient(
                    colors = listOf(
                        (if (isLight) Color.White else Color(0xFF6FA8FF)).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.15f),
                    radius = size.minDimension * 0.65f
                )
                drawRoundRect(glow, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            },
            shape = shape,
            // 中层: surface 半透明 + 边框
            color = scheme.surface.copy(alpha = 0.35f),
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
 * 全局液态玻璃背景。
 *
 * 用多层径向渐变叠加模拟 Apple Liquid Glass 海报感:
 *  - 背景底色
 *  - 左上、右下、中部三个彩色光斑
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
                        Color(0xFFF0F3FA),
                        Color(0xFFE6EBF5),
                        Color(0xFFDDE3F0)
                    ) else listOf(
                        Color(0xFF0A0B12),
                        Color(0xFF0E1020),
                        Color(0xFF141828)
                    )
                )
            )
            .drawBehind {
                fun glow(c: Color, x: Float, y: Float, r: Float, alpha: Float) {
                    val b = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = alpha), Color.Transparent),
                        center = Offset(size.width * x, size.height * y),
                        radius = size.minDimension * r
                    )
                    drawRect(b)
                }
                if (isLight) {
                    glow(JFColors.BrandGradientStart, 0.15f, 0.1f, 0.9f, 0.35f)
                    glow(JFColors.BrandGradientEnd, 0.9f, 0.85f, 0.9f, 0.28f)
                    glow(Color(0xFFFFD9A8), 0.6f, 0.4f, 0.7f, 0.20f)
                } else {
                    glow(JFColors.BrandGradientStart, 0.18f, 0.15f, 0.95f, 0.30f)
                    glow(JFColors.BrandGradientEnd, 0.85f, 0.8f, 0.95f, 0.28f)
                    glow(Color(0xFF7B61FF), 0.55f, 0.55f, 0.7f, 0.18f)
                }
            }
    ) {
        content()
    }
}

/**
 * 玻璃胶囊按钮 v2: 带顶部高光。
 */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = JFColors.Brand.copy(alpha = 0.14f),
        border = BorderStroke(0.8f.dp, JFColors.Brand.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
 * 可点击的液态玻璃卡片 v3。
 */
@Composable
fun LiquidGlassClickableCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    padding: Dp = 0.dp,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.red > 0.5f
    val baseTop = if (isLight) Color.White.copy(alpha = 0.55f) else Color(0xFF2A2D3A).copy(alpha = 0.55f)
    val baseBottom = if (isLight) Color.White.copy(alpha = 0.25f) else Color(0xFF14161F).copy(alpha = 0.55f)
    val highlight = if (isLight) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f)
    val borderColor = if (isLight) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f)

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().drawBehind {
                val r = cornerRadius.toPx()
                drawRoundRect(
                    Brush.linearGradient(
                        colors = listOf(baseTop, baseBottom),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
                drawRoundRect(
                    Brush.horizontalGradient(
                        0f to highlight,
                        0.5f to highlight.copy(alpha = highlight.alpha * 0.4f),
                        1f to Color.Transparent
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.4f.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
                drawRoundRect(
                    Brush.radialGradient(
                        colors = listOf(
                            (if (isLight) Color.White else Color(0xFF6FA8FF)).copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.minDimension * 0.65f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            },
            shape = shape,
            color = scheme.surface.copy(alpha = 0.35f),
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
