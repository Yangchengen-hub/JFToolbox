package com.jifeng.toolbox.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.jifeng.toolbox.adb.protocol.AdbConnection
import com.jifeng.toolbox.adb.protocol.AdbKeyManager
import com.jifeng.toolbox.adb.protocol.AdbSync
import com.jifeng.toolbox.adb.protocol.AdbTransport
import com.jifeng.toolbox.core.Logger

/**
 * ADB 管理器 (单例)。USB 点对点连接, serial 参数仅用于 API 兼容, 实际忽略。
 * 真正的传输由 AdbConnection over UsbDeviceConnection.bulkTransfer 完成。
 */
object AdbManager {

    private const val TAG = "AdbManager"
    lateinit var keys: AdbKeyManager
        private set

    @Volatile private var connection: AdbConnection? = null
    @Volatile private var sync: AdbSync? = null
    @Volatile var currentSerial: String? = null
        private set

    val isConnected: Boolean get() = connection?.connected == true

    fun init(ctx: Context) {
        keys = AdbKeyManager(ctx)
        Logger.i(TAG, "ADB 子系统初始化完成 (真 USB 传输)")
    }

    /**
     * 在已授权的 UsbDevice 上建立 ADB 连接。
     * 由 UsbDeviceManager 在权限授予后调用。
     */
    fun connect(device: UsbDevice, rawConn: android.hardware.usb.UsbDeviceConnection): Boolean {
        disconnect()
        val transport = AdbTransport.open(device, rawConn, AdbTransport.IFACE_PROTOCOL_ADB)
            ?: run { Logger.e(TAG, "未找到 ADB 接口"); return false }
        val adb = AdbConnection(transport, keys)
        if (!adb.connect()) { transport.release(); return false }
        connection = adb
        sync = AdbSync(adb)
        currentSerial = readSerial()
        Logger.i(TAG, "已连接设备 serial=$currentSerial")
        return true
    }

    fun disconnect() {
        connection?.close()
        connection = null
        sync = null
        currentSerial = null
    }

    private fun readSerial(): String? =
        connection?.shell("getprop ro.serialno", 3_000)?.takeIf { it.isNotBlank() }

    // ---------- 兼容旧 API (serial 忽略) ----------

    fun shell(serial: String, cmd: String): String? {
        val c = connection ?: return null
        return try { c.shell(cmd) } catch (e: Exception) { Logger.w(TAG, "shell 失败: ${e.message}"); null }
    }

    fun push(serial: String, local: String, remote: String): Boolean =
        sync?.push(local, remote) ?: false

    fun pull(serial: String, remote: String, local: String): Boolean =
        sync?.pull(remote, local) ?: false

    fun reboot(serial: String, target: String = "") {
        val cmd = if (target.isBlank()) "reboot" else "reboot $target"
        shell(serial, cmd)
    }

    /** 兼容旧 listDevices(): 连接存活时返回 [serial], 否则空。 */
    fun listDevices(): List<String> =
        if (isConnected && currentSerial != null) listOf(currentSerial!!) else emptyList()

    /**
     * 真正的 fastboot 刷写在 FastbootClient (Phase 3) 中实现, 需设备进 bootloader。
     * 此处保留兼容签名, 内部走 sync push + 提示用户切到 fastboot 模式。
     */
    fun fastbootFlash(serial: String, partition: String, imgPath: String): Boolean {
        Logger.w(TAG, "此方法已弃用, 请用 FastbootClient (设备需进 bootloader)")
        return false
    }

    fun parseAndFlashZip(serial: String, zipPath: String): Pair<Boolean, String> {
        return Pair(false, "请使用 FastbootClient 在 bootloader 模式下刷 ZIP (Phase 3)")
    }
}
