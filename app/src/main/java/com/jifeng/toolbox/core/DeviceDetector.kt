package com.jifeng.toolbox.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.jifeng.toolbox.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 OTG/USB 探测被控设备：识别型号、芯片、连接模式。
 * MVP 阶段优先走 ADB shell 抓取信息；fastboot/edl 模式做基础识别。
 */
object DeviceDetector {

    private const val TAG = "DeviceDetector"

    fun listUsbDevices(ctx: Context): List<UsbDevice> {
        val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return mgr.deviceList.values.toList()
    }

    suspend fun probeAdbDevice(serial: String): DeviceInfo = withContext(Dispatchers.IO) {
        Logger.i(TAG, "开始探测设备 $serial")
        val adb = AdbManager
        val info = DeviceInfo(serial = serial, connectionMode = ConnectionMode.USB_ADB)

        try {
            // getprop 是 Android 全版本通用的标准接口
            val props = adb.shell(serial, "getprop").orEmpty()
            val get = { k: String ->
                Regex("$k: \\[(.*?)]").find(props)?.groupValues?.get(1)?.trim().orEmpty()
            }

            val model = get("ro.product.model").ifBlank { "未知" }
            val manufacturer = get("ro.product.manufacturer").ifBlank { "未知" }
            val product = get("ro.product.name").ifBlank { "未知" }
            val board = get("ro.product.board").ifBlank { "未知" }
            val chipset = guessChipset(get("ro.board.platform"), get("ro.hardware"), model)
            val androidVer = get("ro.build.version.release").ifBlank { "未知" }
            val sdk = get("ro.build.version.sdk").toIntOrNull() ?: 0
            val bl = get("ro.bootloader").ifBlank { "未知" }

            // Root / 管理器检测（Magisk / KernelSU / APatch 自动适配）
            val rootStatus = RootDetector.detect(serial)

            // 分区表
            val partitions = readPartitions(serial)

            val result = info.copy(
                model = model, manufacturer = manufacturer, product = product,
                board = board, chipset = chipset, androidVersion = androidVer,
                sdkInt = sdk, bootloader = bl,
                hasRoot = rootStatus.hasRoot,
                rootManager = rootStatus.manager.displayName,
                rootVersion = rootStatus.version,
                partitions = partitions
            )
            Logger.i(TAG, "探测完成: ${result.shortInfo}, root=${result.rootSummary}, 分区=${partitions.size}")
            result
        } catch (e: Exception) {
            Logger.e(TAG, "探测失败: ${e.message}")
            info.copy(connectionMode = ConnectionMode.NONE)
        }
    }

    private fun guessChipset(board: String, hardware: String, model: String): String {
        val src = "$board $hardware $model".lowercase()
        return when {
            src.contains("sm8650") || src.contains("snapdragon 8 gen 3") -> "Qualcomm Snapdragon 8 Gen 3"
            src.contains("sm8550") || src.contains("snapdragon 8 gen 2") -> "Qualcomm Snapdragon 8 Gen 2"
            src.contains("sm8450") || src.contains("snapdragon 8 gen 1") -> "Qualcomm Snapdragon 8 Gen 1"
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
        val adb = AdbManager
        val out = adb.shell(serial, "ls -la /dev/block/by-name 2>/dev/null").orEmpty()
        if (out.isBlank()) return emptyList()
        val list = mutableListOf<Partition>()
        val re = Regex("(\\S+) -> /dev/block/(\\S+)")
        for (m in re.findAll(out)) {
            val name = m.groupValues[1]
            val node = m.groupValues[2]
            val size = try {
                adb.shell(serial, "blockdev --getsize64 /dev/block/$node 2>/dev/null")
                    .orEmpty().trim().toLongOrNull() ?: 0L
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
        Logger.i(TAG, "$serial → 重启到 $target")
    }
}
