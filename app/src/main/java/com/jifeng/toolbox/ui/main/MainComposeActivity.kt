package com.jifeng.toolbox.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cpu
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Flaky
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsPower
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceDetector
import com.jifeng.toolbox.core.DeviceInfo
import com.jifeng.toolbox.ui.about.AboutComposeActivity
import com.jifeng.toolbox.ui.browser.BrowserComposeActivity
import com.jifeng.toolbox.ui.components.FeatureTile
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.crypto.CryptoComposeActivity
import com.jifeng.toolbox.ui.downloader.DownloaderComposeActivity
import com.jifeng.toolbox.ui.flash.FlashComposeActivity
import com.jifeng.toolbox.ui.freeze.FreezeComposeActivity
import com.jifeng.toolbox.ui.kernel.KernelComposeActivity
import com.jifeng.toolbox.ui.backup.BackupComposeActivity
import com.jifeng.toolbox.ui.firmware.FirmwareComposeActivity
import com.jifeng.toolbox.ui.installer.InstallerComposeActivity
import com.jifeng.toolbox.ui.terminal.TerminalComposeActivity
import com.jifeng.toolbox.ui.tweak.TweakComposeActivity
import com.jifeng.toolbox.ui.wireless.WirelessDebugComposeActivity
import com.jifeng.toolbox.ui.screenmirror.ScreenMirrorComposeActivity
import com.jifeng.toolbox.usb.UsbDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    @Composable
    private fun MainScreen() {
        JFScaffold { padding ->
            MainContent(Modifier.padding(padding))
        }
    }

    @Composable
    private fun MainContent(modifier: Modifier) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val usbMgr = remember { UsbDeviceManager.get(ctx) }
        val usbState by usbMgr.state.collectAsState()
        var device by remember { mutableStateOf<DeviceInfo?>(null) }
        var loading by remember { mutableStateOf(false) }

        // 启动时自动扫描
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (usbState == UsbDeviceManager.State.DISCONNECTED) usbMgr.scanAndConnect()
        }

        // 状态变化时刷新设备信息
        androidx.compose.runtime.LaunchedEffect(usbState) {
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

        Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // 顶部设备信息卡片
            DeviceHeader(device, usbState, loading, onRefresh = { usbMgr.scanAndConnect() },
                onReboot = { target ->
                    device?.serial?.let { s ->
                        scope.launch { withContext(Dispatchers.IO) { DeviceDetector.reboot(s, target) } }
                    }
                })

            Spacer(Modifier.height(16.dp))
            // 功能栅格
            Text("功能", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(FEATURE_TILES) { tile ->
                    FeatureTile(icon = tile.icon, label = tile.label) {
                        startActivity(Intent(this@MainComposeActivity, tile.target))
                    }
                }
            }
        }
    }

    @Composable
    private fun DeviceHeader(
        device: DeviceInfo?,
        usbState: UsbDeviceManager.State,
        loading: Boolean,
        onRefresh: () -> Unit,
        onReboot: (String) -> Unit
    ) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("设备状态",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onRefresh,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        Text("刷新", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> Text("🔍 正在探测设备...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    device == null -> Text(
                        when (usbState) {
                            UsbDeviceManager.State.REQUESTING -> "⏳ 请在系统弹窗中授权 USB 调试..."
                            UsbDeviceManager.State.CONNECTING -> "🔌 正在建立 ADB 连接..."
                            UsbDeviceManager.State.FAILED -> "❌ 连接失败, 请检查 OTG 线与被控端 USB 调试设置"
                            else -> "⚠️ 未检测到设备\n请通过 OTG 线连接被控设备并授权 USB 调试"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text(device!!.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Android ${device!!.androidVersion} · ${device!!.chipset} · SDK ${device!!.sdkInt}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("厂商: ${device!!.manufacturer} · 主板: ${device!!.board}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Root: ${if (device!!.hasRoot) "✅ 已获取" else "❌ 未获取"} · 模式: ${device!!.connectionMode.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RebootChip("系统", onReboot, "system")
                    RebootChip("Recovery", onReboot, "recovery")
                    RebootChip("Bootloader", onReboot, "bootloader")
                    RebootChip("9008", onReboot, "9008")
                }
            }
        }
    }

    @Composable
    private fun RowScopeReboot(label: String, target: String, onReboot: (String) -> Unit) {
        OutlinedButton(onClick = { onReboot(target) },
            shape = RoundedCornerShape(20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }

    @Composable
    private fun RebootChip(label: String, onReboot: (String) -> Unit, target: String) {
        OutlinedButton(onClick = { onReboot(target) },
            shape = RoundedCornerShape(20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }

    private data class Tile(val label: String, val icon: ImageVector, val target: Class<*>)

    private val FEATURE_TILES = listOf(
        Tile("Fastboot 刷机", Icons.Default.Bolt, FlashComposeActivity::class.java),
        Tile("9008 救砖", Icons.Default.Build, FlashComposeActivity::class.java),
        Tile("分区表编辑", Icons.Default.Storage, FlashComposeActivity::class.java),
        Tile("超级终端", Icons.Default.Terminal, TerminalComposeActivity::class.java),
        Tile("无线调试", Icons.Default.Wifi, WirelessDebugComposeActivity::class.java),
        Tile("屏幕远程", Icons.Default.ScreenShare, ScreenMirrorComposeActivity::class.java),
        Tile("全能下载器", Icons.Default.CloudDownload, DownloaderComposeActivity::class.java),
        Tile("固件下载", Icons.Default.Download, FirmwareComposeActivity::class.java),
        Tile("全能安装器", Icons.Default.Apps, InstallerComposeActivity::class.java),
        Tile("内核刷写", Icons.Default.Cpu, KernelComposeActivity::class.java),
        Tile("一键备份", Icons.Default.Storage, BackupComposeActivity::class.java),
        Tile("智能冻结", Icons.Default.Flaky, FreezeComposeActivity::class.java),
        Tile("玩机工具", Icons.Default.Security, TweakComposeActivity::class.java),
        Tile("加密工具", Icons.Default.EnhancedEncryption, CryptoComposeActivity::class.java),
        Tile("内置浏览器", Icons.Default.Web, BrowserComposeActivity::class.java),
        Tile("Shell 权限", Icons.Default.VerifiedUser, TerminalComposeActivity::class.java),
        Tile("USB 状态", Icons.Default.Usb, WirelessDebugComposeActivity::class.java),
        Tile("关于", Icons.Default.Info, AboutComposeActivity::class.java)
    )
}
