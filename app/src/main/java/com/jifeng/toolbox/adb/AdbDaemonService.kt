package com.jifeng.toolbox.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jifeng.toolbox.R
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.ui.main.MainActivity

/**
 * 后台 ADB 守护进程：保持 USB 通道活跃、转发日志。
 */
class AdbDaemonService : Service() {

    companion object {
        const val CHANNEL_ID = "adbd_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("ADB 通道已就绪"))
        Logger.i("AdbDaemon", "服务启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.i("AdbDaemon", "服务停止")
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "ADB 后台通道", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(msg: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("极风工具箱")
            .setContentText(msg)
            .setSmallIcon(R.drawable.ic_jf_logo)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
