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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.JFTheme

/**
 * 应用壳 v2 — 顶级视觉框架。
 *
 * v2 改进:
 *  - 新增背景渐变 (顶到底的微妙渐变, 增加纵深氛围)
 *  - 使用 windowInsetsPadding(statusBars) 替代 systemBarsPadding (更精确)
 *  - 状态栏区域透明, 背景渐变延伸到状态栏
 */
@Composable
fun JFScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    JFTheme {
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
                        // 背景渐变 — 从顶部微亮到底部深色, 营造空间纵深
                        val gradient = Brush.verticalGradient(
                            colors = listOf(
                                JFColors.Brand.copy(alpha = 0.03f),
                                Color.Transparent,
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.3f
                        )
                        onDrawBehind {
                            drawRect(gradient)
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
