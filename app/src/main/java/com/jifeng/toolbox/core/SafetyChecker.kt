package com.jifeng.toolbox.core

/**
 * 安全校验器 —— 所有危险操作执行前的数据完整性 / 合法性检查。
 *
 * 覆盖场景：
 * - 分区名白名单（防误删/误刷 modem、xbl 等致命分区）
 * - 镜像魔数校验（boot → ANDROID!, system → 跳过, vbmeta → AVB footer）
 * - 下载大小 vs max-download-size
 * - GPT 分区表黑砖检测（分区数 = 0 → 拒绝写入）
 * - 文件路径安全（防路径穿越）
 */
object SafetyChecker {

    private const val TAG = "SafetyChecker"

    /** 分区名白名单（仅允许字母/数字/下划线，防注入）。 */
    private val PARTITION_NAME_REGEX = Regex("^[a-zA-Z0-9_]+$")

    /**
     * 可被刷写的分区白名单。未在此列表中的分区将拒绝刷写（需用户手动确认 override）。
     * 不包含 modem/radio/xbl/abl/tz/persist 等高危分区。
     */
    val FLASHABLE_PARTITIONS = setOf(
        "boot", "init_boot", "vendor_boot", "dtbo",
        "system", "vendor", "product", "system_ext", "odm",
        "vbmeta", "vbmeta_system", "vbmeta_vendor",
        "userdata", "cache", "recovery",
        "super", "super_empty"
    )

    /** 绝对禁止擦除的分区（擦除即黑砖/基带丢失）。 */
    val NEVER_ERASE = setOf(
        "modem", "radio", "fsg", "fsc", "modemst1", "modemst2",
        "xbl", "xbl_config", "abl", "aboot", "sbl1", "tz", "rpm",
        "devinfo", "persist", "nvdata", "nvram", "frp", "ops",
        "bootloader", "hboot", "sbl", "sdi", "dxh"
    )

    sealed class CheckResult {
        object Ok : CheckResult()
        data class Warn(val message: String) : CheckResult()
        data class Deny(val message: String) : CheckResult()
    }

    // ---------- 分区名校验 ----------

    /** 校验分区名格式合法性。 */
    fun validatePartitionName(name: String): CheckResult {
        if (name.isBlank()) return CheckResult.Deny("分区名为空")
        if (!PARTITION_NAME_REGEX.matches(name)) {
            return CheckResult.Deny("分区名含非法字符: '$name' (仅允许字母/数字/下划线)")
        }
        return CheckResult.Ok
    }

    /**
     * 校验刷写分区的安全性。
     * @param partition 目标分区名
     * @param imageData 镜像数据（用于魔数校验），可为 null 跳过魔数
     */
    fun validateFlash(partition: String, imageData: ByteArray? = null): CheckResult {
        validatePartitionName(partition).let { if (it is CheckResult.Deny) return it }

        if (partition in NEVER_ERASE) {
            return CheckResult.Deny("分区 '$partition' 为致命分区，禁止刷写（防止黑砖/基带丢失）")
        }

        if (partition !in FLASHABLE_PARTITIONS) {
            return CheckResult.Warn("分区 '$partition' 不在常规白名单中，确认要刷写吗？")
        }

        // 魔数校验
        if (imageData != null && imageData.size >= 4) {
            val magicCheck = checkImageMagic(partition, imageData)
            if (magicCheck is CheckResult.Deny) return magicCheck
        }

        return CheckResult.Ok
    }

    /**
     * 校验擦除分区的安全性。
     * 绝对禁止擦除 NEVER_ERASE 中的分区。
     */
    fun validateErase(partition: String): CheckResult {
        validatePartitionName(partition).let { if (it is CheckResult.Deny) return it }

        if (partition in NEVER_ERASE) {
            return CheckResult.Deny("分区 '$partition' 为致命分区，禁止擦除（擦除即黑砖/基带丢失）")
        }

        if (partition in setOf("boot", "init_boot", "vbmeta", "system", "vendor")) {
            return CheckResult.Warn("擦除 '$partition' 可能导致无法开机，确认吗？")
        }

        return CheckResult.Ok
    }

