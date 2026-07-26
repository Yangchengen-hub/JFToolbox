package com.jifeng.toolbox.tools

import android.content.Context
import android.hardware.usb.UsbDevice
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.core.RootDetector
import com.jifeng.toolbox.fastboot.FastbootClient
import com.jifeng.toolbox.usb.UsbDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内核分区刷写器 —— 单分区 .img 镜像刷入, 区别于 [com.jifeng.toolbox.fastboot.FastbootFlasher] 的 ZIP 卡刷包。
 *
 * 支持两种通道:
 * - fastboot: 设备已进 bootloader, 通过 USB bulk 直连 erase/download/flash
 * - root + dd: 设备在线 (ADB), Root 环境下用 dd 直接写 /dev/block/by-name/<part>
 *
 * 风险分级: LOW / MEDIUM / HIGH / FATAL, 调用方据等级决定 reboot 策略与确认流程。
 * 安全: fastboot 通道经 SafetyChecker 校验魔数与白名单; root 通道 dd 写入前必须备份。
 */
object KernelFlasher {

    private const val TAG = "KernelFlasher"
    private const val REMOTE_TMP = "/data/local/tmp"

    /** 风险等级。color 为 ARGB Long, 供 UI 高亮卡片。 */
    enum class RiskLevel(val displayName: String, val color: Long) {
        LOW("低风险", 0xFF4CAF50),
        MEDIUM("中风险", 0xFFFFC107),
        HIGH("高风险", 0xFFFF9800),
        FATAL("致命风险", 0xFFF44336)
    }

    /** 单分区刷写目标。 */
    data class KernelTarget(
        val partition: String,        // boot / init_boot / vendor_boot / dtbo / vbmeta
        val imagePath: String,
        val riskLevel: RiskLevel,
        val reason: String            // 为什么是这个风险等级
    )

    /** 刷写进度回调。current/total 为步骤进度, partition 为分区名, ok 标识本步是否成功。 */
    data class FlashProgress(
        val current: Int,
        val total: Int,
        val partition: String,
        val message: String,
        val ok: Boolean
    )

    /** 致命分区: 错刷永久变砖, 需 9008 救砖。SafetyChecker.NEVER_ERASE 会硬拦截。 */
    private val FATAL_PARTITIONS = setOf("xbl", "abl", "modem", "tz", "aop", "devcfg", "keymaster")

    /**
     * 评估刷写目标的风险等级。
     * 规则:
     *   - boot / init_boot / vendor_boot: MEDIUM (同版本安全, 跨版本可能不启动)
     *   - dtbo: HIGH (错配可能导致显示异常)
     *   - vbmeta 系列: HIGH (影响 AVB 验证链)
     *   - xbl / abl / modem / tz / aop / devcfg / keymaster: FATAL (底层固件, 错刷永久变砖)
     *   - 其它: LOW
     */
    fun assessRisk(partition: String, imageFile: File): RiskLevel {
        val p = partition.trim().lowercase()
        // 文件存在性记录 (不影响风险判定, 仅供日志)
        if (!imageFile.exists()) Logger.w(TAG, "assessRisk: 镜像不存在 ${imageFile.absolutePath}")
        return when (p) {
            "boot", "init_boot", "vendor_boot" -> RiskLevel.MEDIUM
            "dtbo" -> RiskLevel.HIGH
            "vbmeta", "vbmeta_system", "vbmeta_vendor" -> RiskLevel.HIGH
            in FATAL_PARTITIONS -> RiskLevel.FATAL
            else -> RiskLevel.LOW
        }
    }

    /** 构造刷写目标的简短风险说明。 */
    fun riskReason(partition: String, level: RiskLevel): String = when (level) {
        RiskLevel.LOW -> "$partition 非关键分区, 风险较低"
        RiskLevel.MEDIUM -> "$partition 为内核引导分区, 跨版本刷写可能无法启动"
        RiskLevel.HIGH -> "$partition 错配可能影响显示或 AVB 验证链"
        RiskLevel.FATAL -> "$partition 为底层固件, 错刷将永久变砖 (需 9008 救砖)"
    }

