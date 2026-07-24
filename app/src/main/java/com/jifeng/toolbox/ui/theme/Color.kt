package com.jifeng.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 极风工具箱设计 Token (小米风格 + 液态玻璃适配)。
 * 主色取自 HyperOS 的精神色 (橙红渐变), 背景偏冷调以凸显玻璃通透感。
 */
object JFColors {
    // 品牌主色 - 极风橙 (跨亮暗模式通用)
    val Brand = Color(0xFFFF6B35)
    val BrandVariant = Color(0xFFFF8E53)
    val BrandContainer = Color(0xFFFFD4C4)

    // 浅色模式
    val LightBg = Color(0xFFF5F7FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF1F5)
    val LightOnBg = Color(0xFF1A1A1A)
    val LightOnSurface = Color(0xFF2A2A2A)
    val LightOutline = Color(0xFFD8DCE3)

    // 深色模式 (背景稍带蓝调, 不再纯黑, 卡片更可见)
    val DarkBg = Color(0xFF0E1015)
    val DarkSurface = Color(0xFF15171D)
    val DarkSurfaceVariant = Color(0xFF1F2229)
    val DarkOnBg = Color(0xFFE8E9EC)
    val DarkOnSurface = Color(0xFFD2D4D9)
    val DarkOutline = Color(0xFF2E323B)

    // 液态玻璃主色 (半透明)
    val GlassLightTint = Color(0xD9FFFFFF)     // 85% 白, alpha 略调高提升层次
    val GlassDarkTint = Color(0xCC1C1F26)      // 80% 深灰, 稍亮让卡片更可见
    val GlassLightStroke = Color(0x33FFFFFF)   // 20% 白
    val GlassDarkStroke = Color(0x33FFFFFF)

    // 状态色
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFC107)
    val Danger = Color(0xFFEF5350)
    val Info = Color(0xFF2196F3)
}

val LightColorScheme = lightColorScheme(
    primary = JFColors.Brand,
    onPrimary = Color.White,
    primaryContainer = JFColors.BrandContainer,
    onPrimaryContainer = Color(0xFF3D1100),
    secondary = JFColors.BrandVariant,
    onSecondary = Color.White,
    background = JFColors.LightBg,
    onBackground = JFColors.LightOnBg,
    surface = JFColors.LightSurface,
    onSurface = JFColors.LightOnSurface,
    surfaceVariant = JFColors.LightSurfaceVariant,
    outline = JFColors.LightOutline,
    error = JFColors.Danger,
    onError = Color.White
)

val DarkColorScheme = darkColorScheme(
    primary = JFColors.Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5D2200),
    onPrimaryContainer = JFColors.BrandContainer,
    secondary = JFColors.BrandVariant,
    onSecondary = Color.White,
    background = JFColors.DarkBg,
    onBackground = JFColors.DarkOnBg,
    surface = JFColors.DarkSurface,
    onSurface = JFColors.DarkOnSurface,
    surfaceVariant = JFColors.DarkSurfaceVariant,
    outline = JFColors.DarkOutline,
    error = JFColors.Danger,
    onError = Color.White
)

@Composable
fun JFTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = JFTypography.tokens,
        content = content
    )
}
