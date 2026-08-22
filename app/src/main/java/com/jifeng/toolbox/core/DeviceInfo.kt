package com.jifeng.toolbox.core

import android.os.Build

/**
 * 连接的被控设备信息模型 v2 — 全量"关于手机"数据。
 */
data class DeviceInfo(
    val serial: String = "",
    val model: String = "未知",
    val manufacturer: String = "未知",
    val brand: String = "未知",
    val product: String = "未知",
    val device: String = "未知",
    val board: String = "未知",
    val hardware: String = "未知",
    val chipset: String = "未知",
    val platform: String = "未知",
    val androidVersion: String = "未知",
    val sdkInt: Int = 0,
    val buildId: String = "未知",
    val buildType: String = "未知",
    val buildTags: String = "未知",
    val buildFingerprint: String = "未知",
    val buildTime: String = "未知",
    val bootloader: String = "未知",
    val kernelVersion: String = "未知",
    val abi: String = "未知",
    val abi2: String = "未知",
    val language: String = "未知",
    val timezone: String = "未知",
    val uptime: String = "未知",
    val totalMem: String = "未知",
    val cpuTemp: String = "未知",
    val batteryLevel: String = "未知",
    val resolution: String = "未知",
    val density: String = "未知",
    val wifiIp: String = "未知",
    val macAddr: String = "未知",
    val imei: String = "未知",
    val androidId: String = "未知",
    val selinux: String = "未知",
    val encryption: String = "未知",
    val treble: String = "未知",
    val slot: String = "未知",
    val vbmeta: String = "未知",
    val systemPath: String = "未知",
    val vendorPath: String = "未知",
    val dataPath: String = "未知",
    val connectionMode: ConnectionMode = ConnectionMode.NONE,
    val hasRoot: Boolean? = null,
    val rootManager: String = "",
    val rootVersion: String = "",
    val partitions: List<Partition> = emptyList()
) {
    val displayName: String
        get() = if (model != "未知") "$manufacturer $model" else serial.ifBlank { "未命名设备" }

    val shortInfo: String
        get() = "$displayName · Android $androidVersion · ${connectionMode.label}"

    val rootSummary: String
        get() = when {
            hasRoot != true -> "无 Root"
            rootManager.isNotBlank() -> "$rootManager${if (rootVersion.isNotBlank()) " v$rootVersion" else ""}"
            else -> "已 Root (通用)"
        }

    /** 结构化分组, 供"关于手机"页渲染。 */
    fun grouped(): List<Pair<String, List<Pair<String, String>>>> = listOf(
        "设备标识" to listOf(
            "品牌" to brand,
            "制造商" to manufacturer,
            "型号" to model,
            "产品名" to product,
            "设备代号" to device,
            "主板" to board,
            "硬件" to hardware,
            "主板平台" to platform,
            "芯片" to chipset,
            "序列号" to serial
        ),
        "系统信息" to listOf(
            "Android 版本" to androidVersion,
            "SDK 版本" to if (sdkInt > 0) sdkInt.toString() else "未知",
            "Build 号" to buildId,
            "构建类型" to buildType,
            "构建标签" to buildTags,
            "编译时间" to buildTime,
            "Bootloader" to bootloader,
            "内核版本" to kernelVersion,
            "Fingerprint" to buildFingerprint,
            "SELinux" to selinux,
            "加密状态" to encryption,
            "Treble" to treble,
            "当前槽位" to slot,
            "VBMeta" to vbmeta
        ),
        "Root 状态" to listOf(
            "Root 权限" to when (hasRoot) { true -> "已获取"; false -> "未获取"; null -> "未知" },
            "Root 管理器" to rootManager.ifBlank { "—" },
            "Root 版本" to rootVersion.ifBlank { "—" }
        ),
        "硬件运行时" to listOf(
            "CPU 架构" to abi,
            "第二 ABI" to abi2,
            "运行内存" to totalMem,
            "CPU 温度" to cpuTemp,
            "电池电量" to batteryLevel,
            "屏幕分辨率" to resolution,
            "屏幕密度" to density
        ),
        "网络与标识" to listOf(
            "Wi-Fi IP" to wifiIp,
            "MAC 地址" to macAddr,
            "Android ID" to androidId,
            "IMEI" to imei,
            "语言/时区" to "$language / $timezone"
        ),
        "存储挂载" to listOf(
            "/system" to systemPath,
            "/vendor" to vendorPath,
            "/data" to dataPath,
            "运行时长" to uptime
        )
    )
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
