package com.jifeng.toolbox.ui.kernel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFColors

class KernelComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KernelScreen() }
    }
}

@Composable
private fun KernelScreen() {
    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("内核级刷写", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("高危操作检测, 按风险分级提示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            val levels = listOf(
                Triple("低风险", "如刷写 boot.img 同版本", JFColors.Success),
                Triple("中风险", "如替换 init.rc / 修改 sepolicy", JFColors.Warning),
                Triple("高风险", "如刷写不同版本的 vbmeta / dtbo", Color(0xFFFF9800)),
                Triple("致命风险", "如刷写 xbl / abl / modem 等底层分区", JFColors.Danger)
            )
            levels.forEach { (name, desc, color) ->
                RiskCard(name, desc, color)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("操作前必读:", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("• 致命风险操作可能永久变砖, 需 9008 救砖能力备份\n• 请确保已备份当前分区\n• 请确保救砖包就绪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = JFColors.Danger),
                        modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("我已了解风险, 继续", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskCard(name: String, desc: String, color: Color) {
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.padding(end = 4.dp).height(40.dp)
                .clip(RoundedCornerShape(4.dp)).background(color)) {}
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    color = color, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
