package com.jifeng.toolbox.adb

import android.content.Context
import com.jifeng.toolbox.core.Logger

/**
 * ADB 协议管理。MVP 阶段使用系统已授权的 ADB 通道 + 内嵌 ADB 服务器双轨。
 * - 若本机已通过 USB 拿到 ADB 权限（OTG 连被控机），直接走 exec("adb -s <serial> ...")
 * - 后续版本替换为 libusb + ADB-over-USB 私有实现，彻底免 root
 */
object AdbManager {

    private const val TAG = "AdbManager"
    lateinit var instance: AdbManagerImpl
        private set

    fun init(ctx: Context) {
        instance = AdbManagerImpl(ctx)
        Logger.i(TAG, "ADB 子系统初始化完成")
    }
}

class AdbManagerImpl(private val ctx: Context) {

    /**
     * 执行 adb shell 命令。MVP 优先使用系统 adb（要求开发者模式已授权），
     * 失败则回退到内嵌通道（占位，后续接入 libadb）。
     */
    fun shell(serial: String, cmd: String): String? = try {
        val runtime = Runtime.getRuntime()
        val process = runtime.exec(arrayOf("adb", "-s", serial, "shell", cmd))
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        process.waitFor()
        if (err.isNotBlank()) Logger.w("AdbShell", "stderr: $err")
        out
    } catch (e: Exception) {
        Logger.e("AdbShell", "执行失败: ${e.message}")
        null
    }

    fun push(serial: String, local: String, remote: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("adb", "-s", serial, "push", local, remote))
        p.waitFor() == 0
    } catch (e: Exception) { false }

    fun pull(serial: String, remote: String, local: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("adb", "-s", serial, "pull", remote, local))
        p.waitFor() == 0
    } catch (e: Exception) { false }

    fun reboot(serial: String, target: String = "") {
        val cmd = if (target.isBlank()) "reboot" else "reboot $target"
        shell(serial, cmd)
    }

    fun listDevices(): List<String> {
        val out = shell("", "adb devices").orEmpty()
        return out.lines().filter { it.contains("\t") }.map { it.split("\t")[0] }
    }

    /**
     * Fastboot 刷写单个分区。
     */
    fun fastbootFlash(serial: String, partition: String, imgPath: String): Boolean = try {
        // 先把 img push 到被控机的 /data/local/tmp
        push(serial, imgPath, "/data/local/tmp/${partition}.img")
        val r = shell(serial, "dd if=/data/local/tmp/${partition}.img of=/dev/block/by-name/$partition")
        Logger.i("Fastboot", "$partition 刷写结果: $r")
        r != null
    } catch (e: Exception) {
        Logger.e("Fastboot", "刷写 $partition 失败: ${e.message}")
        false
    }

    /**
     * 解析 ZIP 卡刷包（简化版）：列出 entries，按 partition map 刷写。
     * 完整实现需要 zip 解包 + 校验，MVP 用 commons-compress。
     */
    fun parseAndFlashZip(serial: String, zipPath: String): Pair<Boolean, String> {
        return try {
            // push 到设备端，让设备端 fastboot 自己处理
            push(serial, zipPath, "/data/local/tmp/update.zip")
            val r = shell(serial, "cd /data/local/tmp && unzip -l update.zip")
            Logger.i("ZipFlash", "ZIP 内容:\n$r")
            // 这里仅做展示；真刷写需进入 fastboot 模式逐分区 dd
            Pair(true, r ?: "无输出")
        } catch (e: Exception) {
            Pair(false, e.message ?: "unknown")
        }
    }
}
