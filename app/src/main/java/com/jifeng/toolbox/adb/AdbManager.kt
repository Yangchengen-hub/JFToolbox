package com.jifeng.toolbox.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.jifeng.toolbox.adb.protocol.AdbConnection
import com.jifeng.toolbox.adb.protocol.AdbKeyManager
import com.jifeng.toolbox.adb.protocol.AdbSync
import com.jifeng.toolbox.adb.protocol.AdbTransport
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ADB 子系统门面: 管理 USB 连接生命周期 + 对外暴露 shell/push/pull/reboot。
 *
 * 真实实现: UsbDeviceConnection.bulkTransfer ↔ ADB 协议 (无 exec("adb"))。
 * 控制端免 Root; 被控端需开启 USB 调试并在屏幕授权 (这是 Android 安全模型, 无法绕过)。
 *
 * serial 参数保留为 API 兼容, 实际 USB 点对点传输中忽略 (单设备连接)。
 */
object AdbManager {
    private const val TAG = "AdbManager"

    private lateinit var keys: AdbKeyManager
    private var connection: AdbConnection? = null
    private var sync: AdbSync? = null

    @Volatile var currentSerial: String? = null
        private set
    @Volatile var isConnected: Boolean = false
        private set

    val instance: AdbManagerImpl get() = AdbManagerImpl

    fun init(ctx: Context) {
        keys = AdbKeyManager(ctx)
        Logger.i(TAG, "ADB 子系统初始化完成 (真 USB 传输)")
    }

    /** 打开 USB 设备并完成 ADB 握手。需先获得 USB 权限。 */
    suspend fun connect(ctx: Context, device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!mgr.hasPermission(device)) {
            Logger.w(TAG, "无 USB 权限, 请先授权")
            return@withContext false
        }
        val rawConn = mgr.openDevice(device) ?: run {
            Logger.e(TAG, "openDevice 失败"); return@withContext false
        }
        val transport = AdbTransport.open(device, rawConn) ?: run {
            Logger.e(TAG, "未找到 ADB 接口 (class=0xFF/sub=0x42/proto=0x01)")
            return@withContext false
        }
        val adb = AdbConnection(transport, keys)
        if (!adb.connect()) {
            transport.release(); return@withContext false
        }
        connection = adb
        sync = AdbSync(adb)
        isConnected = true
        currentSerial = shell("", "getprop ro.serialno").orEmpty().ifBlank { device.deviceName }
        Logger.i(TAG, "连接成功, serial=$currentSerial")
        true
    }

    fun disconnect() {
        connection?.close()
        connection = null
        sync = null
        isConnected = false
        currentSerial = null
    }

    /** 执行 shell 命令。serial 忽略 (USB 点对点)。 */
    fun shell(@Suppress("UNUSED_PARAMETER") serial: String, cmd: String): String? {
        val c = connection ?: return null
        return try { c.shell(cmd) } catch (e: Exception) {
            Logger.e("AdbShell", "执行失败: ${e.message}"); null
        }
    }

    fun push(@Suppress("UNUSED_PARAMETER") serial: String, local: String, remote: String): Boolean =
        try { sync?.push(local, remote) ?: false } catch (e: Exception) {
            Logger.e("AdbPush", e.message ?: ""); false
        }

    fun pull(@Suppress("UNUSED_PARAMETER") serial: String, remote: String, local: String): Boolean =
        try { sync?.pull(remote, local) ?: false } catch (e: Exception) {
            Logger.e("AdbPull", e.message ?: ""); false
        }

    fun reboot(@Suppress("UNUSED_PARAMETER") serial: String, target: String = "") {
        val cmd = if (target.isBlank()) "reboot" else "reboot $target"
        shell("", cmd)
    }

    /** 当前已连接设备序列号列表 (USB 点对点, 至多 1 个)。 */
    fun listDevices(): List<String> =
        if (isConnected && currentSerial != null) listOf(currentSerial!!) else emptyList()
}

/** 兼容旧调用入口 (DeviceDetector/FlashActivity)。 */
object AdbManagerImpl {
    fun shell(serial: String, cmd: String) = AdbManager.shell(serial, cmd)
    fun push(serial: String, l: String, r: String) = AdbManager.push(serial, l, r)
    fun pull(serial: String, r: String, l: String) = AdbManager.pull(serial, r, l)
    fun reboot(serial: String, target: String) = AdbManager.reboot(serial, target)
    fun listDevices() = AdbManager.listDevices()
}
