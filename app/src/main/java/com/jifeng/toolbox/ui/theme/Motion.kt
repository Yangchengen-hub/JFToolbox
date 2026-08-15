package com.jifeng.toolbox.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 澎湃OS 4「生命感动效」运动曲线 v4。
 */
object HyperOSMotion {
    val emphasizedDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.7f, 0.0f)
    val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val enterExitEasing = CubicBezierEasing(0.35f, 0.0f, 0.0f, 1.0f)
    val microEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    /** 玻璃呼吸缓动 — 用于玻璃材质的微妙形变 */
    val glassBreathing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    const val durationMicro = 80
    const val durationShort = 180
    const val durationMedium = 320
    const val durationLong = 500
    const val durationHero = 700

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
}
