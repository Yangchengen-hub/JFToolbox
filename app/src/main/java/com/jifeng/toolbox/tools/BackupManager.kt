package com.jifeng.toolbox.tools

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.DeviceInfo
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.core.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 分区备份管理器 —— Root 环境下通过 dd 提取分区镜像并打包为 ZIP。
 *
 * 备份策略:
 * - 读取 /dev/block/by-name 获取全部分区节点
 * - 对每个分区执行 `su -c dd if=/dev/block/by-name/X bs=8M | gzip > /sdcard/JFToolbox/backup/X.img.gz`
 * - 将 boot/dtbo/vbmeta 等关键分区打包为 backup_a.zip
 * - 将 system/vendor/product 等大分区打包为 backup_b.zip
 *
 * 前置: 被控设备需 Root (Magisk / KernelSU / APatch)。
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val REMOTE_DIR = "/sdcard/JFToolbox/backup"

    /** 关键分区 (小体积, 打包到 A 包)。 */
    private val CRITICAL_PARTITIONS = setOf(
        "boot", "init_boot", "vendor_boot", "dtbo", "vbmeta", "vbmeta_system",
        "vbmeta_vendor", "recovery", "super", "modem"
    )

    /** 大分区 (打包到 B 包, 可选)。 */
    private val LARGE_PARTITIONS = setOf(
        "system", "vendor", "product", "system_ext", "odm", "userdata", "cache"
    )

    sealed class BackupState {
        object Idle : BackupState()
        data class Running(val current: String, val index: Int, val total: Int, val phase: String) : BackupState()
        data class Done(val packA: String, val packB: String?, val partitionCount: Int) : BackupState()
        data class Failed(val message: String) : BackupState()
    }

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state

    /**
     * 执行全量分区备份。
     * @param serial 设备序列号
     * @param localDir 本地保存目录
     * @param includeLarge 是否备份大分区 (system/vendor 等, 耗时较长)
     */
    suspend fun backupAll(
        serial: String,
        localDir: String,
        includeLarge: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val adb = AdbManager
        if (!adb.isConnected) {
            _state.value = BackupState.Failed("ADB 未连接")
            return@withContext false
        }

        // Root 检测
        val root = RootDetector.detect(serial)
        if (!root.hasRoot) {
            _state.value = BackupState.Failed("被控设备无 Root, 无法 dd 分区")
            return@withContext false
        }
        val suPrefix = root.manager.suPrefix

        // 创建远程目录
        adb.shell(serial, "mkdir -p $REMOTE_DIR")

        // 读取分区列表
        val byName = adb.shell(serial, "ls /dev/block/by-name 2>/dev/null").orEmpty()
        val allPartitions = byName.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (allPartitions.isEmpty()) {
            _state.value = BackupState.Failed("无法读取分区表 /dev/block/by-name")
            return@withContext false
        }

        Logger.i(TAG, "发现 ${allPartitions.size} 个分区, Root=${root.manager.displayName}")

        // 筛选要备份的分区
        val critical = allPartitions.filter { it in CRITICAL_PARTITIONS }
        val large = if (includeLarge) allPartitions.filter { it in LARGE_PARTITIONS } else emptyList()
        val toBackup = (critical + large).distinct()

        if (toBackup.isEmpty()) {
            _state.value = BackupState.Failed("未找到可备份的关键分区")
            return@withContext false
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val total = toBackup.size
        var idx = 0
        var failCount = 0

        for (part in toBackup) {
            idx++
            _state.value = BackupState.Running(part, idx, total, "dd 提取中")

            val remoteGz = "$REMOTE_DIR/${part}.img.gz"
            // dd if=... | gzip > ...  (通过 su 执行)
            val ddCmd = "$suPrefix 'dd if=/dev/block/by-name/$part bs=8M 2>/dev/null | gzip > $remoteGz'"
            val result = adb.shell(serial, ddCmd)

            // 检查文件是否生成
            val sizeCheck = adb.shell(serial, "ls -la $remoteGz 2>/dev/null").orEmpty()
            if (sizeCheck.isBlank() || sizeCheck.contains("No such file")) {
                Logger.e(TAG, "分区 $part 备份失败 (文件未生成)")
                failCount++
                continue
            }
            Logger.i(TAG, "[$idx/$total] $part → $remoteGz ✓")
        }

        // 拉取到本地并打包
        _state.value = BackupState.Running("打包", total, total, "拉取并打包 ZIP")

        val localBackupDir = File(localDir, "backup_$timestamp").apply { mkdirs() }
        val packA = File(localBackupDir, "backup_a.zip")
        val packB = if (includeLarge) File(localBackupDir, "backup_b.zip") else null

        // A 包: 关键分区
        var aCount = 0
        ZipOutputStream(packA.outputStream()).use { zos ->
            for (part in critical) {
                val remoteGz = "$REMOTE_DIR/${part}.img.gz"
                if (pullAndZip(adb, serial, remoteGz, "${part}.img.gz", zos)) aCount++
            }
        }

        // B 包: 大分区
        var bCount = 0
        if (packB != null) {
            ZipOutputStream(packB.outputStream()).use { zos ->
                for (part in large) {
                    val remoteGz = "$REMOTE_DIR/${part}.img.gz"
                    if (pullAndZip(adb, serial, remoteGz, "${part}.img.gz", zos)) bCount++
                }
            }
        }

        // 清理远程临时文件
        adb.shell(serial, "rm -f $REMOTE_DIR/*.img.gz 2>/dev/null")

        val totalBacked = aCount + bCount
        Logger.i(TAG, "备份完成: A包=$aCount B包=$bCount 失败=$failCount")
        _state.value = BackupState.Done(packA.absolutePath, packB?.absolutePath, totalBacked)
        failCount == 0
    }

    /** 从设备拉取单个文件并写入 ZIP。 */
    private fun pullAndZip(
        adb: AdbManager,
        serial: String,
        remotePath: String,
        entryName: String,
        zos: ZipOutputStream
    ): Boolean {
        // 拉到临时文件
        val tmp = File.createTempFile("jfbk_", ".tmp")
        return try {
            if (!adb.pull(serial, remotePath, tmp.absolutePath)) {
                Logger.e(TAG, "拉取失败: $remotePath")
                return false
            }
            zos.putNextEntry(ZipEntry(entryName))
            tmp.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            true
        } catch (e: Exception) {
            Logger.e(TAG, "打包异常 $entryName: ${e.message}")
            false
        } finally {
            tmp.delete()
        }
    }
}
