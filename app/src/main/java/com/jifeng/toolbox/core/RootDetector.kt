package com.jifeng.toolbox.core

import com.jifeng.toolbox.adb.AdbManager

/**
 * Root / 管理器检测器 —— 执行前自动检测并适配路径。
 *
 * 支持 Magisk / KernelSU / APatch 三大主流方案：
 * - 通过 ADB shell 检测 `su` 二进制、`uid=0`、各管理器特征路径与命令。
 * - 自动推断当前管理器类型，为后续命令选择正确的 su 调用前缀与模块路径。
 *
 * 所有方法均在 ADB 连接存活时通过 shell 远程探测被控设备，
 * 不依赖本机 Root。
 */
object RootDetector {

    private const val TAG = "RootDetector"

    /** 识别到的 Root 管理器类型。 */
    enum class RootManager(val displayName: String, val suPrefix: String) {
        MAGISK("Magisk", "su -c"),
        KERNELSU("KernelSU", "su -c"),
        APATCH("APatch", "su -c"),
        GENERIC_ROOT("通用 Root", "su -c"),
        NONE("无 Root", "");

        val isAvailable: Boolean get() = this != NONE
    }

    /** 完整的 Root 检测结果。 */
    data class RootStatus(
        val manager: RootManager = RootManager.NONE,
        val hasSu: Boolean = false,
        val uidZero: Boolean = false,
        val version: String = "",
        val moduleBasePath: String = ""
    ) {
        val hasRoot: Boolean get() = manager.isAvailable
    }

    /**
     * 对已连接的被控设备执行 Root 探测。
     * @param serial 设备序列号（兼容参数，内部使用当前连接）
     */
    suspend fun detect(serial: String): RootStatus {
        val adb = AdbManager.instance
        if (!adb.isConnected) {
            Logger.w(TAG, "ADB 未连接，无法探测 Root")
            return RootStatus()
        }

        // 1. 基础 su 检测
        val whichSu = adb.shell(serial, "command -v su 2>/dev/null || which su 2>/dev/null").orEmpty().trim()
        val hasSu = whichSu.isNotBlank() && whichSu != "su"

        // 2. uid 检测 —— 尝试 su 0 id
        val idOut = if (hasSu) {
            adb.shell(serial, "su 0 id 2>/dev/null").orEmpty()
        } else ""
        val uidZero = idOut.contains("uid=0")

        if (!hasSu && !uidZero) {
            Logger.i(TAG, "设备无 Root")
            return RootStatus()
        }

        // 3. 管理器识别 —— 依次探测 Magisk / KernelSU / APatch
        val magisk = detectMagisk(serial, adb)
        if (magisk != null) {
            Logger.i(TAG, "检测到 Magisk v${magisk.version.ifBlank { "?" }}")
            return magisk
        }

        val ksu = detectKernelSU(serial, adb)
        if (ksu != null) {
            Logger.i(TAG, "检测到 KernelSU v${ksu.version.ifBlank { "?" }}")
            return ksu
        }

        val apatch = detectAPatch(serial, adb)
        if (apatch != null) {
            Logger.i(TAG, "检测到 APatch v${apatch.version.ifBlank { "?" }}")
            return apatch
        }

        // 4. 通用 Root 回退
        Logger.i(TAG, "检测到通用 Root (su=$whichSu, uid0=$uidZero)")
        return RootStatus(
            manager = RootManager.GENERIC_ROOT,
            hasSu = hasSu,
            uidZero = uidZero,
            moduleBasePath = "/data/adb/modules"
        )
    }

    private fun detectMagisk(serial: String, adb: AdbManager): RootStatus? {
        // 特征路径: /sbin/.magisk (新版) 或 magisk 命令
        val path = adb.shell(serial, "ls -d /sbin/.magisk /data/adb/magisk 2>/dev/null").orEmpty().trim()
        val ver = adb.shell(serial, "su 0 magisk -V 2>/dev/null").orEmpty().trim().lineOrNull(0)
        val hasMagisk = path.isNotBlank() || ver.isNotBlank()
        if (!hasMagisk) return null
        return RootStatus(
            manager = RootManager.MAGISK,
            hasSu = true,
            uidZero = true,
            version = ver.orEmpty(),
            moduleBasePath = "/data/adb/modules"
        )
    }

    private fun detectKernelSU(serial: String, adb: AdbManager): RootStatus? {
        // 特征路径: /data/adb/ksu 或 ksu 命令
        val path = adb.shell(serial, "ls -d /data/adb/ksu 2>/dev/null").orEmpty().trim()
        val ver = adb.shell(serial, "su 0 ksud --version 2>/dev/null").orEmpty().trim().lineOrNull(0)
        val hasKsu = path.isNotBlank() || ver.isNotBlank()
        if (!hasKsu) return null
        return RootStatus(
            manager = RootManager.KERNELSU,
            hasSu = true,
            uidZero = true,
            version = ver.orEmpty(),
            moduleBasePath = "/data/adb/ksu/modules"
        )
    }

    private fun detectAPatch(serial: String, adb: AdbManager): RootStatus? {
        // 特征路径: /data/adb/ap 或 apd 命令
        val path = adb.shell(serial, "ls -d /data/adb/ap 2>/dev/null").orEmpty().trim()
        val ver = adb.shell(serial, "su 0 apd --version 2>/dev/null").orEmpty().trim().lineOrNull(0)
        val hasApatch = path.isNotBlank() || ver.isNotBlank()
        if (!hasApatch) return null
        return RootStatus(
            manager = RootManager.APATCH,
            hasSu = true,
            uidZero = true,
            version = ver.orEmpty(),
            moduleBasePath = "/data/adb/ap/modules"
        )
    }

    private fun String.lineOrNull(index: Int): String? =
        this.lineSequence().drop(index).firstOrNull()?.takeIf { it.isNotBlank() }
}
