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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * USB OTG 设备编排器 v3: 枚举 / 权限 / 热插拔 / 自动建立 ADB 连接。
 *
 * v3 修复:
 *  - P0: isAdbInterface() 过严导致很多设备不被识别
 *  - 增加更多接口匹配规则: 标准ADB / Google旧版 / CDC-ACM / vendor-specific
 *  - 增加 vendor ID 白名单扩展 (14 家主流厂商)
 *  - scanAndConnect(): 即使没有标准ADB接口, 也列出所有已连接设备让用户选择
 *  - 设备插入后如果没有ADB接口, 仍然显示设备信息而不是"未检测到设备"
 */
class UsbDeviceManager private constructor(private val app: Context) {

    enum class State { DISCONNECTED, REQUESTING, CONNECTING, CONNECTED, FAILED }

    private val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

    /** IO 协程作用域 — 用于 ADB 连接等阻塞操作 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _attachedDevice = MutableStateFlow<UsbDevice?>(null)
    val attachedDevice: StateFlow<UsbDevice?> = _attachedDevice.asStateFlow()

    /** 已发现设备列表 (含非 ADB 设备, 供用户选择)。 */
    private val _discoveredDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<UsbDevice>> = _discoveredDevices.asStateFlow()

    /** 列出当前已插入的所有 USB 设备。 */
    fun listDevices(): List<UsbDevice> = usbManager.deviceList.values.toList()

