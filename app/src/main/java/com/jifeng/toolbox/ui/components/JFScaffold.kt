package com.jifeng.toolbox.ui.components

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
 * 应用壳 v4 — 环境自适应柔光玻璃框架。
 *
 * v4 改进:
 *  - 移除固定蓝色渐变, 改为极微弱的中性环境光
 *  - 使用多层径向渐变模拟壁纸色彩自然渗透
 *  - 透明度更低, 让背景色彩更好地透出
 */
@Composable
fun JFScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    JFTheme {
        val isDark = MaterialTheme.colorScheme.background.red < 0.5f
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
                        val ambientAlpha = if (isDark) 0.03f else 0.015f
                        // 顶部微弱氛围光
                        val topGlow = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.02f else 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, -size.height * 0.1f),
                            radius = size.width * 0.8f
                        )
                        // 右下角微弱暖色渗透
                        val warmGlow = Brush.radialGradient(
                            colors = listOf(
                                JFColors.AmbientTintWarm.let { 
                                    if (isDark) Color(0xFF2A2010).copy(alpha = ambientAlpha) 
                                    else Color(0xFFFFF0D0).copy(alpha = ambientAlpha)
                                },
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.9f, size.height * 0.9f),
                            radius = size.width * 0.5f
                        )
                        // 左上角微弱冷色渗透
                        val coolGlow = Brush.radialGradient(
                            colors = listOf(
                                JFColors.AmbientTintCool.let {
                                    if (isDark) Color(0xFF101828).copy(alpha = ambientAlpha)
                                    else Color(0xFFD0E8FF).copy(alpha = ambientAlpha)
                                },
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.1f, size.height * 0.1f),
                            radius = size.width * 0.4f
                        )
                        onDrawBehind {
                            drawRect(topGlow)
                            drawRect(warmGlow)
                            drawRect(coolGlow)
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