    /**
     * 通过 fastboot 刷内核分区 (设备需进 bootloader)。
     *
     * 内部: USB 权限 → 打开 FastbootClient → erase → download → flash → 按 riskLevel 决定 reboot。
     * 参考 FastbootFlasher.flash 的设备打开逻辑; 因 imagePath 是单 .img 而非 ZIP, 不走 flashZip,
     * 直接用 FastbootClient.erase + flashImage (后者内部 download+flash, 并经 SafetyChecker 校验)。
     *
     * reboot 策略:
     *   - LOW/MEDIUM: 自动 reboot
     *   - HIGH: 不自动 reboot, 通过 onProgress 发提示, 由 UI 接管确认
     *   - FATAL: 不自动 reboot, 让用户手动检查设备状态
     *
     * @return 刷写本身是否成功 (不含 reboot 结果)
     */
    suspend fun flashKernel(
        ctx: Context,
        device: UsbDevice,
        target: KernelTarget,
        onProgress: (FlashProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val total = 3
        val usbMgr = UsbDeviceManager.get(ctx)

        // 1. USB 权限
        if (!usbMgr.hasPermission(device)) {
            onProgress(FlashProgress(0, total, target.partition, "请求 fastboot USB 权限...", false))
            usbMgr.requestFastbootPermission(device)
            onProgress(FlashProgress(0, total, target.partition, "请在系统弹窗授权后重试", false))
            return@withContext false
        }

        // 2. 打开 USB 设备 + FastbootClient (参考 FastbootFlasher.flash L144-L157)
        val rawConn = usbMgr.openDevice(device)
        if (rawConn == null) {
            onProgress(FlashProgress(0, total, target.partition, "❌ 打开 USB 设备失败 (权限或拔出)", false))
            return@withContext false
        }
        val client = FastbootClient()
        if (!client.open(device, rawConn)) {
            client.close()
            onProgress(FlashProgress(0, total, target.partition, "❌ Fastboot 接口打开失败 (设备未真进 bootloader?)", false))
            return@withContext false
        }
        onProgress(FlashProgress(0, total, target.partition, "✅ Fastboot 设备已连接: ${device.deviceName}", true))

        var ok = false
        try {
            // 3. erase (失败不阻断, 某些分区 erase 会失败但 flash 仍可成功)
            onProgress(FlashProgress(1, total, target.partition, "erase ${target.partition} ...", true))
            try {
                client.erase(target.partition) { msg ->
                    onProgress(FlashProgress(1, total, target.partition, msg, true))
                }
            } catch (e: Exception) {
                onProgress(FlashProgress(1, total, target.partition, "erase 警告: ${e.message} (继续)", true))
            }

            // 4. download + flash (FastbootClient 内部经 SafetyChecker 校验魔数与白名单)
            onProgress(FlashProgress(2, total, target.partition, "download + flash ${target.partition} ...", true))
            ok = try {
                client.flashImage(
                    target.partition, target.imagePath,
                    onInfo = { msg -> onProgress(FlashProgress(2, total, target.partition, msg, true)) },
                    onProgress = { pct -> onProgress(FlashProgress(2, total, target.partition, "传输 $pct%", true)) }
                )
            } catch (e: Exception) {
                onProgress(FlashProgress(2, total, target.partition, "❌ flash 异常: ${e.message}", false))
                false
            }
            onProgress(FlashProgress(3, total, target.partition, if (ok) "✓ 刷写完成" else "✗ 刷写失败", ok))

            // 5. 按 riskLevel 决定 reboot
            if (ok) {
                when (target.riskLevel) {
                    RiskLevel.LOW, RiskLevel.MEDIUM -> {
                        try {
                            onProgress(FlashProgress(3, total, target.partition, "自动重启设备...", true))
                            client.reboot()
                        } catch (e: Exception) {
                            Logger.w(TAG, "reboot 失败 (可手动重启): ${e.message}")
                            onProgress(FlashProgress(3, total, target.partition, "⚠ reboot 指令失败, 请手动重启", true))
                        }
                    }
                    RiskLevel.HIGH -> {
                        onProgress(
                            FlashProgress(
                                3, total, target.partition,
                                "⚠ HIGH 风险: 等待用户确认 reboot, 请检查设备状态后手动重启或确认", true
                            )
                        )
                    }
                    RiskLevel.FATAL -> {
                        onProgress(
                            FlashProgress(
                                3, total, target.partition,
                                "⚠ FATAL 风险: 不自动 reboot, 请手动检查设备状态", true
                            )
                        )
                    }
                }
            }
        } finally {
            client.close()
        }
        ok
    }

    /**
     * 通过 Root + dd 直接写分区 (设备在线, Root 环境)。
     *
     * 内部: RootDetector 检测 → push 镜像到 /data/local/tmp → su -c dd 写分区 → sync。
     * reboot 策略同 [flashKernel]。
     *
     * @param serial 设备序列号 (AdbManager 兼容参数)
     */
    suspend fun flashKernelRoot(
        serial: String,
        target: KernelTarget,
        onProgress: (FlashProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val total = 4

        // 1. Root 检测
        onProgress(FlashProgress(1, total, target.partition, "检测 Root 环境...", true))
        val root = RootDetector.detect(serial)
        if (!root.hasRoot) {
            onProgress(FlashProgress(1, total, target.partition, "❌ 设备无 Root, 无法走 dd 通道", false))
            return@withContext false
        }
        onProgress(
            FlashProgress(
                1, total, target.partition,
                "✅ Root: ${root.manager.displayName} v${root.version.ifBlank { "?" }}", true
            )
        )

        // 2. push 镜像到 /data/local/tmp/
        val localFile = File(target.imagePath)
        if (!localFile.exists()) {
            onProgress(FlashProgress(0, total, target.partition, "❌ 本地镜像不存在: ${target.imagePath}", false))
            return@withContext false
        }
        val remoteImg = "$REMOTE_TMP/jf_kernel_${target.partition}.img"
        onProgress(
            FlashProgress(
                2, total, target.partition,
                "push 镜像 ${localFile.name} (${localFile.length() / 1024} KB) ...", true
            )
        )
        if (!AdbManager.push(serial, target.imagePath, remoteImg)) {
            onProgress(FlashProgress(2, total, target.partition, "❌ push 镜像失败", false))
            return@withContext false
        }

        // 3. dd 写分区 (走 su -c 提权; && echo 标记用于判定成功, 因 AdbManager.shell 不返回退出码)
        val ddCmd = "su -c 'dd if=$remoteImg of=/dev/block/by-name/${target.partition} bs=8M && echo JF_DD_OK'"
        onProgress(FlashProgress(3, total, target.partition, "dd 写入 ${target.partition} ...", true))
        val out = AdbManager.shell(serial, ddCmd) ?: ""
        val ok = out.contains("JF_DD_OK")
        onProgress(
            FlashProgress(
                3, total, target.partition,
                if (ok) "✓ dd 完成" else "✗ dd 失败 (out=${out.take(120)})",
                ok
            )
        )

        // 4. sync (无论 dd 成否都同步, 避免脏数据)
        AdbManager.shell(serial, "su -c sync")
        if (ok) onProgress(FlashProgress(4, total, target.partition, "sync 完成", true))

        // 5. 按 riskLevel 决定 reboot
        if (ok) {
            when (target.riskLevel) {
                RiskLevel.LOW, RiskLevel.MEDIUM -> {
                    onProgress(FlashProgress(4, total, target.partition, "自动重启设备...", true))
                    AdbManager.reboot(serial)
                }
                RiskLevel.HIGH -> {
                    onProgress(
                        FlashProgress(
                            4, total, target.partition,
                            "⚠ HIGH 风险: 等待用户确认 reboot, 请检查设备状态后手动重启或确认", true
                        )
                    )
                }
                RiskLevel.FATAL -> {
                    onProgress(
                        FlashProgress(
                            4, total, target.partition,
                            "⚠ FATAL 风险: 不自动 reboot, 请手动检查设备状态", true
                        )
                    )
                }
            }
        }
        ok
    }

    /**
     * 内核备份 (刷写前必备份)。
     *
     * 走 Root + dd 提取 boot 分区到 /data/local/tmp, 再 pull 到本地目录。
     *
     * @param serial 设备序列号
     * @param outputDir 本地保存目录
     * @return 备份文件, 失败返回 null (无 ADB / 无 Root / dd 失败 / pull 失败)
     */
    suspend fun backupCurrentKernel(serial: String, outputDir: File): File? = withContext(Dispatchers.IO) {
        // 1. Root 检测
        val root = RootDetector.detect(serial)
        if (!root.hasRoot) {
            Logger.w(TAG, "备份失败: 设备无 Root")
            return@withContext null
        }

        // 2. dd 提取 boot 分区
        val remoteBackup = "$REMOTE_TMP/boot_backup.img"
        val out = AdbManager.shell(
            serial,
            "su -c 'dd if=/dev/block/by-name/boot of=$remoteBackup bs=8M && echo JF_DD_OK'"
        ) ?: ""
        if (!out.contains("JF_DD_OK")) {
            Logger.e(TAG, "备份失败: dd 未成功 (out=${out.take(120)})")
            return@withContext null
        }
        AdbManager.shell(serial, "su -c sync")

        // 3. pull 到本地
        if (!outputDir.exists()) outputDir.mkdirs()
        val localFile = File(outputDir, "boot_backup_${System.currentTimeMillis()}.img")
        val ok = AdbManager.pull(serial, remoteBackup, localFile.absolutePath)
        // 清理远程临时文件
        AdbManager.shell(serial, "rm -f $remoteBackup")
        if (ok && localFile.exists() && localFile.length() > 0) {
            Logger.i(TAG, "boot 备份完成: ${localFile.absolutePath} (${localFile.length() / 1024} KB)")
            localFile
        } else {
            Logger.e(TAG, "备份失败: pull 失败或文件为空")
            null
        }
    }
}
