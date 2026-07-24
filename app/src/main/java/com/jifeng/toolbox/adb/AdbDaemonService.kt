package com.jifeng.toolbox.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jifeng.toolbox.R
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.ui.main.MainComposeActivity

/**
 * 后台 ADB 守护进程: USB 设备连接时启动, 保持 USB 通道活跃并显示常驻通知;
 * 设备断开时由 UsbDeviceManager 停止本服务。
 *
 * foregroundServiceType=connectedDevice (Android 14+ 强制声明)。
 */
class AdbDaemonService : Service() {

    companion object {
        const val CHANNEL_ID = "adbd_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification())
        Logger.i("AdbDaemon", "服务启动 - 设备已连接")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 每次 start 都刷新通知文本 (保持 "设备已连接" 常驻)
        startForegroundCompat(buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.i("AdbDaemon", "服务停止 - 设备已断开")
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "ADB 后台通道", NotificationManager.IMPORTANCE_LOW).apply {
                description = "USB 设备连接保活通知"
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainComposeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("极风工具箱 - 设备已连接")
            .setContentText("USB OTG 通道活跃中")
            .setSmallIcon(R.drawable.ic_jf_logo)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** API 34+ 需显式声明 foregroundServiceType; 之前版本无类型重载。 */
    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
