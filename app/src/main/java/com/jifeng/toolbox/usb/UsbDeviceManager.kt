package com.jifeng.toolbox.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
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

    /** 请求 USB 权限 (弹系统对话框)。 */
    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            onPermissionGranted(device); return
        }
        _state.value = State.REQUESTING
        val intent = Intent(UsbPermissionReceiver.ACTION_USB_PERM).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(app, 0, intent, flags)
        usbManager.requestPermission(device, pi)
    }

    /** 由 UsbPermissionReceiver 在收到授权结果后调用。 */
    fun onPermissionGranted(device: UsbDevice) {
        _state.value = State.CONNECTING
        _attachedDevice.value = device
        try {
            val conn = usbManager.openDevice(device)
                ?: throw RuntimeException("openDevice 返回 null")
            val ok = AdbManager.connect(device, conn)
            _state.value = if (ok) State.CONNECTED else State.FAILED
        } catch (e: Exception) {
            Logger.e("UsbDevMgr", "连接失败: ${e.message}")
            _state.value = State.FAILED
        }
    }

    fun onPermissionDenied() {
        _state.value = State.FAILED
    }

    /** 设备拔出。 */
    fun onDeviceDetached() {
        AdbManager.disconnect()
        _attachedDevice.value = null
        _state.value = State.DISCONNECTED
    }

    /** 启动时扫描已插入设备并自动请求权限。 */
    fun scanAndConnect() {
        val dev = listDevices().firstOrNull() ?: return
        requestPermission(dev)
    }

    companion object {
        @Volatile private var instance: UsbDeviceManager? = null
        fun get(ctx: Context): UsbDeviceManager =
            instance ?: synchronized(this) { instance ?: UsbDeviceManager(ctx.applicationContext).also { instance = it } }
    }
}
