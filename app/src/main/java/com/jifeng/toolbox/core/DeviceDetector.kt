package com.jifeng.toolbox.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.jifeng.toolbox.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceDetector {

    private const val TAG = "DeviceDetector"

    fun listUsbDevices(ctx: Context): List<UsbDevice> {
        val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return mgr.deviceList.values.toList()
    }

    suspend fun probeAdbDevice(serial: String): DeviceInfo = withContext(Dispatchers.IO) {
        Logger.i(TAG, "开始全量探测设备 $serial")
        val adb = AdbManager
        val info = DeviceInfo(serial = serial, connectionMode = ConnectionMode.USB_ADB)

        try {
            val props = adb.shell(serial, "getprop").orEmpty()
            val get = { k: String ->
                Regex("\\[$k\\]: \\[(.*?)]").find(props)?.groupValues?.get(1)?.trim().orEmpty()
            }

            val model = get("ro.product.model").ifBlank { "未知" }
            val manufacturer = get("ro.product.manufacturer").ifBlank { "未知" }
            val brand = get("ro.product.brand").ifBlank { "未知" }
            val product = get("ro.product.name").ifBlank { "未知" }
            val device = get("ro.product.device").ifBlank { "未知" }
            val board = get("ro.product.board").ifBlank { "未知" }
            val hardware = get("ro.hardware").ifBlank { "未知" }
            val platform = get("ro.board.platform").ifBlank { "未知" }
            val chipset = guessChipset(platform, hardware, model)
            val androidVer = get("ro.build.version.release").ifBlank { "未知" }
            val sdk = get("ro.build.version.sdk").toIntOrNull() ?: 0
            val buildId = get("ro.build.display.id").ifBlank { get("ro.build.id") }.ifBlank { "未知" }
            val buildType = get("ro.build.type").ifBlank { "未知" }
            val buildTags = get("ro.build.tags").ifBlank { "未知" }
            val fingerprint = get("ro.build.fingerprint").ifBlank { "未知" }
            val buildTime = get("ro.build.date").ifBlank { "未知" }
            val bl = get("ro.bootloader").ifBlank { "未知" }
            val abi = get("ro.product.cpu.abi").ifBlank { "未知" }
            val abi2 = get("ro.product.cpu.abi2").ifBlank { get("ro.product.cpu.abilist").ifBlank { "—" } }
            val language = get("persist.sys.locale").ifBlank { get("ro.product.locale").ifBlank { "—" } }
            val timezone = get("persist.sys.timezone").ifBlank { "—" }

            // 独立 shell 命令
            val kernel = runCmd(serial, "uname -r").ifBlank { "未知" }
            val uptime = runCmd(serial, "uptime").ifBlank { "未知" }.trim()
            val mem = runCmd(serial, "cat /proc/meminfo | head -1").replace("MemTotal:", "").trim().ifBlank { "未知" }
            val temp = runCmd(serial, "cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null").trim().let {
                v -> v.toLongOrNull()?.let { "${it/1000}°C" } ?: "—"
            }
            val batt = runCmd(serial, "dumpsys battery | grep level | head -1").replace("level:", "").trim().ifBlank { "—" }
            val res = runCmd(serial, "wm size").replace("Physical size:", "").trim().ifBlank { "未知" }
            val density = runCmd(serial, "wm density").replace("Physical density:", "").trim().ifBlank { "未知" }
            val wifiIp = runCmd(serial, "ip -4 addr show wlan0 2>/dev/null | grep inet | awk '{print \$2}' | cut -d/ -f1").trim().ifBlank { "—" }
            val mac = runCmd(serial, "cat /sys/class/net/wlan0/address 2>/dev/null").trim().ifBlank { "—" }
            val androidId = runCmd(serial, "settings get secure android_id").trim().ifBlank { "—" }
            val imei = runCmd(serial, "service call iphonesubinfo 1 2>/dev/null | grep -o '[0-9a-f]\\{8\\} ' | awk '{print \$1}'").trim().ifBlank { "无权限" }
            val selinux = runCmd(serial, "getenforce 2>/dev/null").trim().ifBlank { "—" }
            val enc = runCmd(serial, "getprop ro.crypto.state").ifBlank { "—" }
            val treble = runCmd(serial, "getprop ro.treble.enabled").ifBlank { "—" }
            val slot = runCmd(serial, "getprop ro.boot.slot_suffix").ifBlank {
                runCmd(serial, "getprop ro.build.ab_update").ifBlank { "非A/B" }
            }
            val vbmeta = runCmd(serial, "getprop ro.boot.vbmeta.device_state").ifBlank { "—" }
            val sysP = runCmd(serial, "df -h /system 2>/dev/null | tail -1").ifBlank { "—" }
            val venP = runCmd(serial, "df -h /vendor 2>/dev/null | tail -1").ifBlank { "—" }
            val dataP = runCmd(serial, "df -h /data 2>/dev/null | tail -1").ifBlank { "—" }

            val rootStatus = RootDetector.detect(serial)
            val partitions = readPartitions(serial)

            val result = info.copy(
                model = model, manufacturer = manufacturer, brand = brand,
                product = product, device = device, board = board,
                hardware = hardware, platform = platform, chipset = chipset,
                androidVersion = androidVer, sdkInt = sdk,
                buildId = buildId, buildType = buildType, buildTags = buildTags,
                buildFingerprint = fingerprint, buildTime = buildTime,
                bootloader = bl, kernelVersion = kernel, abi = abi, abi2 = abi2,
                language = language, timezone = timezone, uptime = uptime,
                totalMem = mem, cpuTemp = temp, batteryLevel = if (batt.isNotBlank()) "$batt%" else "—",
                resolution = res, density = density,
                wifiIp = wifiIp, macAddr = mac, imei = imei, androidId = androidId,
                selinux = selinux, encryption = enc, treble = treble,
                slot = slot, vbmeta = vbmeta,
                systemPath = sysP, vendorPath = venP, dataPath = dataP,
                hasRoot = rootStatus.hasRoot,
                rootManager = rootStatus.manager.displayName,
                rootVersion = rootStatus.version,
                partitions = partitions
            )
            Logger.i(TAG, "全量探测完成: ${result.shortInfo}, root=${result.rootSummary}, 分区=${partitions.size}")
            result
        } catch (e: Exception) {
            Logger.e(TAG, "探测失败: ${e.message}")
            info.copy(connectionMode = ConnectionMode.NONE)
        }
    }

    private fun runCmd(serial: String, cmd: String): String =
        try { AdbManager.shell(serial, cmd)?.trim().orEmpty() } catch (_: Exception) { "" }

    private fun guessChipset(board: String, hardware: String, model: String): String {
        val src = "$board $hardware $model".lowercase()
        return when {
            src.contains("sm8750") || src.contains("snapdragon 8 elite") -> "Qualcomm Snapdragon 8 Elite"
            src.contains("sm8650") || src.contains("8 gen 3") -> "Qualcomm Snapdragon 8 Gen 3"
            src.contains("sm8550") || src.contains("8 gen 2") -> "Qualcomm Snapdragon 8 Gen 2"
            src.contains("sm8450") || src.contains("8 gen 1") -> "Qualcomm Snapdragon 8 Gen 1"
            src.contains("sm8350") || src.contains("888") -> "Qualcomm Snapdragon 888"
            src.contains("sm7325") || src.contains("778g") -> "Qualcomm Snapdragon 778G"
            src.contains("mt6989") || src.contains("dimensity 9300") -> "MediaTek Dimensity 9300"
            src.contains("mt6983") || src.contains("dimensity 9000") -> "MediaTek Dimensity 9000"
            src.contains("mt6895") || src.contains("dimensity 8200") -> "MediaTek Dimensity 8200"
            src.contains("mt6768") || src.contains("g85") -> "MediaTek Helio G85"
            src.contains("exynos 2400") -> "Samsung Exynos 2400"
            src.contains("exynos 2200") -> "Samsung Exynos 2200"
            src.contains("kirin") -> "HiSilicon Kirin (需loader)"
            board.isNotBlank() -> "疑似 ${board.uppercase()}"
            else -> "未知"
        }
    }

    private fun readPartitions(serial: String): List<Partition> {
        val out = runCmd(serial, "ls -la /dev/block/by-name 2>/dev/null")
        if (out.isBlank()) return emptyList()
        val list = mutableListOf<Partition>()
        val re = Regex("(\\S+) -> /dev/block/(\\S+)")
        for (m in re.findAll(out)) {
            val name = m.groupValues[1]
            val node = m.groupValues[2]
            val size = try {
                runCmd(serial, "blockdev --getsize64 /dev/block/$node 2>/dev/null")
                    .trim().toLongOrNull() ?: 0L
            } catch (_: Exception) { 0L }
            list.add(Partition(name = name, size = size, used = 0, isProtected = PROTECTED.contains(name)))
        }
        return list.sortedBy { it.name }
    }

    private val PROTECTED = setOf("modem", "radio", "persist", "nvdata", "nvram", "aboot", "sbl1", "tz", "rpm", "hboot", "bootloader", "xbl", "abl")

    suspend fun reboot(serial: String, target: String) = withContext(Dispatchers.IO) {
        val cmd = when (target.lowercase()) {
            "recovery" -> "reboot recovery"
            "bootloader", "fastboot" -> "reboot bootloader"
            "9008", "edl" -> "reboot edl"
            else -> "reboot"
        }
        AdbManager.shell(serial, cmd)
        Logger.i(TAG, "$serial -> 重启到 $target")
    }
}
