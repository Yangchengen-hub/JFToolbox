package com.jifeng.toolbox.tools

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * APK 扫描器 + 批量安装器。
 *
 * 功能:
 * - 扫描被控设备 /sdcard 全目录下的 .apk 文件 (通过 ADB shell find)
 * - 对每个 APK 提取包名/版本/应用名 (通过 aapt 或 dump badging)
 * - 批量安装: 通过 ADB push + pm install
 *
 * 两种模式:
 * 1. 本机扫描: 扫描本机存储的 APK (用于本机安装)
 * 2. 远程扫描: 扫描被控设备的 APK (用于远程安装到被控设备)
 */
object ApkScanner {

    private const val TAG = "ApkScanner"

    /** 扫描到的 APK 信息。 */
    data class ApkEntry(
        val path: String,
        val fileName: String,
        val sizeBytes: Long,
        val packageName: String = "",
        val versionName: String = "",
        val appName: String = "",
        val isInstalled: Boolean = false
    ) {
        val sizeFormatted: String
            get() = when {
                sizeBytes >= 1_000_000_000 -> "%.2f GB".format(sizeBytes / 1_000_000_000.0)
                sizeBytes >= 1_000_000 -> "%.1f MB".format(sizeBytes / 1_000_000.0)
                sizeBytes >= 1_000 -> "%.0f KB".format(sizeBytes / 1_000.0)
                else -> "$sizeBytes B"
            }
    }

    sealed class ScanState {
        object Idle : ScanState()
        data class Scanning(val currentDir: String) : ScanState()
        data class Done(val apks: List<ApkEntry>) : ScanState()
        data class Failed(val message: String) : ScanState()
    }

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state

    // ---------- 本机扫描 ----------

    /**
     * 扫描本机指定目录下的所有 APK 文件。
     * @param rootDir 根目录 (如 /sdcard/Download)
     * @param maxDepth 递归最大深度
     */
    suspend fun scanLocal(rootDir: String, maxDepth: Int = 10): List<ApkEntry> = withContext(Dispatchers.IO) {
        val root = File(rootDir)
        if (!root.exists() || !root.isDirectory) {
            _state.value = ScanState.Failed("目录不存在: $rootDir")
            return@withContext emptyList()
        }

        _state.value = ScanState.Scanning(rootDir)
        val results = mutableListOf<ApkEntry>()

        fun scan(dir: File, depth: Int) {
            if (depth > maxDepth) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    _state.value = ScanState.Scanning(f.absolutePath)
                    scan(f, depth + 1)
                } else if (f.extension.equals("apk", ignoreCase = true)) {
                    results.add(ApkEntry(
                        path = f.absolutePath,
                        fileName = f.name,
                        sizeBytes = f.length()
                    ))
                }
            }
        }
        scan(root, 0)
        Logger.i(TAG, "本机扫描完成: ${results.size} 个 APK @ $rootDir")
        _state.value = ScanState.Done(results)
        results
    }

    // ---------- 远程扫描 (被控设备) ----------

    /**
     * 扫描被控设备 /sdcard 下所有 APK 文件。
     * 使用 ADB shell find 命令全量扫描。
     */
    suspend fun scanRemote(serial: String, rootPath: String = "/sdcard"): List<ApkEntry> = withContext(Dispatchers.IO) {
        val adb = AdbManager
        if (!adb.isConnected) {
            _state.value = ScanState.Failed("ADB 未连接")
            return@withContext emptyList()
        }

        _state.value = ScanState.Scanning(rootPath)
        // find /sdcard -name "*.apk" -type f 2>/dev/null
        val findCmd = "find $rootPath -name '*.apk' -type f 2>/dev/null"
        val output = adb.shell(serial, findCmd).orEmpty()

        if (output.isBlank()) {
            Logger.i(TAG, "远程扫描: 未找到 APK")
            _state.value = ScanState.Done(emptyList())
            return@withContext emptyList()
        }

        val paths = output.lines().map { it.trim() }.filter { it.isNotBlank() && it.endsWith(".apk", ignoreCase = true) }
        Logger.i(TAG, "远程扫描: 发现 ${paths.size} 个 APK")

        // 获取每个 APK 的文件大小
        val results = mutableListOf<ApkEntry>()
        for ((idx, path) in paths.withIndex()) {
            val sizeStr = adb.shell(serial, "ls -la '$path' 2>/dev/null").orEmpty()
            val size = parseLsSize(sizeStr)
            val fileName = path.substringAfterLast("/")
            results.add(ApkEntry(path = path, fileName = fileName, sizeBytes = size))

            // 每扫 20 个更新一次状态
            if (idx % 20 == 0) {
                _state.value = ScanState.Scanning("$path (${idx + 1}/${paths.size})")
            }
        }

        Logger.i(TAG, "远程扫描完成: ${results.size} 个 APK")
        _state.value = ScanState.Done(results)
        results
    }

    // ---------- 远程安装 ----------

    /**
     * 将 APK 安装到被控设备。
     * 流程: push APK → pm install → 清理临时文件
     */
    suspend fun installRemote(serial: String, apkPath: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val adb = AdbManager
        if (!adb.isConnected) return@withContext Pair(false, "ADB 未连接")

        val remoteTmp = "/data/local/tmp/install_${System.currentTimeMillis()}.apk"

        // 如果是本机路径, 先 push 到设备
        val localFile = File(apkPath)
        if (localFile.exists()) {
            Logger.i(TAG, "推送 APK: ${localFile.name} (${localFile.length()} bytes)")
            if (!adb.push(serial, apkPath, remoteTmp)) {
                return@withContext Pair(false, "推送 APK 失败")
            }
        } else {
            // 已经是远程路径, 直接用
            // (不需要 push)
        }

        val installPath = if (localFile.exists()) remoteTmp else apkPath

        // pm install
        Logger.i(TAG, "执行 pm install $installPath")
        val result = adb.shell(serial, "pm install -r $installPath").orEmpty()
        val success = result.contains("Success", ignoreCase = true)

        // 清理临时文件
        if (localFile.exists()) {
            adb.shell(serial, "rm -f $installPath 2>/dev/null")
        }

        val msg = if (success) "安装成功" else "安装失败: ${result.trim()}"
        Logger.i(TAG, msg)
        Pair(success, msg)
    }

    /**
     * 批量安装 APK 到被控设备。
     */
    suspend fun batchInstallRemote(serial: String, apkPaths: List<String>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        for (path in apkPaths) {
            val (ok, _) = installRemote(serial, path)
            if (ok) success++ else failed++
        }
        Logger.i(TAG, "批量安装完成: 成功=$success 失败=$failed")
        Pair(success, failed)
    }

    // ---------- 工具 ----------

    private fun parseLsSize(lsOutput: String): Long {
        // ls -la 格式: -rw-rw---- 1 root root 12345678 ... filename.apk
        val parts = lsOutput.trim().split(Regex("\\s+"))
        return parts.getOrNull(4)?.toLongOrNull() ?: 0L
    }
}
