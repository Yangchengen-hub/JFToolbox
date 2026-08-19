package com.jifeng.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 极风工具箱设计 Token v5 — 液态玻璃 (Liquid Glass) 设计语言。
 *
 * v5 液态玻璃改进:
 *  - 更高透明度: 浅色85%白 / 深色65%深色底 (更多背景透出)
 *  - 更强折射效果: 多层折射模拟液态流动感
 *  - 液态流动感边框高光: 上边缘更亮, 侧边缘带虹彩流动
 *  - 更圆润的圆角 (统一大圆角)
 *  - 环境色渗透系统增强
 */
object JFColors {
    // ── 品牌主色 — 中性渐变 ──
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

    // ── 深色模式 — 纯粹深灰 ──
    val DarkBg = Color(0xFF0E0E10)
    val DarkSurface = Color(0xFF141416)
    val DarkSurfaceVariant = Color(0xFF1A1A1E)
    val DarkOnBg = Color(0xFFE6E6E8)
    val DarkOnSurface = Color(0xFFCCCCCE)
    val DarkOutline = Color(0xFF28282E)

    // ── 液态玻璃材质色 v5 (Liquid Glass) ──
    // 浅色: 85% 白 (高透明度, 液态感)
    val GlassLightTint = Color(0xD8FFFFFF)     // ~85% 白
    // 深色: 65% 深色底 (更高透明度)
    val GlassDarkTint = Color(0xA60E0E12)       // ~65% 深色底
    // 边缘高光 — 液态流动感 (更亮的上边缘, 渐变到两侧)
    val GlassLightStroke = Color(0x4DFFFFFF)    // 30% 白 — 更亮的液态边缘
    val GlassDarkStroke = Color(0x3DFFFFFF)     // 24% 白
    // 折射层模拟色 — 更强折射 (液态流动感)
    val GlassRefractionLight = Color(0x14FFFFFF) // 8% 白 — 强折射层
    val GlassRefractionDark = Color(0x0DFFFFFF)  // 5% 白
    // 高光叠加
    val GlassSpecularHighlight = Color(0x26FFFFFF) // 15% 白 — 反射高光 (更强)
    val GlassHighlight = Color(0x1AFFFFFF)       // 10% 白 — 顶部柔光带
    val GlassPressedLight = Color(0x12FFFFFF)    // 7% 白
    val GlassPressedDark = Color(0x0AFFFFFF)     // 4% 白
    // 色散模拟 (液态虹彩边缘 — 更明显)
    val ChromaticAberrationR = Color(0x0FFF0000)  // 6% 红色偏移
    val ChromaticAberrationG = Color(0x0800FF00)  // 3% 绿色偏移
    val ChromaticAberrationB = Color(0x0F0000FF)  // 6% 蓝色偏移
    // 液态流动边框高光 (内描边渐变)
    val LiquidEdgeHighlight = Color(0x33FFFFFF)   // 20% 白 — 流动高光
    val LiquidEdgeShadow = Color(0x1A000000)      // 10% 黑 — 底部阴影

    // ── 环境色渗透系统 — 壁纸色彩感知 ──
    val AmbientTintWarm = Color(0x0DFFD6A0)
    val AmbientTintCool = Color(0x0DA0D6FF)
    val AmbientTintGreen = Color(0x0DA0FFD6)
    val AmbientTintRose = Color(0x0DFFA0D6)

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

    // ── 多层阴影色 ──
    val ShadowOuter = Color(0x0D000000)     // 5% 黑 — 外层柔阴影
    val ShadowMiddle = Color(0x0A000000)    // 4% 黑
    val ShadowInner = Color(0x07000000)     // 3% 黑 — 最内层
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
