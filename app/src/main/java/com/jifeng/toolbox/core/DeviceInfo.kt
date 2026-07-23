package com.jifeng.toolbox.core

import android.os.Build

/**
 * 连接的被控设备信息模型。
 */
data class DeviceInfo(
    val serial: String = "",
    val model: String = "未知",
    val manufacturer: String = "未知",
    val product: String = "未知",
    val board: String = "未知",
    val chipset: String = "未知",
    val androidVersion: String = "未知",
    val sdkInt: Int = 0,
    val bootloader: String = "未知",
    val connectionMode: ConnectionMode = ConnectionMode.NONE,
    val hasRoot: Boolean? = null,
    val partitions: List<Partition> = emptyList()
) {
    val displayName: String
        get() = if (model != "未知") "$manufacturer $model" else serial.ifBlank { "未命名设备" }

    val shortInfo: String
        get() = "$displayName · Android $androidVersion · ${connectionMode.label}"
}

enum class ConnectionMode(val label: String) {
    NONE("未连接"),
    USB_ADB("USB ADB"),
    USB_FASTBOOT("Fastboot"),
    USB_9008("9008 EDL"),
    WIRELESS_ADB("无线 ADB");

    companion object {
        fun fromMode(s: String): ConnectionMode = when (s.lowercase()) {
            "adb" -> USB_ADB
            "fastboot" -> USB_FASTBOOT
            "edl", "9008" -> USB_9008
            "wireless" -> WIRELESS_ADB
            else -> NONE
        }
    }
}

data class Partition(
    val name: String,
    val size: Long,
    val used: Long,
    val filesystem: String = "",
    val mountPoint: String = "",
    val isProtected: Boolean = false
)
