package com.jifeng.toolbox.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.jifeng.toolbox.adb.AdbDaemonService
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * USB OTG 设备编排器: 枚举 / 权限 / 热插拔 / 自动建立 ADB 连接。
 * 通过 StateFlow 向 UI 暴露连接状态, 实现需求中 "0.1s 极速识别插拔"。
 */
class UsbDeviceManager private constructor(private val app: Context) {

    enum class State { DISCONNECTED, REQUESTING, CONNECTING, CONNECTED, FAILED }

    private val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _attachedDevice = MutableStateFlow<UsbDevice?>(null)
    val attachedDevice: StateFlow<UsbDevice?> = _attachedDevice.asStateFlow()

    /** 列出当前已插入的设备 (ADB 接口优先)。 */
    fun listDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()

    /** 判断设备是否暴露 ADB 接口 (class=0xFF subclass=0x42 protocol=0x01)。 */
    fun isAdbDevice(device: UsbDevice): Boolean = findInterface(device, IFACE_PROTOCOL_ADB) != null

    /** 判断设备是否暴露 Fastboot 接口 (class=0xFF subclass=0x42 protocol=0x03)。 */
    fun isFastbootDevice(device: UsbDevice): Boolean = findInterface(device, IFACE_PROTOCOL_FASTBOOT) != null

