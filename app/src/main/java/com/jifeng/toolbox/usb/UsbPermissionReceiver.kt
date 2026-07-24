package com.jifeng.toolbox.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.jifeng.toolbox.core.Logger

/**
 * 监听 USB 设备插拔 + 权限授权结果。
 * 在 AndroidManifest 中注册以接收 USB_PERMISSION 广播 (PendingIntent 触发, 跨 Activity 生命周期可靠)。
 * USB_DEVICE_ATTACHED/DETACHED 由 MainComposeActivity 的动态接收器处理 (系统对 manifest 注册的插拔广播支持不稳定)。
 */
class UsbPermissionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_USB_PERM = "com.jifeng.toolbox.USB_PERMISSION"
        const val ACTION_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val mgr = UsbDeviceManager.get(ctx)
        when (intent.action) {
            ACTION_USB_PERM -> {
                val device = intent.usbDevice()
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Logger.i("USB", "权限结果: ${device?.deviceName} granted=$granted " +
                    "vid=${device?.vendorId} pid=${device?.productId}")
                when {
                    device == null -> Logger.w("USB", "权限结果无 device extra")
                    granted -> {
                        // 按接口类型路由: fastboot 设备不触发 ADB 连接
                        if (mgr.isFastbootDevice(device) && !mgr.isAdbDevice(device)) {
                            mgr.onFastbootPermissionGranted(device)
                        } else {
                            mgr.onPermissionGranted(device)
                        }
                    }
                    else -> mgr.onPermissionDenied()
                }
            }
            ACTION_DEVICE_ATTACHED -> {
                val device = intent.usbDevice()
                Logger.i("USB", "设备插入: ${device?.deviceName} vid=${device?.vendorId} pid=${device?.productId}")
                device?.let { mgr.onDeviceAttached(it) }
            }
            ACTION_DEVICE_DETACHED -> {
                val device = intent.usbDevice()
                Logger.i("USB", "设备拔出: ${device?.deviceName}")
                mgr.onDeviceDetached()
            }
        }
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33)
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else
            getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
}
