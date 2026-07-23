package com.jifeng.toolbox.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.jifeng.toolbox.core.Logger

/**
 * 监听 USB 设备插拔 + 权限授权结果。
 */
class UsbPermissionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_USB_PERM = "com.jifeng.toolbox.USB_PERMISSION"
        const val ACTION_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_USB_PERM -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Logger.i("USB", "权限结果: ${device?.deviceName} granted=$granted")
            }
            ACTION_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Logger.i("USB", "设备插入: ${device?.deviceName} vid=${device?.vendorId} pid=${device?.productId}")
            }
            ACTION_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Logger.i("USB", "设备拔出: ${device?.deviceName}")
            }
        }
    }
}
