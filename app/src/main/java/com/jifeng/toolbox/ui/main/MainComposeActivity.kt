package com.jifeng.toolbox.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import com.jifeng.toolbox.core.Logger
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Flaky
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceDetector
import com.jifeng.toolbox.core.DeviceInfo
import com.jifeng.toolbox.ui.about.AboutComposeActivity
import com.jifeng.toolbox.ui.about.PhoneInfoComposeActivity
import com.jifeng.toolbox.ui.components.FeatureTile
import com.jifeng.toolbox.ui.components.GlassCapsuleButton
import com.jifeng.toolbox.ui.components.LiquidGlassBackground
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.crypto.CryptoComposeActivity
import com.jifeng.toolbox.ui.downloader.DownloaderComposeActivity
import com.jifeng.toolbox.ui.flash.FlashComposeActivity
import com.jifeng.toolbox.ui.flash.PartitionEditorComposeActivity
import com.jifeng.toolbox.ui.freeze.FreezeComposeActivity
import com.jifeng.toolbox.ui.kernel.KernelComposeActivity
import com.jifeng.toolbox.ui.backup.BackupComposeActivity
import com.jifeng.toolbox.ui.installer.InstallerComposeActivity
import com.jifeng.toolbox.ui.terminal.TerminalComposeActivity
import com.jifeng.toolbox.ui.usb.UsbManagerComposeActivity
import com.jifeng.toolbox.ui.tweak.TweakComposeActivity
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.ui.theme.HyperOSMotion
import com.jifeng.toolbox.usb.UsbDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.usbDeviceExtra()
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Logger.i("MainUSB", "设备插入: ${device?.deviceName}")
                    device?.let { UsbDeviceManager.get(this@MainComposeActivity).onDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Logger.i("MainUSB", "设备拔出: ${device?.deviceName}")
                    UsbDeviceManager.get(this@MainComposeActivity).onDeviceDetached()
                }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            UsbDeviceManager.get(this).scanAndConnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33)
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else
            getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

    @Composable
    private fun MainScreen() {
        LiquidGlassBackground {
            JFScaffold { padding ->
                MainContent(Modifier.padding(padding))
            }
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

        // 每次回到主页都扫描 (设备可能在别的页面授权/插入)
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    usbMgr.scanAndConnect()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val discovered by usbMgr.discoveredDevices.collectAsState()

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
            HeroHeader()
            Spacer(Modifier.height(16.dp))

            DeviceHeader(
                device = device,
                usbState = usbState,
                discoveredCount = discovered.size,
                discoveredDesc = discovered.firstOrNull()?.let { d ->
                    "vid=0x${d.vendorId.toString(16)} pid=0x${d.productId.toString(16)} · ${usbMgr.getDeviceTypeLabel(d)}"
                },
                loading = loading,
                onRefresh = { usbMgr.scanAndConnect() },
                onManualConnect = {
                    discovered.firstOrNull { usbMgr.isAdbDevice(it) }
                        ?.let { usbMgr.requestPermission(it) }
                        ?: discovered.firstOrNull()?.let { usbMgr.requestPermission(it) }
                },
                onOpenPhoneInfo = {
                    device?.let {
                        PhoneInfoComposeActivity.device = it
                        ctx.startActivity(Intent(ctx, PhoneInfoComposeActivity::class.java))
                    }
                },
                onReboot = { target ->
                    device?.serial?.let { s ->
                        scope.launch { withContext(Dispatchers.IO) { DeviceDetector.reboot(s, target) } }
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(colors = listOf(JFColors.BrandGradientStart, JFColors.BrandGradientEnd))))
                Spacer(Modifier.width(8.dp))
                Text("功能", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
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
    private fun HeroHeader() {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, bottom = 4.dp)) {
            Text("极风工具箱", style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            Text(buildAnnotatedString {
                withStyle(SpanStyle(brush = Brush.horizontalGradient(
                    colors = listOf(JFColors.BrandGradientStart, JFColors.BrandGradientEnd)),
                    fontWeight = FontWeight.Medium)) { append("JF Toolbox · 专业玩机工具集") }
            }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        }
    }

    @Composable
    private fun DeviceHeader(
        device: DeviceInfo?,
        usbState: UsbDeviceManager.State,
        discoveredCount: Int,
        discoveredDesc: String?,
        loading: Boolean,
        onRefresh: () -> Unit,
        onManualConnect: () -> Unit,
        onOpenPhoneInfo: () -> Unit,
        onReboot: (String) -> Unit
    ) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth().clickable(enabled = device != null) { onOpenPhoneInfo() },
            cornerRadius = 24.dp, padding = 16.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.linearGradient(colors = listOf(
                            JFColors.BrandGradientStart.copy(alpha = 0.8f),
                            JFColors.BrandGradientEnd.copy(alpha = 0.6f))))
                        .drawBehind {
                            drawCircle(color = JFColors.Brand.copy(alpha = 0.15f), radius = size.minDimension / 2f + 4f)
                        }) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("设备状态", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(when {
                            loading -> "探测中..."
                            device != null -> "已连接 · 点击查看完整信息"
                            else -> "未连接"
                        }, style = MaterialTheme.typography.bodySmall,
                            color = if (device != null) JFColors.Success else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GlassCapsuleButton(text = "刷新", onClick = onRefresh)
                }

                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(visible = true, enter = fadeIn(tween(HyperOSMotion.durationMedium)), exit = fadeOut(tween(HyperOSMotion.durationMedium))) {
                    when {
                        loading -> Text("正在全量探测设备...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        device == null -> Column {
                            Text(
                                when (usbState) {
                                    UsbDeviceManager.State.REQUESTING -> "请在系统弹窗中授权 USB 调试..."
                                    UsbDeviceManager.State.CONNECTING -> "正在建立 ADB 连接..."
                                    UsbDeviceManager.State.FAILED -> "连接失败, 请检查 OTG 线与被控端 USB 调试开关"
                                    else -> if (discoveredCount > 0) "发现 $discoveredCount 台 USB 设备, 点击右侧「连接」授权"
                                             else "未检测到设备\n请通过 OTG 线连接被控设备并开启 USB 调试"
                                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (discoveredCount > 0 && usbState != UsbDeviceManager.State.REQUESTING &&
                                usbState != UsbDeviceManager.State.CONNECTING) {
                                Spacer(Modifier.height(8.dp))
                                GlassCapsuleButton(if (usbState == UsbDeviceManager.State.FAILED) "重试连接" else "连接设备",
                                    onManualConnect)
                                if (!discoveredDesc.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(discoveredDesc, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        else -> Column {
                            Text(device!!.displayName, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Android ${device!!.androidVersion} · ${device!!.chipset} · SDK ${device!!.sdkInt}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("厂商: ${device!!.manufacturer} · 主板: ${device!!.board}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Root: ${device!!.rootSummary} · 模式: ${device!!.connectionMode.label}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (device != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassCapsuleButton("系统", { onReboot("system") })
                        GlassCapsuleButton("Recovery", { onReboot("recovery") })
                        GlassCapsuleButton("Bootloader", { onReboot("bootloader") })
                        GlassCapsuleButton("9008", { onReboot("9008") })
                    }
                }
            }
        }
    }

    private data class Tile(val label: String, val icon: ImageVector, val target: Class<*>)

    private val FEATURE_TILES = listOf(
        Tile("刷机工具", Icons.Default.Bolt, FlashComposeActivity::class.java),
        Tile("分区表编辑", Icons.Default.Storage, PartitionEditorComposeActivity::class.java),
        Tile("超级终端", Icons.Default.Terminal, TerminalComposeActivity::class.java),
        Tile("全线程下载", Icons.Default.CloudDownload, DownloaderComposeActivity::class.java),
        Tile("全能安装器", Icons.Default.Apps, InstallerComposeActivity::class.java),
        Tile("内核刷写", Icons.Default.Memory, KernelComposeActivity::class.java),
        Tile("一键备份", Icons.Default.Storage, BackupComposeActivity::class.java),
        Tile("智能冻结", Icons.Default.Flaky, FreezeComposeActivity::class.java),
        Tile("玩机工具", Icons.Default.Security, TweakComposeActivity::class.java),
        Tile("加密工具", Icons.Default.EnhancedEncryption, CryptoComposeActivity::class.java),
        Tile("USB 管理", Icons.Default.Usb, UsbManagerComposeActivity::class.java),
        Tile("关于", Icons.Default.Info, AboutComposeActivity::class.java)
    )
}
