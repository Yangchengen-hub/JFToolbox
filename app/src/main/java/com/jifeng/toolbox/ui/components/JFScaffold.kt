package com.jifeng.toolbox.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jifeng.toolbox.ui.theme.JFTheme

/**
 * 应用壳: 主题 + 系统栏内边距 + 玻璃背景。
 */
@Composable
fun JFScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    JFTheme {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                containerColor = MaterialTheme.colorScheme.background,
                content = content
            )
        }
    }
}
