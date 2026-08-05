package com.jifeng.toolbox.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * HyperOS 风格运动曲线 v2 — 优化过渡动画质感。
 *
 * v2 改进:
 *  - 新增 cardPressSpring: 按压回弹使用更柔和的 spring 参数
 *  - 新增 cardReleaseSpring: 释放时更快回弹 (stiffness 更高)
 *  - 统一 duration 规范到 4 级: 150ms / 300ms / 450ms / 600ms
 *  - 新增 enterExitEasing 用于页面转场
 */
object HyperOSMotion {
    // 缓动曲线 — HyperOS 风格
    val emphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val standardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val decelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val enterExitEasing = CubicBezierEasing(0.35f, 0.0f, 0.0f, 1.0f)

    // 时长规范 — 4 级
    const val durationInstant = 100      // 微交互 (涟漪)
    const val durationShort = 150        // 短过渡 (按压)
    const val durationMedium = 300       // 中等过渡 (卡片悬停)
    const val durationLong = 450         // 长过渡 (页面转场)
    const val durationExtraLong = 600    // 超长过渡 (Hero 入场)

    // 按压弹簧 — 柔和回弹
    val cardPressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // 释放弹簧 — 快速回弹
    val cardReleaseSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // 通用弹簧 — 用于缩放/位移
    val defaultSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
