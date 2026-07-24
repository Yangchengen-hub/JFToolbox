package com.jifeng.toolbox.tools

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 应用冻结/解冻管理器 —— 通过 ADB pm 命令冻结/解冻第三方应用。
 *
 * 冻结策略 (免 Root, ADB 权限即可):
 * - 冻结: pm disable-user --user 0 <pkg>  (用户级禁用, 可恢复)
 * - 解冻: pm enable <pkg>
 * - 卸载: pm uninstall --user 0 <pkg>  (用户级卸载, 保留 APK)
 *
 * 支持:
 * - 列出第三方应用及其启用/禁用状态
 * - 单个/批量冻结
 * - 单个/批量解冻
 */
object AppFreezer {

    private const val TAG = "AppFreezer"

    data class AppEntry(
        val packageName: String,
        val isFrozen: Boolean,
        val isSystem: Boolean = false
    )

    sealed class FreezeState {
        object Idle : FreezeState()
        data class Loading(val message: String) : FreezeState()
        data class AppsLoaded(val apps: List<AppEntry>) : FreezeState()
        data class ActionDone(val success: Int, val failed: Int, val action: String) : FreezeState()
        data class Failed(val message: String) : FreezeState()
    }

    private val _state = MutableStateFlow<FreezeState>(FreezeState.Idle)
    val state: StateFlow<FreezeState> = _state

    /**
     * 列出设备上所有第三方应用及其冻结状态。
     */
    suspend fun listThirdPartyApps(serial: String): List<AppEntry> = withContext(Dispatchers.IO) {
        val adb = AdbManager.instance
        if (!adb.isConnected) {
            _state.value = FreezeState.Failed("ADB 未连接")
            return@withContext emptyList()
        }

        _state.value = FreezeState.Loading("正在获取应用列表...")

        // pm list packages -3  → 第三方应用
        val out = adb.shell(serial, "pm list packages -3").orEmpty()
        val packages = out.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }

        Logger.i(TAG, "发现 ${packages.size} 个第三方应用")

        val apps = mutableListOf<AppEntry>()
        for (pkg in packages) {
            // pm list packages -d → 已禁用的包
            val disabledCheck = adb.shell(serial, "pm list packages -d | grep '$pkg'").orEmpty()
            val isFrozen = disabledCheck.isNotBlank()
            apps.add(AppEntry(packageName = pkg, isFrozen = isFrozen, isSystem = false))
        }

        _state.value = FreezeState.AppsLoaded(apps)
        apps
    }

    /**
     * 冻结单个应用。
     */
    suspend fun freeze(serial: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val adb = AdbManager.instance
        val result = adb.shell(serial, "pm disable-user --user 0 $packageName").orEmpty()
        val ok = result.contains("disabled", ignoreCase = true) || result.contains("true", ignoreCase = true)
        Logger.i(TAG, "冻结 $packageName: ${if (ok) "成功" else "失败"}")
        ok
    }

    /**
     * 解冻单个应用。
     */
    suspend fun unfreeze(serial: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val adb = AdbManager.instance
        val result = adb.shell(serial, "pm enable $packageName").orEmpty()
        val ok = result.contains("enabled", ignoreCase = true) || result.contains("true", ignoreCase = true) || result.contains("1")
        Logger.i(TAG, "解冻 $packageName: ${if (ok) "成功" else "失败"}")
        ok
    }

    /**
     * 批量冻结。
     */
    suspend fun batchFreeze(serial: String, packages: List<String>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        _state.value = FreezeState.Loading("批量冻结中 (0/${packages.size})...")
        for ((idx, pkg) in packages.withIndex()) {
            if (freeze(serial, pkg)) success++ else failed++
            _state.value = FreezeState.Loading("批量冻结中 (${idx + 1}/${packages.size})...")
        }
        _state.value = FreezeState.ActionDone(success, failed, "冻结")
        Pair(success, failed)
    }

    /**
     * 批量解冻。
     */
    suspend fun batchUnfreeze(serial: String, packages: List<String>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        _state.value = FreezeState.Loading("批量解冻中 (0/${packages.size})...")
        for ((idx, pkg) in packages.withIndex()) {
            if (unfreeze(serial, pkg)) success++ else failed++
            _state.value = FreezeState.Loading("批量解冻中 (${idx + 1}/${packages.size})...")
        }
        _state.value = FreezeState.ActionDone(success, failed, "解冻")
        Pair(success, failed)
    }

    /**
     * 用户级卸载 (保留 APK, 仅移除当前用户)。
     */
    suspend fun uninstallUser(serial: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val adb = AdbManager.instance
        val result = adb.shell(serial, "pm uninstall --user 0 $packageName").orEmpty()
        val ok = result.contains("Success", ignoreCase = true)
        Logger.i(TAG, "卸载 $packageName: ${if (ok) "成功" else "失败"}")
        ok
    }
}
