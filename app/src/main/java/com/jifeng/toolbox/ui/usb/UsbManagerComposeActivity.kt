package com.jifeng.toolbox.ui.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceDetector
import com.jifeng.toolbox.core.DeviceInfo
import com.jifeng.toolbox.ui.components.GlassCapsuleButton
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.usb.UsbDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbManagerComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JFScaffold { UsbManagerScreen() } }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                    UsbDeviceManager.get(this@UsbManagerComposeActivity).scanAndConnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED ->
                    UsbDeviceManager.get(this@UsbManagerComposeActivity).onDeviceDetached()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }
}

@Composable
private fun UsbManagerScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val usbMgr = remember { UsbDeviceManager.get(ctx) }
    val usbState by usbMgr.state.collectAsState()
    var device by remember { mutableStateOf<DeviceInfo?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (usbState == UsbDeviceManager.State.DISCONNECTED) usbMgr.scanAndConnect()
    }

    LaunchedEffect(usbState) {
        if (usbState == UsbDeviceManager.State.CONNECTED) {
            loading = true
            scope.launch {
                device = withContext(Dispatchers.IO) {
                    AdbManager.listDevices().firstOrNull()?.let {
                        DeviceDetector.probeAdbDevice(it)
                    }
                }
                loading = false
            }
        } else {
            device = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                JFColors.BrandGradientStart.copy(alpha = 0.8f),
                                JFColors.BrandGradientEnd.copy(alpha = 0.6f)
                            )
                        )
                    )
            ) {
                Icon(Icons.Default.Usb, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column {
                Text("USB 设备管理",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("管理已连接的 USB 设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("连接状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                when {
                    loading -> Text("正在探测设备...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    device == null -> Text(
                        when (usbState) {
                            UsbDeviceManager.State.REQUESTING -> "请在系统弹窗中授权 USB 调试..."
                            UsbDeviceManager.State.CONNECTING -> "正在建立 ADB 连接..."
                            UsbDeviceManager.State.FAILED -> "连接失败，请检查 OTG 线与被控端设置"
                            else -> "未检测到设备\n请通过 OTG 线连接被控设备"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        InfoRow("设备", device!!.displayName)
                        InfoRow("Android", "Android ${device!!.androidVersion} (SDK ${device!!.sdkInt})")
                        InfoRow("芯片", device!!.chipset)
                        InfoRow("厂商", device!!.manufacturer)
                        InfoRow("主板", device!!.board)
                        InfoRow("Root", if (device!!.hasRoot == true) "已获取" else "未获取")
                        InfoRow("模式", device!!.connectionMode.label)
                        device!!.serial?.let { InfoRow("序列号", it) }
                    }
                }
            }
        }

        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快捷操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                if (device != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassCapsuleButton(text = "重启系统", onClick = {
                            scope.launch { withContext(Dispatchers.IO) { device?.serial?.let { DeviceDetector.reboot(it, "system") } } }
                        })
                        GlassCapsuleButton(text = "Recovery", onClick = {
                            scope.launch { withContext(Dispatchers.IO) { device?.serial?.let { DeviceDetector.reboot(it, "recovery") } } }
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassCapsuleButton(text = "Bootloader", onClick = {
                            scope.launch { withContext(Dispatchers.IO) { device?.serial?.let { DeviceDetector.reboot(it, "bootloader") } } }
                        })
                        GlassCapsuleButton(text = "EDL 9008", onClick = {
                            scope.launch { withContext(Dispatchers.IO) { device?.serial?.let { DeviceDetector.reboot(it, "9008") } } }
                        })
                    }
                } else {
                    Text("连接设备后可使用快捷操作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                GlassCapsuleButton(text = "刷新", onClick = { usbMgr.scanAndConnect() })
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
