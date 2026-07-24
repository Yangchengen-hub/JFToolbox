package com.jifeng.toolbox.ui.freeze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard

class FreezeComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreezeScreen() }
    }
}

@Composable
private fun FreezeScreen() {
    val items = remember { mutableStateListOf<Pair<String, Boolean>>() }
    var loaded by remember { mutableStateOf(false) }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("智能冻结", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("通过 pm disable / pm uninstall 冻结指定应用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column {
                    Button(onClick = {
                        items.clear()
                        val serial = AdbManager.listDevices().firstOrNull()
                        if (serial == null) {
                            items.add("(无设备)" to false)
                        } else {
                            // 列出第三方包 (pm list packages -3)
                            val out = AdbManager.shell(serial, "pm list packages -3") ?: ""
                            out.lines().filter { it.startsWith("package:") }.forEach {
                                items.add(it.removePrefix("package:") to false)
                            }
                        }
                        loaded = true
                    }) { Text("列出第三方应用") }
                    Spacer(Modifier.height(12.dp))
                    if (loaded) {
                        Text("共 ${items.size} 个应用", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(items) { (pkg, frozen) ->
                                Text("• $pkg" + if (frozen) " [已冻结]" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
