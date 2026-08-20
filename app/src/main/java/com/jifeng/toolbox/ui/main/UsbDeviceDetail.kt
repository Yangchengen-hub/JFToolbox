package com.jifeng.toolbox.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFColors
import com.jifeng.toolbox.usb.UsbDeviceManager

/**
 * USB 设备详情卡片 — 显示设备列表、选择和连接详情。
 *
 * 功能:
 * - 列出所有已连接 USB 设备
 * - 显示设备基本信息 (VID/PID/厂商/产品/接口)
 * - 支持手动选择设备并请求权限
 * - 显示当前连接状态和详情
 */
@Composable
fun UsbDeviceDetailCard(
    usbManager: UsbDeviceManager,
    modifier: Modifier = Modifier
) {
    val devices by usbManager.discoveredDevices.collectAsState(initial = emptyList<android.hardware.usb.UsbDevice>())
    val state by usbManager.state.collectAsState(initial = UsbDeviceManager.State.DISCONNECTED)
    val attached by usbManager.attachedDevice.collectAsState(initial = null)

    LiquidGlassCard(modifier = modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // USB 图标
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
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
                        tint = Color.White,
                        modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("USB 设备管理",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "已发现 ${devices.size} 个设备 · 状态: ${state.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (devices.isEmpty()) {
                Text(
                    "未检测到 USB 设备\n请通过 OTG 线连接设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    val isCurrent = attached?.deviceName == device.deviceName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                device.deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "VID: 0x${device.vendorId.toString(16).uppercase()} " +
                                "· PID: 0x${device.productId.toString(16).uppercase()} " +
                                "· ${usbManager.getDeviceTypeLabel(device)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "接口数: ${device.interfaceCount}" +
                                (if (device.manufacturerName != null) " · ${device.manufacturerName}" else "") +
                                (if (device.productName != null) " · ${device.productName}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrent) {
                            Text(
                                "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = JFColors.Success,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
