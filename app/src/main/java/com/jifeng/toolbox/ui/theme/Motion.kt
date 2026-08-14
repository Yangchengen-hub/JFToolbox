package com.jifeng.toolbox.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 澎湃OS 4「生命感动效」运动曲线 v3。
 *
 * v3 改进 (澎湃OS 4 柔光玻璃):
 *  - 引入「生命感动效」曲线体系
 *  - 主缓动: emphasizedDecelerate 用于进入, emphasizedAccelerate 用于退出
 *  - 弹簧系统: softBounce / crispBounce / floatSpring
 *  - 时长规范调整为 5 级: micro(80ms) / short(180ms) / medium(320ms) / long(500ms) / hero(700ms)
 */
object HyperOSMotion {
    // ── 缓动曲线 — 澎湃OS 4 生命感动效 ──
    /** 进入主缓动: 快速启动 → 柔和减速到位 (emphasizedDecelerate) */
    val emphasizedDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    /** 退出主缓动: 缓慢启动 → 快速消失 (emphasizedAccelerate) */
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.7f, 0.0f)
    /** 标准缓动: 用于常规位移/缩放 */
    val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** 页面转场缓动 */
    val enterExitEasing = CubicBezierEasing(0.35f, 0.0f, 0.0f, 1.0f)
    /** 微交互缓动: 用于按钮/小元素 */
    val microEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // ── 时长规范 — 5 级 ──
    const val durationMicro = 80       // 微交互 (涟漪、颜色变化)
    const val durationShort = 180      // 短过渡 (按压、缩放)
    const val durationMedium = 320     // 中等过渡 (卡片展开、页面元素)
    const val durationLong = 500       // 长过渡 (页面转场、Hero 入场)
    const val durationHero = 700       // 超长 Hero 动画 (首屏大动画)

    // ── 弹簧系统 — 澎湃OS 4 ──
    /** softBounce: 卡片按压 — 柔和弹性 (dampingRatio=0.7, stiffness=Medium) */
    val softBounce = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium
    )
    /** crispBounce: 按钮释放 — 清脆弹性 (dampingRatio=0.6, stiffness=Medium) */
    val crispBounce = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium
    )
    /** floatSpring: 悬浮元素 — 轻柔漂浮 (dampingRatio=0.8, stiffness=Low) */
    val floatSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )

    // ── 兼容别名 (保持与已有组件的兼容性) ──
    val cardPressSpring = softBounce
    val cardReleaseSpring = crispBounce
    val defaultSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
