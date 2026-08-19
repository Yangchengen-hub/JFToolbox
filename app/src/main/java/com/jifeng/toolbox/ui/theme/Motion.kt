package com.jifeng.toolbox.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 澎湃OS 4 (HyperOS 4, 2026)「生命感动效」运动曲线 v5。
 *
 * 关键曲线:
 *  - emphasizedDecelerate(0.05, 0.0, 0.1, 1.0) — 强调减速
 *  - standardEasing(0.2, 0.0, 0.0, 1.0) — 标准缓动
 *
 * 新增:
 *  - 光感扩散动画曲线 (lightDiffusion)
 *  - 按钮按压弹性曲线 (buttonPressSpring / buttonReleaseSpring)
 *  - 页面转场曲线 (pageTransition)
 *
 * 时长:
 *  - micro = 60ms
 *  - short = 150ms
 *  - medium = 280ms
 *  - long = 450ms
 *  - hero = 600ms
 */
object HyperOSMotion {
    // ── 核心缓动曲线 ──
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.0f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.9f, 0.0f, 1.0f, 0.0f)
    val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val enterExitEasing = CubicBezierEasing(0.35f, 0.0f, 0.0f, 1.0f)
    val microEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // ── 新增曲线 ──
    /** 光感扩散 — 用于按钮长按、光效扩散, 先快后慢的柔和扩散 */
    val lightDiffusion = CubicBezierEasing(0.1f, 0.0f, 0.25f, 1.0f)
    /** 按钮按压弹性曲线 — 快速按下 + 轻微回弹 */
    val buttonPressEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** 页面转场曲线 — 流畅的进入退出 */
    val pageTransition = CubicBezierEasing(0.15f, 0.0f, 0.15f, 1.0f)
    /** 玻璃呼吸缓动 — 用于玻璃材质的微妙形变 */
    val glassBreathing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // ── 时长定义 v5 ──
    const val durationMicro = 60
    const val durationShort = 150
    const val durationMedium = 280
    const val durationLong = 450
    const val durationHero = 600

    // ── 弹性动画 ──
    /** 按钮按压弹性 — 按压缩放 0.92, 松手弹性回弹 */
    val buttonPressSpring = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 600f
    )
    val buttonReleaseSpring = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium
    )
    val softBounce = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium
    )
    val crispBounce = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium
    )
    val floatSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )
    /** 玻璃弹性 — 用于玻璃容器按压 */
    val glassPressSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    val cardPressSpring = softBounce
    val cardReleaseSpring = crispBounce
    val defaultSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    /** 长按光效扩散弹性 */
    val longPressGlowSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )
}
