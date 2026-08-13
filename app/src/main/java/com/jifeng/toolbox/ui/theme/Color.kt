package com.jifeng.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 极风工具箱设计 Token v3 — 澎湃OS 4 柔光玻璃设计语言。
 *
 * v3 改进 (澎湃OS 4 柔光玻璃):
 *  - 品牌色从橙色(#FF6B35)改为小米蓝渐变系(#2979FF → #448AFF)
 *  - 深色模式背景改为更纯粹的深蓝黑(#0A0B10)
 *  - 柔光玻璃材质色: 浅色白底85%透明, 深色底65%透明
 *  - 新增"AI 感色"辅助色板 — 用于卡片根据内容动态调色
 *  - 状态色调整色相使其更融入蓝色系
 */
object JFColors {
    // ── 品牌主色 - 小米蓝渐变系 ──
    val Brand = Color(0xFF2979FF)
    val BrandVariant = Color(0xFF448AFF)
    val BrandContainer = Color(0xFFC4DFFF)
    val BrandGradientStart = Color(0xFF2979FF)
    val BrandGradientEnd = Color(0xFF448AFF)

    // ── 浅色模式 ──
    val LightBg = Color(0xFFF2F5FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE8EDF4)
    val LightOnBg = Color(0xFF1A1C2E)
    val LightOnSurface = Color(0xFF242638)
    val LightOutline = Color(0xFFD0D8E8)

    // ── 深色模式 — 纯粹深蓝黑 ──
    val DarkBg = Color(0xFF0A0B10)
    val DarkSurface = Color(0xFF0F1018)
    val DarkSurfaceVariant = Color(0xFF161822)
    val DarkOnBg = Color(0xFFE4E6F0)
    val DarkOnSurface = Color(0xFFCDD0DE)
    val DarkOutline = Color(0xFF22243A)

    // ── 柔光玻璃材质色 (澎湃OS 4) ──
    val GlassLightTint = Color(0xD9FFFFFF)     // 85% 白 — 柔和半透明
    val GlassDarkTint = Color(0xA60E0F18)       // 65% 深色底 — 更多背景透出
    val GlassLightStroke = Color(0x26FFFFFF)    // 15% 白 — 边缘高光线条
    val GlassDarkStroke = Color(0x1FFFFFFF)     // 12% 白 — 深色边缘微光
    // 高光叠加色
    val GlassHighlight = Color(0x14FFFFFF)      // 8% 白 — 顶部柔光
    val GlassPressedLight = Color(0x0DFFFFFF)   // 5% 白 — 按压透光
    val GlassPressedDark = Color(0x08FFFFFF)    // 3% 白 — 深色按压透光

    // ── AI 感色辅助色板 — 卡片根据内容动态调色 ──
    val AiSenseBlue = Color(0xFF4FC3F7)
    val AiSensePurple = Color(0xFFCE93D8)
    val AiSenseTeal = Color(0xFF80CBC4)
    val AiSenseAmber = Color(0xFFFFD54F)
    val AiSenseRose = Color(0xFFF48FB1)
    val AiSenseIndigo = Color(0xFF9FA8DA)
    // AI 感色容器 (深色底)
    val AiSenseBlueContainer = Color(0xFF0D2137)
    val AiSensePurpleContainer = Color(0xFF2A1533)
    val AiSenseTealContainer = Color(0xFF0F2B28)
    val AiSenseAmberContainer = Color(0xFF332B0F)
    val AiSenseRoseContainer = Color(0xFF331525)
    val AiSenseIndigoContainer = Color(0xFF1A1C33)

    // ── 状态色 — 融入蓝色系 ──
    val Success = Color(0xFF43A6CB)
    val SuccessContainer = Color(0xFF0F2A33)
    val Warning = Color(0xFFD4A843)
    val WarningContainer = Color(0xFF332C14)
    val Danger = Color(0xFFD45B5B)
    val DangerContainer = Color(0xFF331616)
    val Info = Color(0xFF4A90D9)
    val InfoContainer = Color(0xFF12203A)

    // ── 功能图标渐变色组 ──
    val IconGradientStart = Color(0xFF2979FF)
    val IconGradientEnd = Color(0xFF448AFF)
}

val LightColorScheme = lightColorScheme(
    primary = JFColors.Brand,
    onPrimary = Color.White,
    primaryContainer = JFColors.BrandContainer,
    onPrimaryContainer = Color(0xFF001A40),
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
    primaryContainer = Color(0xFF003063),
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
