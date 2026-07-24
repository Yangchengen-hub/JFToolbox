package com.jifeng.toolbox.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * HyperOS 动画规范。
 * 核心: 强调 "弹性+阻尼" 的物理感, 强调 "超快入场+慢出退场"。
 * 参考 MIUI/HyperOS 动效团队公开曲线文档。
 */
object HyperOSEasing {
    // 标准 ease (sharp 入场)
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    // 强调 ease (有冲击感)
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    // 减速 (慢出)
    val Decelerated = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    // 加速 (快出)
    val Accelerated = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
}

/**
 * HyperOS 时长规范 (单位 ms)。
 */
object HyperOSDuration {
    const val Instant = 100     // 微交互 (按钮反馈)
    const val Quick = 200       // 状态切换
    const val Normal = 300      // 标准过渡
    const val Slow = 450        // 页面切换
    const val Slowest = 600     // 大型动画
}

/**
 * 预设动画规格。
 */
object HyperOSMotion {
    /** 卡片按压回弹 (spring)。 */
    val cardPressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
    /** 页面转场 (fade + slide)。 */
    fun <T> pageTransition() = tween<T>(HyperOSDuration.Slow, easing = HyperOSEasing.Emphasized)
    /** 列表项入场。 */
    fun <T> listItemEnter(delay: Int = 0) = tween<T>(HyperOSDuration.Normal, delay, HyperOSEasing.Standard)
}
