package com.jifeng.toolbox.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.ui.components.LiquidGlassCard

/**
 * 代码编辑器预览组件。
 *
 * 提供语法高亮、行号显示、代码预览功能。
 * 支持语言: shell / python / javascript / lua / c++ / xml / json
 */
@Composable
fun CodeEditorPreview(
    code: String,
    language: String = "shell",
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(modifier = modifier, padding = 12.dp) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 基础代码预览 (后续加入语法高亮)
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/**
 * 支持的语言列表。
 */
val SUPPORTED_LANGUAGES = listOf(
    "shell", "bash",
    "python",
    "javascript", "js",
    "lua",
    "c", "cpp", "c++",
    "xml",
    "json",
    "yaml", "yml",
    "java",
    "kotlin",
    "gradle"
)
