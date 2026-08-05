package com.jifeng.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 极风工具箱设计 Token v2 — 顶级视觉系统。
 *
 * v2 改进:
 *  - 深色模式背景从 #0E1015 改为 #08080C (更深, 玻璃卡片对比度更高)
 *  - 品牌色微调: #FF6B35 → #FF6B35 (保持不变, 但增加渐变变体)
 *  - 新增 accent 渐变色组 (用于按钮/图标渐变)
 *  - 玻璃色调整: 深色模式 tint 降低透明度让背景透出, 浅色模式提高
 *  - 新增 success/warning/danger/info 的 container 变体
 */
object JFColors {
    // 品牌主色 - 极风橙
    val Brand = Color(0xFFFF6B35)
    val BrandVariant = Color(0xFFFF8E53)
    val BrandContainer = Color(0xFFFFD4C4)
    val BrandGradientStart = Color(0xFFFF6B35)
    val BrandGradientEnd = Color(0xFFFF9F1C)

    // 浅色模式
    val LightBg = Color(0xFFF5F7FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF1F5)
    val LightOnBg = Color(0xFF1A1A1A)
    val LightOnSurface = Color(0xFF2A2A2A)
    val LightOutline = Color(0xFFD8DCE3)

    // 深色模式 (背景更深, 玻璃通透感更强)
    val DarkBg = Color(0xFF08080C)
    val DarkSurface = Color(0xFF101015)
    val DarkSurfaceVariant = Color(0xFF1A1A22)
    val DarkOnBg = Color(0xFFE8E9EC)
    val DarkOnSurface = Color(0xFFD2D4D9)
    val DarkOutline = Color(0xFF2A2A32)

    // 液态玻璃主色 (半透明) — 调整透明度
    val GlassLightTint = Color(0xE6FFFFFF)     // 90% 白
    val GlassDarkTint = Color(0xB3141419)      // 70% 深灰 (降低让背景透出)
    val GlassLightStroke = Color(0x40FFFFFF)   // 25% 白
    val GlassDarkStroke = Color(0x33FFFFFF)    // 20% 白

    // 状态色
    val Success = Color(0xFF4CAF50)
    val SuccessContainer = Color(0xFF1B3A1E)
    val Warning = Color(0xFFFFC107)
    val WarningContainer = Color(0xFF3D341A)
    val Danger = Color(0xFFEF5350)
    val DangerContainer = Color(0xFF3A1B1B)
    val Info = Color(0xFF2196F3)
    val InfoContainer = Color(0xFF1A2533)

    // 功能图标渐变色组 (用于 FeatureTile 的高级感)
    val IconGradientStart = Color(0xFFFF6B35)
    val IconGradientEnd = Color(0xFFFF9F1C)
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
