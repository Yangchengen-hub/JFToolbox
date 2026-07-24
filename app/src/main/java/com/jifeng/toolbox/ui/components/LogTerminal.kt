package com.jifeng.toolbox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 可视化日志终端 - 用于刷机/救砖实时输出。
 */
@Composable
fun LogTerminal(
    lines: List<String>,
    progress: Float? = null,
    progressLabel: String? = null,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(modifier = modifier.fillMaxWidth(), padding = 12.dp) {
        if (progress != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(progressLabel ?: "", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = lines.joinToString("\n").ifBlank { "等待输出..." },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
