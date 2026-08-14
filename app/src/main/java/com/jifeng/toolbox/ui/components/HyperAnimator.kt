package com.jifeng.toolbox.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jifeng.toolbox.ui.theme.HyperOSMotion
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 澎湃OS 4 动画引擎 v3。
 *
 * v3 改进:
 *  - 更新动画引擎使用新的 Motion 曲线
 *  - 新增光效动画 modifier (流动光效跟随手势)
 *  - 按压光效跟随、呼吸光效等
 */

/**
 * 流动光效 Modifier — 模拟澎湃OS 4 的柔光流动效果。
 * 光效在卡片顶部缓慢流动，创造生命感。
 */
fun Modifier.hyperFlowingLight(
    isActive: Boolean = true,
    lightColor: Color = JFColors.Brand
): Modifier = composed {
    if (!isActive) return@composed this
    val infiniteTransition = rememberInfiniteTransition(label = "flowingLight")
    val flowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "flowProgress"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = 2000,
                easing = HyperOSMotion.standardEasing
            ),
            RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    this.drawBehind {
        // 顶部流动光带
        val lightX = size.width * flowProgress
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    lightColor.copy(alpha = glowAlpha),
                    Color.Transparent
                ),
                startX = lightX - size.width * 0.3f,
                endX = lightX + size.width * 0.3f
            ),
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.15f)
        )
    }
}

/**
 * 按压光效 Modifier — 按压时局部光效扩散。
 * 配合 HyperOSMotion 弹簧系统使用。
 */
fun Modifier.hyperPressGlow(
    interactionSource: MutableInteractionSource,
    glowColor: Color = JFColors.Brand
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.12f else 0f,
        animationSpec = tween(
            durationMillis = HyperOSMotion.durationShort,
            easing = HyperOSMotion.emphasizedDecelerate
        ),
        label = "pressGlow"
    )
    val glowRadius by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = HyperOSMotion.softBounce,
        label = "pressRadius"
    )
    this.drawBehind {
        if (glowAlpha > 0.001f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    radius = size.minDimension * 0.5f * glowRadius
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.minDimension * 0.5f * glowRadius
            )
        }
    }
}

/**
 * 呼吸光效 Modifier — 缓慢呼吸式光效，用于强调活跃状态。
 */
fun Modifier.hyperBreathingGlow(
    isActive: Boolean = true,
    glowColor: Color = JFColors.Brand
): Modifier = composed {
    if (!isActive) return@composed this
    val infiniteTransition = rememberInfiniteTransition(label = "breathingGlow")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = 2500,
                easing = HyperOSMotion.standardEasing
            ),
            RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )
    this.drawBehind {
        drawRect(
            color = glowColor.copy(alpha = breathAlpha)
        )
    }
}