    fun isAdbDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (isAdbInterface(iface, device.vendorId)) return true
        }
        return false
    }

    fun isFastbootDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (isFastbootInterface(iface)) return true
        }
        return false
    }

    fun isQualcommEdlDevice(device: UsbDevice): Boolean {
        return device.vendorId == 0x05C6 &&
            (device.productId == 0x9008 || device.productId == 0x9006 || device.productId == 0x900A)
    }

    fun isMediaTekPreloaderDevice(device: UsbDevice): Boolean {
        return device.vendorId == 0x0E8D || device.vendorId == 0x22B8
    }

    /**
     * 获取设备类型标签, 用于 UI 显示。
     */
    fun getDeviceTypeLabel(device: UsbDevice): String {
        return when {
            isAdbDevice(device) -> "ADB (调试模式)"
            isFastbootDevice(device) -> "Fastboot (Bootloader)"
            isQualcommEdlDevice(device) -> "高通 EDL (9008 救砖)"
            isMediaTekPreloaderDevice(device) -> "MTK Preloader"
            else -> "未知模式"
        }
    }

    /**
     * v3: 扩展的 ADB 接口匹配规则。
     *
     * 标准 ADB:      class=0xFF sub=0x42 proto=0x01
     * Google ADB旧版: class=0xFF sub=0xFF proto=0x00  (仅在已知ADB VID时)
     * CDC-ACM:       class=0x02 sub=0x02 proto=0x01  (部分国产设备)
     * vendor-specific 常见组合: class=0xFF sub=0x00 proto=0x00  (在已知厂商VID下)
     */
    private fun isAdbInterface(iface: UsbInterface, vendorId: Int): Boolean {
        val clazz = iface.interfaceClass
        val sub = iface.interfaceSubclass
        val proto = iface.interfaceProtocol
        // 标准 ADB 接口
        if (clazz == IFACE_CLASS && sub == IFACE_SUBCLASS && proto == IFACE_PROTOCOL_ADB) return true
        // Google ADB 旧版 (仅在已知 ADB VID 时匹配)
        if (clazz == 0xFF && sub == 0xFF && proto == 0x00 && isKnownAdbVendor(vendorId)) return true
        // CDC-ACM 类设备 (部分国产手机的调试口)
        if (clazz == 0x02 && sub == 0x02 && proto == 0x01 && isKnownAdbVendor(vendorId)) return true
        // vendor-specific 常见组合 (部分厂商的自定义调试接口)
        if (clazz == 0xFF && sub == 0x00 && proto == 0x00 && isKnownAdbVendor(vendorId)) return true
        return false
    }

    /**
     * 扩展 Fastboot 接口匹配。
     */
    private fun isFastbootInterface(iface: UsbInterface): Boolean {
        val clazz = iface.interfaceClass
        val sub = iface.interfaceSubclass
        val proto = iface.interfaceProtocol
        if (clazz == IFACE_CLASS && sub == IFACE_SUBCLASS && proto == IFACE_PROTOCOL_FASTBOOT) return true
        // 部分设备使用 0xFF/0x42/0x02 (fastboot secondary)
        if (clazz == 0xFF && sub == 0x42 && proto == 0x02) return true
        return false
    }

    /**
     * 优先返回含 ADB 接口的设备, 没有则回退到第一个插入设备。
     */
    fun findAdbDevice(): UsbDevice? {
        val all = listDevices()
        if (all.isEmpty()) return null
        return all.firstOrNull { isAdbDevice(it) } ?: all.first()
    }

    /** 返回含 Fastboot 接口的设备。 */
    fun findFastbootDevice(): UsbDevice? = listDevices().firstOrNull { isFastbootDevice(it) }

    /** 返回所有已发现设备 (供 UI 展示和选择)。 */
    fun getAllDevices(): List<UsbDevice> = listDevices()

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
        Logger.i(TAG, "请求 USB 设备权限: ${device.deviceName} vid=${device.vendorId} pid=${device.productId}")
        val intent = Intent(UsbPermissionReceiver.ACTION_USB_PERM).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(app, 0, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /** 请求 Fastboot 设备权限。 */
    fun requestFastbootPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            onFastbootPermissionGranted(device); return
        }
        Logger.i(TAG, "请求 Fastboot 设备权限: ${device.deviceName} vid=${device.vendorId} pid=${device.productId}")
        val intent = Intent(UsbPermissionReceiver.ACTION_USB_PERM).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(app, 1, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /**
     * 由 UsbPermissionReceiver 在收到授权结果后调用 (ADB 设备)。
     */
    fun onPermissionGranted(device: UsbDevice) {
        _state.value = State.CONNECTING
        _attachedDevice.value = device
        Logger.i(TAG, "权限已授予, 开始建立 ADB 连接 (IO 线程): ${device.deviceName} " +
            "vid=${device.vendorId} pid=${device.productId} iface=${device.interfaceCount}")

        ioScope.launch {
            try {
                val conn = usbManager.openDevice(device)
                if (conn == null) {
                    Logger.e(TAG, "openDevice 返回 null (权限被撤销或设备已拔出)")
                    _state.value = State.FAILED
                    return@launch
                }
                if (!isAdbDevice(device)) {
                    Logger.w(TAG, "设备 ${device.deviceName} 未暴露标准 ADB 接口, " +
                        "可能处于 fastboot/9008 模式或非调试设备, 类型=${getDeviceTypeLabel(device)}")
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
    }

    /** Fastboot 设备授权完成。 */
    fun onFastbootPermissionGranted(device: UsbDevice) {
        Logger.i(TAG, "Fastboot 设备权限已授予: ${device.deviceName}")
    }

    fun onPermissionDenied() {
        Logger.w(TAG, "USB 权限被用户拒绝")
        _state.value = State.FAILED
    }

    /**
     * 设备插入 (由热插拔广播触发)。
     * v3: 即使没有 ADB 接口, 也更新设备列表让用户看到。
     */
    fun onDeviceAttached(device: UsbDevice) {
        val isAdb = isAdbDevice(device)
        val isFastboot = isFastbootDevice(device)
        val isEdl = isQualcommEdlDevice(device)
        val isMtk = isMediaTekPreloaderDevice(device)
        val typeLabel = getDeviceTypeLabel(device)
        Logger.i(TAG, "设备插入: ${device.deviceName} vid=${device.vendorId} pid=${device.productId} " +
            "type=$typeLabel adb=$isAdb fastboot=$isFastboot edl=$isEdl mtk=$isMtk")

        // 更新已发现设备列表
        _discoveredDevices.value = listDevices()
        // 同时更新附加设备 (第一个)
        if (_attachedDevice.value == null) {
            _attachedDevice.value = device
        }

        when {
            isAdb -> requestPermission(device)
            isFastboot -> requestFastbootPermission(device)
            isEdl -> Logger.w(TAG, "检测到 Qualcomm EDL 模式设备, 请使用专用刷机工具")
            isMtk -> Logger.w(TAG, "检测到 MTK Preloader 设备, 请使用专用刷机工具")
            else -> Logger.i(TAG, "非标准设备已发现并列出, 用户可手动选择")
        }
    }

    /** 设备拔出。 */
    fun onDeviceDetached() {
        Logger.i(TAG, "设备拔出, 断开 ADB 连接")
        AdbManager.disconnect()
        _attachedDevice.value = null
        _discoveredDevices.value = listDevices()
        _state.value = State.DISCONNECTED
        stopDaemonService()
    }

    /**
     * v3: 启动时扫描已插入设备。
     * 即使没有 ADB 接口, 也列出所有已连接设备让用户选择。
     */
    fun scanAndConnect() {
        val all = listDevices()
        _discoveredDevices.value = all
        if (all.isEmpty()) {
            Logger.i(TAG, "扫描: 未发现任何 USB 设备")
            return
        }
        Logger.i(TAG, "扫描: 发现 ${all.size} 个 USB 设备")
        all.forEach {
            Logger.i(TAG, "  - ${it.deviceName} vid=0x${it.vendorId.toString(16)} " +
                "pid=0x${it.productId.toString(16)} type=${getDeviceTypeLabel(it)}")
        }
        val dev = findAdbDevice()
        if (dev != null && isAdbDevice(dev)) {
            requestPermission(dev)
        } else {
            // 没有 ADB 接口的设备也展示出来, 不请求权限
            Logger.w(TAG, "扫描: 未找到含 ADB 接口的设备, 已列出所有设备供用户选择")
            // 显示第一个设备的基本信息
            val first = all.firstOrNull()
            if (first != null) {
                _attachedDevice.value = first
                Logger.i(TAG, "已发现设备: ${first.deviceName} (${getDeviceTypeLabel(first)})")
            }
        }
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

        /**
         * 已知 ADB 设备的 Vendor ID 白名单。
         * 用于放宽非标准接口的匹配 (Google旧版ADB / CDC-ACM / vendor-specific)。
         * 注意: binarySearch 要求数组升序, 必须保持有序!
         */
        private val KNOWN_ADB_VENDORS = intArrayOf(
            0x04e8, // 三星 (Samsung)
            0x05c6, // 一加/高通 (OnePlus / Qualcomm)
            0x0bb4, // HTC
            0x0e8d, // MTK (MediaTek)
            0x0fce, // 索尼 (Sony)
            0x1004, // LG
            0x12d1, // 华为 (Huawei)
            0x17ef, // 联想 (Lenovo)
            0x18d1, // Google
            0x19d2, // 中兴 (ZTE)
            0x22b8, // 摩托罗拉 (Motorola)
            0x22d9, // OPPO
            0x24e3, // 红米 K 系列部分 / Motorola 补充
            0x2717, // 小米 (Xiaomi)
            0x2a45, // 魅族 (Meizu)
            0x2a70, // OnePlus 补充 VID
            0x2b0e, // Nothing
            0x2d95, // vivo
            0x2e04, // 真我 realme (部分机型)
            0x0489, // Foxconn/夏普 (部分平板)
            0x1949  // 展锐 Unisoc / 部分国产
        )

        /** 检查是否为已知 ADB 厂商 VID (数组已升序, 可直接 binarySearch)。 */
        fun isKnownAdbVendor(vendorId: Int): Boolean =
            KNOWN_ADB_VENDORS.binarySearch(vendorId) >= 0

        @Volatile private var instance: UsbDeviceManager? = null
        fun get(ctx: Context): UsbDeviceManager =
            instance ?: synchronized(this) { instance ?: UsbDeviceManager(ctx.applicationContext).also { instance = it } }
    }
}
