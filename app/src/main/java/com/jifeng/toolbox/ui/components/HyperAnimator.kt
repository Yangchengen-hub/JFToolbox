package com.jifeng.toolbox.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jifeng.toolbox.ui.theme.HyperOSMotion
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 澎湃OS 4 动画引擎 v4 — 柔光玻璃光效系统。
 */

/**
 * 流动光效 Modifier — 玻璃顶部缓慢流动的微光。
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
            tween(durationMillis = 4000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "flowProgress"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.01f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2500, easing = HyperOSMotion.standardEasing),
            RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    this.drawBehind {
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
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.1f)
        )
    }
}

/**
 * 按压光效 Modifier — 按压时从触点向外扩散。
 */
fun Modifier.hyperPressGlow(
    interactionSource: MutableInteractionSource,
    glowColor: Color = JFColors.Brand
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
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
 * 呼吸光效 Modifier — 玻璃材质微呼吸感。
 */
fun Modifier.hyperBreathingGlow(
    isActive: Boolean = true,
    glowColor: Color = JFColors.Brand
): Modifier = composed {
    if (!isActive) return@composed this
    val infiniteTransition = rememberInfiniteTransition(label = "breathingGlow")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 3000, easing = HyperOSMotion.glassBreathing),
            RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )
    this.drawBehind {
        drawRect(color = glowColor.copy(alpha = breathAlpha))
    }
}