    private fun findInterface(device: UsbDevice, protocol: Int): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == IFACE_CLASS &&
                iface.interfaceSubclass == IFACE_SUBCLASS &&
                iface.interfaceProtocol == protocol) return iface
        }
        return null
    }

    /** 优先返回含 ADB 接口的设备, 没有则回退到第一个插入设备。 */
    fun findAdbDevice(): UsbDevice? {
        val all = listDevices()
        if (all.isEmpty()) return null
        return all.firstOrNull { isAdbDevice(it) } ?: all.first()
    }

    /** 返回含 Fastboot 接口的设备 (设备须已进 bootloader)。 */
    fun findFastbootDevice(): UsbDevice? = listDevices().firstOrNull { isFastbootDevice(it) }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    /** 打开已授权设备, 返回原始 USB 连接。 */
    fun openDevice(device: UsbDevice): android.hardware.usb.UsbDeviceConnection? {
        if (!usbManager.hasPermission(device)) {
            Logger.w(TAG, "openDevice 失败: 未授权 ${device.deviceName}")
            return null
        }
        return usbManager.openDevice(device)
    }

    /** 请求 USB 权限 (弹系统对话框), 授权后走 ADB 连接。 */
    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            onPermissionGranted(device); return
        }
        _state.value = State.REQUESTING
        Logger.i(TAG, "请求 ADB 设备权限: ${device.deviceName} vid=${device.vendorId} pid=${device.productId}")
        val intent = Intent(UsbPermissionReceiver.ACTION_USB_PERM).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(app, 0, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /**
     * 请求 Fastboot 设备权限 (不触发 ADB 连接)。
     * 授权结果由 UsbPermissionReceiver 路由到 onFastbootPermissionGranted。
     */
    fun requestFastbootPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            onFastbootPermissionGranted(device); return
        }
        Logger.i(TAG, "请求 Fastboot 设备权限: ${device.deviceName} vid=${device.vendorId} pid=${device.productId}")
        val intent = Intent(UsbPermissionReceiver.ACTION_USB_PERM).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(app, 0, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /** 由 UsbPermissionReceiver 在收到授权结果后调用 (ADB 设备)。 */
    fun onPermissionGranted(device: UsbDevice) {
        _state.value = State.CONNECTING
        _attachedDevice.value = device
        Logger.i(TAG, "权限已授予, 开始建立 ADB 连接: ${device.deviceName} " +
            "vid=${device.vendorId} pid=${device.productId} iface=${device.interfaceCount}")
        try {
            val conn = usbManager.openDevice(device)
            if (conn == null) {
                Logger.e(TAG, "openDevice 返回 null (权限被撤销或设备已拔出)")
                _state.value = State.FAILED
                return
            }
            if (!isAdbDevice(device)) {
                Logger.w(TAG, "设备 ${device.deviceName} 未暴露 ADB 接口 (class=0xFF sub=0x42 proto=0x01), " +
                    "可能处于 fastboot/9008 模式或非调试设备")
            }
            val ok = AdbManager.connect(device, conn)
            if (ok) {
                Logger.i(TAG, "ADB 连接成功: ${device.deviceName}")
                _state.value = State.CONNECTED
                startDaemonService()
            } else {
                Logger.e(TAG, "AdbManager.connect 返回 false (握手/授权失败, 请在被控端同意 USB 调试)")
                _state.value = State.FAILED
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "连接被拒 (SecurityException): ${e.message}")
            _state.value = State.FAILED
        } catch (e: Exception) {
            Logger.e(TAG, "连接异常: ${e.javaClass.simpleName}: ${e.message}")
            _state.value = State.FAILED
        }
    }

    /** Fastboot 设备授权完成 (不自动连接, 由调用方打开 FastbootClient)。 */
    fun onFastbootPermissionGranted(device: UsbDevice) {
        Logger.i(TAG, "Fastboot 设备权限已授予: ${device.deviceName}")
    }

    fun onPermissionDenied() {
        Logger.w(TAG, "USB 权限被用户拒绝")
        _state.value = State.FAILED
    }

    /** 设备插入 (由热插拔广播触发)。ADB 设备自动请求权限连接。 */
    fun onDeviceAttached(device: UsbDevice) {
        Logger.i(TAG, "设备插入: ${device.deviceName} vid=${device.vendorId} pid=${device.productId} " +
            "adb=${isAdbDevice(device)} fastboot=${isFastbootDevice(device)}")
        if (isAdbDevice(device)) {
            requestPermission(device)
        }
    }

    /** 设备拔出。 */
    fun onDeviceDetached() {
        Logger.i(TAG, "设备拔出, 断开 ADB 连接")
        AdbManager.disconnect()
        _attachedDevice.value = null
        _state.value = State.DISCONNECTED
        stopDaemonService()
    }

    /** 启动时扫描已插入设备并自动请求权限 (ADB 接口优先)。 */
    fun scanAndConnect() {
        val all = listDevices()
        if (all.isEmpty()) {
            Logger.i(TAG, "扫描: 未发现任何 USB 设备")
            return
        }
        Logger.i(TAG, "扫描: 发现 ${all.size} 个 USB 设备")
        all.forEach {
            Logger.i(TAG, "  - ${it.deviceName} vid=${it.vendorId} pid=${it.productId} " +
                "adb=${isAdbDevice(it)} fastboot=${isFastbootDevice(it)}")
        }
        val dev = findAdbDevice() ?: run {
            Logger.w(TAG, "扫描: 未找到含 ADB 接口的设备, 请确认被控端已开启 USB 调试")
            return
        }
        requestPermission(dev)
    }

    private fun startDaemonService() {
        try {
            val i = Intent(app, AdbDaemonService::class.java)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
            Logger.i(TAG, "已启动 AdbDaemonService")
        } catch (e: Exception) {
            Logger.e(TAG, "启动 AdbDaemonService 失败: ${e.message}")
        }
    }

    private fun stopDaemonService() {
        try {
            app.stopService(Intent(app, AdbDaemonService::class.java))
        } catch (e: Exception) {
            Logger.w(TAG, "停止 AdbDaemonService 失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "UsbDevMgr"

        const val IFACE_CLASS = 0xFF
        const val IFACE_SUBCLASS = 0x42
        const val IFACE_PROTOCOL_ADB = 0x01
        const val IFACE_PROTOCOL_FASTBOOT = 0x03

        @Volatile private var instance: UsbDeviceManager? = null
        fun get(ctx: Context): UsbDeviceManager =
            instance ?: synchronized(this) { instance ?: UsbDeviceManager(ctx.applicationContext).also { instance = it } }
    }
}
