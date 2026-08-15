package com.jifeng.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 极风工具箱设计 Token v4 — 澎湃OS 4 柔光玻璃设计语言 (环境自适应)。
 *
 * v4 改进:
 *  - 移除固定蓝色调, 改为环境自适应的中性基底
 *  - 玻璃透明度降低: 浅色70%, 深色50%, 让更多壁纸色彩透出
 *  - 新增环境色渗透系统 (AmbientTint)
 *  - 阴影系统完全自定义, 不使用Material默认阴影
 */
object JFColors {
    // ── 品牌主色 — 中性渐变 (不绑定特定色相) ──
    val Brand = Color(0xFF6B7BFF)
    val BrandVariant = Color(0xFF8B9CFF)
    val BrandContainer = Color(0xFFD4DBFF)
    val BrandGradientStart = Color(0xFF6B7BFF)
    val BrandGradientEnd = Color(0xFF8B9CFF)

    // ── 浅色模式 — 中性暖白基底 ──
    val LightBg = Color(0xFFF5F5F0)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFECEEE8)
    val LightOnBg = Color(0xFF1A1C1E)
    val LightOnSurface = Color(0xFF242628)
    val LightOutline = Color(0xFFD8DAE0)

    // ── 深色模式 — 纯粹深灰 (非蓝黑) ──
    val DarkBg = Color(0xFF0E0E10)
    val DarkSurface = Color(0xFF141416)
    val DarkSurfaceVariant = Color(0xFF1A1A1E)
    val DarkOnBg = Color(0xFFE6E6E8)
    val DarkOnSurface = Color(0xFFCCCCCE)
    val DarkOutline = Color(0xFF28282E)

    // ── 柔光玻璃材质色 v4 (环境自适应) ──
    // 浅色: 70% 白 (更低不透明度, 更多背景透出)
    val GlassLightTint = Color(0xB3FFFFFF)     // 70% 白
    // 深色: 50% 深色底 (更多背景透出)
    val GlassDarkTint = Color(0x800E0E12)       // 50% 深色底
    // 边缘高光
    val GlassLightStroke = Color(0x33FFFFFF)    // 20% 白 — 更亮的边缘
    val GlassDarkStroke = Color(0x29FFFFFF)     // 16% 白
    // 折射层模拟色
    val GlassRefractionLight = Color(0x0AFFFFFF) // 4% 白 — 折射层
    val GlassRefractionDark = Color(0x06FFFFFF)  // 2.5% 白
    // 高光叠加
    val GlassSpecularHighlight = Color(0x1FFFFFFF) // 12% 白 — 反射高光
    val GlassHighlight = Color(0x14FFFFFF)       // 8% 白 — 顶部柔光
    val GlassPressedLight = Color(0x0DFFFFFF)    // 5% 白
    val GlassPressedDark = Color(0x08FFFFFF)     // 3% 白
    // 色散模拟 (边缘微弱的彩虹偏移)
    val ChromaticAberrationR = Color(0x08FF0000)  // 微弱红色偏移
    val ChromaticAberrationB = Color(0x080000FF)  // 微弱蓝色偏移

    // ── 环境色渗透系统 — 壁纸色彩感知 ──
    val AmbientTintWarm = Color(0x0DFFD6A0)    // 暖色调渗透
    val AmbientTintCool = Color(0x0DA0D6FF)    // 冷色调渗透
    val AmbientTintGreen = Color(0x0DA0FFD6)   // 绿色调渗透
    val AmbientTintRose = Color(0x0DFFA0D6)    // 玫瑰调渗透

    // ── AI 感色辅助色板 ──
    val AiSenseBlue = Color(0xFF4FC3F7)
    val AiSensePurple = Color(0xFFCE93D8)
    val AiSenseTeal = Color(0xFF80CBC4)
    val AiSenseAmber = Color(0xFFFFD54F)
    val AiSenseRose = Color(0xFFF48FB1)
    val AiSenseIndigo = Color(0xFF9FA8DA)
    val AiSenseBlueContainer = Color(0xFF0D2137)
    val AiSensePurpleContainer = Color(0xFF2A1533)
    val AiSenseTealContainer = Color(0xFF0F2B28)
    val AiSenseAmberContainer = Color(0xFF332B0F)
    val AiSenseRoseContainer = Color(0xFF331525)
    val AiSenseIndigoContainer = Color(0xFF1A1C33)

    // ── 状态色 ──
    val Success = Color(0xFF43A6CB)
    val SuccessContainer = Color(0xFF0F2A33)
    val Warning = Color(0xFFD4A843)
    val WarningContainer = Color(0xFF332C14)
    val Danger = Color(0xFFD45B5B)
    val DangerContainer = Color(0xFF331616)
    val Info = Color(0xFF4A90D9)
    val InfoContainer = Color(0xFF12203A)

    // ── 功能图标渐变色组 ──
    val IconGradientStart = Color(0xFF6B7BFF)
    val IconGradientEnd = Color(0xFF8B9CFF)

    // ── 多层阴影色 (非Material默认) ──
    val ShadowOuter = Color(0x0A000000)     // 2.5% 黑 — 最外层极柔阴影
    val ShadowMiddle = Color(0x08000000)    // 3% 黑
    val ShadowInner = Color(0x05000000)     // 2% 黑 — 最内层
}

val LightColorScheme = lightColorScheme(
    primary = JFColors.Brand,
    onPrimary = Color.White,
    primaryContainer = JFColors.BrandContainer,
    onPrimaryContainer = Color(0xFF1A1A40),
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
    primaryContainer = Color(0xFF282863),
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
