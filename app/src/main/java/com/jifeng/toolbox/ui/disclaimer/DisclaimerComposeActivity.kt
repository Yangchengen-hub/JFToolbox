package com.jifeng.toolbox.ui.disclaimer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFTheme

class DisclaimerComposeActivity : ComponentActivity() {

    companion object {
        const val PREFS = "jf_disclaimer"
        const val KEY_ACCEPTED = "accepted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)) {
            startActivity(Intent(this, com.jifeng.toolbox.ui.main.MainComposeActivity::class.java))
            finish(); return
        }
        setContent { JFTheme { DisclaimerScreen(onAccept = { accept() }, onExit = { finishAffinity() }) } }
    }

    private fun accept() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ACCEPTED, true).apply()
        startActivity(Intent(this, com.jifeng.toolbox.ui.main.MainComposeActivity::class.java))
        finish()
    }
}

@Composable
private fun DisclaimerScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(64.dp))
            Text("免责声明", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("使用极风工具箱前, 请仔细阅读:", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    val items = listOf(
                        "1. 刷机/解锁/写分区为高危操作, 可能导致设备变砖、数据丢失、失去保修。",
                        "2. 本软件仅供技术研究与学习, 不得用于商业或非法用途。",
                        "3. 9008/EDL 操作直接写底层分区表, 错误刷写可致设备永久损坏。",
                        "4. 您对使用本软件产生的一切后果自行承担, 作者与极风工作室不承担任何责任。",
                        "5. 继续使用即视为您已阅读、理解并同意以上全部条款。"
                    )
                    items.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("我已阅读并同意", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("不同意, 退出", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
