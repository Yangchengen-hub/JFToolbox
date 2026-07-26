package com.jifeng.toolbox.ui.wireless

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent { JFTheme { PairingOverlayScreen() } }
    }

    companion object {
        private const val TAG = "PairingOverlay"
    }
}

@Composable
private fun PairingOverlayScreen() {
    val ctx = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Link, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text("无线调试配对", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { (ctx as ComponentActivity).finish() }) {
                Icon(Icons.Default.Close, contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("被控端: 设置 → 开发者选项 → 无线调试 → 使用配对码配对设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = ip, onValueChange = { ip = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("IP 地址") },
                    singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = port, onValueChange = { port = it },
                        modifier = Modifier.weight(1f), label = { Text("配对端口") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = code, onValueChange = { code = it },
                        modifier = Modifier.weight(1f), label = { Text("6 位配对码") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                Button(onClick = {
                    if (ip.isBlank() || port.isBlank() || code.length != 6) return@Button
                    isPairing = true
                    result = null
                    scope.launch {
                        val (ok, msg) = withContext(Dispatchers.IO) {
                            AdbManager.pair(ip, port.toInt(), code)
                        }
                        isPairing = false
                        result = if (ok) "✓ $msg" else "✗ $msg"
                    }
                }, enabled = !isPairing && ip.isNotBlank() && port.isNotBlank() && code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPairing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Text("配对")
                    }
                }

                result?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error)
                }
            }
        }

        Button(onClick = {
            ctx.startActivity(Intent(ctx, WirelessDebugComposeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            (ctx as ComponentActivity).finish()
        }) { Text("打开完整无线调试页面") }
    }
}