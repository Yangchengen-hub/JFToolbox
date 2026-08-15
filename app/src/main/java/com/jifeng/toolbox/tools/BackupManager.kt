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
 * 分区备份管理器 v2 —— 支持用户选择备份分区。
 *
 * v2 改进:
 * - 默认备份重要分区 (boot, system, data, recovery 等)
 * - 用户可勾选其他分区
 * - 提供全选/取消全选功能
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val REMOTE_DIR = "/sdcard/JFToolbox/backup"

    /** 默认备份的重要分区 */
    val DEFAULT_PARTITIONS = setOf(
        "boot", "init_boot", "vendor_boot", "dtbo", "vbmeta",
        "vbmeta_system", "vbmeta_vendor", "recovery"
    )

    /** 可选的大分区 */
    val OPTIONAL_PARTITIONS = setOf(
        "system", "vendor", "product", "system_ext", "odm",
        "userdata", "cache", "modem", "super", "persist",
        "dsp", "bluetooth", "wifi", "tz", "hyp", "keymaster",
        "sec", "frp", "misc", "logo", "spmfw", "scp1", "scp2",
        "lk", "preloader", "tee1", "tee2", "sspm1", "sspm2"
    )

    sealed class BackupState {
        object Idle : BackupState()
        data class Running(val current: String, val index: Int, val total: Int, val phase: String) : BackupState()
        data class Done(val packA: String, val packB: String?, val partitionCount: Int) : BackupState()
        data class Failed(val message: String) : BackupState()
    }

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state

    /** 远程可用分区列表 */
    private val _availablePartitions = MutableStateFlow<List<String>>(emptyList())
    val availablePartitions: StateFlow<List<String>> = _availablePartitions

    /** 扫描远程可用分区 */
    suspend fun scanPartitions(serial: String): List<String> = withContext(Dispatchers.IO) {
        val adb = AdbManager
        if (!adb.isConnected) return@withContext emptyList()
        val byName = adb.shell(serial, "ls /dev/block/by-name 2>/dev/null").orEmpty()
        val partitions = byName.lines().map { it.trim() }.filter { it.isNotBlank() }
        _availablePartitions.value = partitions
        partitions
    }

    /**
     * 执行分区备份。
     * @param serial 设备序列号
     * @param localDir 本地保存目录
     * @param selectedPartitions 用户选择的分区列表 (为空则使用默认)
     */
    suspend fun backup(
        serial: String,
        localDir: String,
        selectedPartitions: Set<String> = emptySet()
    ): Boolean = withContext(Dispatchers.IO) {
        val adb = AdbManager
        if (!adb.isConnected) {
            _state.value = BackupState.Failed("ADB 未连接")
            return@withContext false
        }

        val root = RootDetector.detect(serial)
        if (!root.hasRoot) {
            _state.value = BackupState.Failed("被控设备无 Root, 无法 dd 分区")
            return@withContext false
        }
        val suPrefix = root.manager.suPrefix

        adb.shell(serial, "mkdir -p $REMOTE_DIR")

        // 读取分区列表
        val byName = adb.shell(serial, "ls /dev/block/by-name 2>/dev/null").orEmpty()
        val allPartitions = byName.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (allPartitions.isEmpty()) {
            _state.value = BackupState.Failed("无法读取分区表 /dev/block/by-name")
            return@withContext false
        }

        // 确定要备份的分区
        val toBackup = if (selectedPartitions.isNotEmpty()) {
            allPartitions.filter { it in selectedPartitions }
        } else {
            allPartitions.filter { it in DEFAULT_PARTITIONS }
        }

        if (toBackup.isEmpty()) {
            _state.value = BackupState.Failed("未找到可备份的分区")
            return@withContext false
        }

        Logger.i(TAG, "发现 ${allPartitions.size} 个分区, 将备份 ${toBackup.size} 个")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val total = toBackup.size
        var failCount = 0

        for ((idx, part) in toBackup.withIndex()) {
            _state.value = BackupState.Running(part, idx + 1, total, "dd 提取中")

            val remoteGz = "$REMOTE_DIR/${part}.img.gz"
            val ddCmd = "$suPrefix 'dd if=/dev/block/by-name/$part bs=8M 2>/dev/null | gzip > $remoteGz'"
            adb.shell(serial, ddCmd)

            val sizeCheck = adb.shell(serial, "ls -la $remoteGz 2>/dev/null").orEmpty()
            if (sizeCheck.isBlank() || sizeCheck.contains("No such file")) {
                Logger.e(TAG, "分区 $part 备份失败")
                failCount++
                continue
            }
            Logger.i(TAG, "[${idx + 1}/$total] $part → $remoteGz ✓")
        }

        _state.value = BackupState.Running("打包", total, total, "拉取并打包 ZIP")

        val localBackupDir = File(localDir, "backup_$timestamp").apply { mkdirs() }
        val packA = File(localBackupDir, "backup.zip")

        var count = 0
        ZipOutputStream(packA.outputStream()).use { zos ->
            for (part in toBackup) {
                val remoteGz = "$REMOTE_DIR/${part}.img.gz"
                if (pullAndZip(adb, serial, remoteGz, "${part}.img.gz", zos)) count++
            }
        }

        adb.shell(serial, "rm -f $REMOTE_DIR/*.img.gz 2>/dev/null")

        Logger.i(TAG, "备份完成: $count 个分区, 失败 $failCount")
        _state.value = BackupState.Done(packA.absolutePath, null, count)
        failCount == 0
    }

    private fun pullAndZip(
        adb: AdbManager,
        serial: String,
        remotePath: String,
        entryName: String,
        zos: ZipOutputStream
    ): Boolean {
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
