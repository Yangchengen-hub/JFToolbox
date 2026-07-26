package com.jifeng.toolbox.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jifeng.toolbox.R
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.ui.wireless.PairingOverlayActivity
import com.jifeng.toolbox.ui.wireless.WirelessDebugComposeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ShellHub 前台服务。
 *
 * 职责:
 * 1. 维持 ShellHub daemon 中继进程长期存活 (不被系统 kill)
 * 2. 在通知栏展示运行状态, 点击跳转到「无线调试」页面 (配对入口)
 * 3. 监听 pendingAuth, 一旦有第三方请求就拉起 ShellHubAuthActivity 悬浮窗
 */
class ShellHubService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var authWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Shell 中枢运行中"))
        Logger.i(TAG, "ShellHubService 已启动 (前台)")
        watchAuthRequests()
    }

    override fun onDestroy() {
        authWatchJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        Logger.i(TAG, "ShellHubService 已销毁")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 监听 ShellHub.pendingAuth, 有请求就拉起悬浮窗 Activity。 */
    private fun watchAuthRequests() {
        authWatchJob = scope.launch {
            ShellHub.pendingAuth.collect { req ->
                if (req != null) {
                    Logger.i(TAG, "拉起授权悬浮窗: ${req.packageName}")
                    val i = Intent(this@ShellHubService,
                        com.jifeng.toolbox.ui.tools.ShellHubAuthActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_PACKAGE, req.packageName)
                        putExtra(EXTRA_LABEL, req.label)
                        putExtra(EXTRA_UID, req.uid)
                    }
                    startActivity(i)
                }
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Shell 中枢", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "ShellHub daemon 运行状态与第三方授权请求"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = Intent(this, WirelessDebugComposeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pairingIntent = Intent(this, PairingOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val pairingPi = PendingIntent.getActivity(
            this, 1, pairingIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ShellHubService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("极风工具箱 · Shell 中枢")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_SERVICE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.addAction(
                android.R.drawable.ic_menu_add, "配对", pairingPi
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi
            )
        }

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        private const val TAG = "ShellHubService"
        private const val CHANNEL_ID = "jf_shellhub"
        private const val NOTIF_ID = 0x4F4F

        const val EXTRA_PACKAGE = "extra_pkg"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_UID = "extra_uid"
        const val ACTION_STOP = "com.jifeng.toolbox.SHELLHUB_STOP"
    }
}
