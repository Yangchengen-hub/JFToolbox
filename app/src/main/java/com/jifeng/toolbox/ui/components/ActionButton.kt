package com.jifeng.toolbox.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.HyperOSMotion

/**
 * 玻璃瓷砖按钮 v4 — 柔光玻璃风格。
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.crispBounce,
        label = "btnScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = when {
            pressed -> 0f
            isActive -> 0.2f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = HyperOSMotion.durationShort,
            easing = HyperOSMotion.standardEasing
        ),
        label = "btnGlow"
    )

    val shape = RoundedCornerShape(16.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .drawBehind {
                // 玻璃底色
                val baseAlpha = if (isDark) 0.50f else 0.70f
                val baseColor = if (isDark) Color(0xFF0E0E12) else Color.White
                drawRect(color = baseColor.copy(alpha = baseAlpha))

                // 顶部反射高光
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassSpecularHighlight,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.2f
                    )
                )

                // 活跃态内发光
                if (glowAlpha > 0f) {
                    drawRect(color = JFColors.Brand.copy(alpha = glowAlpha))
                }
            }
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        JFColors.GlassLightStroke.copy(alpha = 0.6f),
                        JFColors.GlassLightStroke.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                enabled = enabled
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) JFColors.Brand
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 玻璃胶囊按钮 — 小型操作。
 */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = HyperOSMotion.crispBounce,
        label = "capsuleScale"
    )

    val shape = RoundedCornerShape(24.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .drawBehind {
                val baseAlpha = if (isDark) 0.45f else 0.60f
                val baseColor = if (isDark) Color(0xFF0E0E12) else Color.White
                drawRect(color = baseColor.copy(alpha = baseAlpha))

                // 顶部微光
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JFColors.GlassSpecularHighlight,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f
                    )
                )

                if (pressed) {
                    drawRect(color = JFColors.Brand.copy(alpha = 0.12f))
                }
            }
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        JFColors.GlassLightStroke.copy(alpha = 0.5f),
                        JFColors.GlassLightStroke.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                enabled = enabled
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
