package com.jifeng.toolbox.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.JFTheme

/**
 * 应用壳 v3 — 澎湃OS 4 柔光玻璃框架。
 *
 * v3 改进:
 *  - 背景层加入微妙的渐变网格 (模拟壁纸色彩渗透感)
 *  - 使用小米蓝渐变色系作为氛围光
 *  - 多层径向渐变模拟壁纸色彩渗透
 */
@Composable
fun JFScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    JFTheme {
        val isDark = MaterialTheme.colorScheme.background.red < 0.5f
        val brandAlpha = if (isDark) 0.06f else 0.03f
        val accentAlpha = if (isDark) 0.04f else 0.02f
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        // 渐变网格 — 模拟壁纸色彩渗透感
                        // 顶部品牌色渐变
                        val topGradient = Brush.verticalGradient(
                            colors = listOf(
                                JFColors.Brand.copy(alpha = brandAlpha),
                                Color.Transparent,
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.35f
                        )
                        // 右上角辅助色渐变 (紫色渗透)
                        val rightGlow = Brush.radialGradient(
                            colors = listOf(
                                JFColors.AiSensePurple.copy(alpha = accentAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.9f, size.height * 0.15f),
                            radius = size.width * 0.4f
                        )
                        // 左下角辅助色渐变 (蓝色渗透)
                        val leftGlow = Brush.radialGradient(
                            colors = listOf(
                                JFColors.AiSenseBlue.copy(alpha = accentAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.1f, size.height * 0.85f),
                            radius = size.width * 0.35f
                        )
                        onDrawBehind {
                            drawRect(topGradient)
                            drawRect(rightGlow)
                            drawRect(leftGlow)
                        }
                    }
            ) {
                Scaffold(
                    topBar = topBar,
                    bottomBar = bottomBar,
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.statusBars,
                    content = content
                )
            }
        }
    }
}