    // ---------- 镜像魔数校验 ----------

    private fun checkImageMagic(partition: String, data: ByteArray): CheckResult {
        val magic = String(data, 0, minOf(4, data.size), Charsets.US_ASCII)
        val hex = data.take(8).joinToString("") { "%02x".format(it) }

        // boot / init_boot: Android boot image → "ANDROID!"
        if (partition in setOf("boot", "init_boot", "vendor_boot", "recovery")) {
            if (!magic.startsWith("ANDROID!")) {
                return CheckResult.Deny("boot 镜像魔数错误: 期望 'ANDROID!' 实际 '$magic' (hex: $hex)")
            }
        }

        // vbmeta: AVB magic → "AVB0"
        if (partition.startsWith("vbmeta")) {
            val avbMagic = String(data, 0, 4, Charsets.US_ASCII)
            if (avbMagic != "AVB0") {
                return CheckResult.Deny("vbmeta 魔数错误: 期望 'AVB0' 实际 '$avbMagic' (hex: $hex)")
            }
        }

        // super: CRC header → 检查是否为 sparse 或 raw
        if (partition == "super" || partition == "super_empty") {
            // sparse image magic = 0x3aff26ed
            if (data.size >= 4) {
                val be = ((data[0].toInt() and 0xff) shl 24) or
                         ((data[1].toInt() and 0xff) shl 16) or
                         ((data[2].toInt() and 0xff) shl 8) or
                         (data[3].toInt() and 0xff)
                if (be == 0x3aff26ed) return CheckResult.Ok // sparse
            }
            // raw super image 通常很大，不强制魔数
        }

        return CheckResult.Ok
    }

    // ---------- 下载大小校验 ----------

    /**
     * 校验下载镜像大小是否超出 fastboot max-download-size 限制。
     * @param dataSize 镜像字节数
     * @param maxDownloadSize 设备 max-download-size（字节），0 表示未知
     */
    fun validateDownloadSize(dataSize: Long, maxDownloadSize: Long): CheckResult {
        if (maxDownloadSize <= 0) return CheckResult.Ok // 未知限制，放行
        if (dataSize > maxDownloadSize) {
            return CheckResult.Deny(
                "镜像大小 ${formatBytes(dataSize)} 超出设备 max-download-size ${formatBytes(maxDownloadSize)}"
            )
        }
        return CheckResult.Ok
    }

    // ---------- GPT 黑砖检测 ----------

    /**
     * 校验 GPT 分区表完整性（9008 救砖用）。
     * 分区数 = 0 → 黑砖，拒绝继续写入。
     */
    fun validateGpt(partitionCount: Int): CheckResult {
        if (partitionCount <= 0) {
            return CheckResult.Deny("GPT 分区表为空 (count=0) → 疑似黑砖，已拦截写入操作防止进一步损坏")
        }
        if (partitionCount < 3) {
            return CheckResult.Warn("GPT 分区数异常少 ($partitionCount)，请确认 rawprogram 配置正确")
        }
        return CheckResult.Ok
    }

    // ---------- 路径安全 ----------

    /**
     * 校验远程路径不含目录穿越攻击（../）。
     */
    fun validateRemotePath(path: String): CheckResult {
        if (path.isBlank()) return CheckResult.Deny("路径为空")
        if (path.contains("..")) return CheckResult.Deny("路径含非法 '../' 穿越: $path")
        if (!path.startsWith("/")) return CheckResult.Deny("远程路径必须为绝对路径: $path")
        return CheckResult.Ok
    }

    // ---------- 工具 ----------

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    /** 快捷判断：是否应阻断操作。 */
    fun shouldBlock(result: CheckResult): Boolean = result is CheckResult.Deny
}
