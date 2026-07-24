package com.jifeng.toolbox.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.adb.protocol.AdbTransport
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * USB 设备编排: 枚举 OTG 设备, 请求权限, 触发 ADB 连接, 对外暴露连接状态。
 * 热插拔: 通过 BroadcastReceiver 监听 ATTACHED/DETACHED, 0.1s 级响应。
 */
object UsbDeviceManager {

    private const val TAG = "UsbMgr"
    const val ACTION_USB_PERM = "com.jifeng.toolbox.USB_PERMISSION"

    enum class ConnState { DISCONNECTED, REQUESTING, CONNECTING, CONNECTED, FASTBOOT, FAILED }

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state

    @Volatile
    var pendingDevice: UsbDevice? = null
        private set

    private var ctx: Context? = null

    fun init(context: Context) {
        ctx = context.applicationContext
        register(context.applicationContext)
        // 启动时检查已插入设备
        context.applicationContext.let { scanExisting(it) }
    }

    private fun register(c: Context) {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERM)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // 复用 UsbPermissionReceiver 的接收逻辑, 这里直接内联一个 receiver
        c.registerReceiver(usbReceiver, filter)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            when (intent.action) {
                ACTION_USB_PERM -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Logger.i(TAG, "权限结果 ${device?.deviceName} granted=$granted")
                    if (granted && device != null) {
                        _state.value = ConnState.CONNECTING
                        kotlinx.coroutines.GlobalScope.launch { connect(context, device) }
                    } else {
                        _state.value = ConnState.FAILED
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Logger.i(TAG, "设备插入 ${device?.deviceName} vid=${device?.vendorId} pid=${device?.productId}")
                    device?.let { requestPermission(context, it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Logger.i(TAG, "设备拔出 ${device?.deviceName}")
                    if (device?.deviceName == pendingDevice?.deviceName) {
                        AdbManager.disconnect()
                        _state.value = ConnState.DISCONNECTED
                        pendingDevice = null
                    }
                }
            }
        }
    }

    private fun scanExisting(c: Context) {
        val mgr = c.getSystemService(Context.USB_SERVICE) as UsbManager
        mgr.deviceList.values.firstOrNull { isAdbCapable(it) }?.let {
            requestPermission(c, it)
        }
    }

    private fun isAdbCapable(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == AdbTransport.IFACE_CLASS &&
                iface.interfaceSubclass == AdbTransport.IFACE_SUBCLASS &&
                iface.interfaceProtocol == AdbTransport.IFACE_PROTOCOL_ADB) return true
        }
        return false
    }

    fun requestPermission(c: Context, device: UsbDevice) {
        pendingDevice = device
        _state.value = ConnState.REQUESTING
        val mgr = c.getSystemService(Context.USB_SERVICE) as UsbManager
        if (mgr.hasPermission(device)) {
            _state.value = ConnState.CONNECTING
            kotlinx.coroutines.GlobalScope.launch { connect(c, device) }
            return
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(c, 0,
            Intent(ACTION_USB_PERM).setPackage(c.packageName), flags)
        mgr.requestPermission(device, pi)
    }

    private suspend fun connect(c: Context, device: UsbDevice) {
        val ok = AdbManager.connect(c, device)
        _state.value = if (ok) ConnState.CONNECTED else ConnState.FAILED
        if (ok) pendingDevice = device
    }
}

private object GlobalScope
private fun GlobalScope.launch(block: suspend () -> Unit) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() +
        kotlinx.coroutines.Dispatchers.IO).launch { block() }
}
