package com.jifeng.toolbox.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jifeng.toolbox.R
import com.jifeng.toolbox.core.Logger

/**
 * 刷机进度通知栏管理器 (单例)。
 *
 * 渠道 "flash_progress" (名称"刷机进度", 重要性 LOW, 不响铃): 进度/成功/失败三态。
 * 所有方法在任意线程调用安全 (NotificationManager.notify 线程安全)。
 * 使用前须调用 [init] 注入 Application 上下文 (在 JFToolboxApp.onCreate 中完成)。
 *
 * 通知样式:
 *  - 进度中: 标题 "极风工具箱 - 刷机中", 内容 "<分区名> 镜像 (current/total)", 带进度条
 *  - 失败:   标题 "极风工具箱 - 刷机失败 ❌", 内容 "<分区名>: <错误>", 取消进度条
 *  - 成功:   标题 "刷机完成", 内容 "所有分区刷写成功"
 */
object FlashNotificationManager {

    const val CHANNEL_ID = "flash_progress"
    private const val CHANNEL_NAME = "刷机进度"
    private const val NOTIF_ID = 2001

    private lateinit var appCtx: Context

    /** 注入 Application 上下文并创建通知渠道。 */
    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        ensureChannel()
        Logger.i("FlashNotif", "已初始化, 渠道=$CHANNEL_ID")
    }

    private fun ensureChannel() {
        val mgr = manager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                // 重要性 LOW: 不响铃, 仅在通知栏静默展示进度, 避免刷机过程中频繁打扰
                val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "极风工具箱刷机实时进度"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun manager(): NotificationManager =
        appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 分区名首字母大写, 用于通知内容展示 (如 "boot" → "Boot")。 */
    private fun prettify(partitionName: String): String =
        if (partitionName.isBlank()) partitionName
        else partitionName.substring(0, 1).uppercase() + partitionName.substring(1)

    /**
     * 开始刷机: 显示初始进度通知 (indeterminate → 0/total)。
     * @param totalPartitions 待刷分区总数
     */
    fun startFlash(ctx: Context, totalPartitions: Int) {
        ensureAppCtx(ctx)
        ensureChannel()
        val n = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setContentTitle("极风工具箱 - 刷机中")
            .setContentText("准备刷写 (0/$totalPartitions)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(totalPartitions, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        manager().notify(NOTIF_ID, n)
    }

    /**
     * 更新刷机进度 (按分区计数, ongoing, 不可滑动清除)。
     * @param partitionName 当前正在刷的分区名
     * @param current 当前分区序号 (从 1 起)
     * @param total 分区总数
     */
    fun updateProgress(ctx: Context, partitionName: String, current: Int, total: Int) {
        ensureAppCtx(ctx)
        ensureChannel()
        val n = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setContentTitle("极风工具箱 - 刷机中")
            .setContentText("${prettify(partitionName)} 镜像 ($current/$total)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        manager().notify(NOTIF_ID, n)
    }

    /**
     * 刷机失败: 标题加 ❌, 内容显示分区名与错误原因, 取消进度条。
     * @param partitionName 失败分区名 (或 "校验"/"设备" 等阶段标识)
     * @param error 错误原因
     */
    fun flashFailed(ctx: Context, partitionName: String, error: String) {
        ensureAppCtx(ctx)
        ensureChannel()
        val n = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setContentTitle("极风工具箱 - 刷机失败 ❌")
            .setContentText("${prettify(partitionName)}: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager().notify(NOTIF_ID, n)
    }

    /**
     * 刷机成功: 标题 "刷机完成", 内容 "所有分区刷写成功", 取消进度条。
     */
    fun flashSuccess(ctx: Context) {
        ensureAppCtx(ctx)
        ensureChannel()
        val n = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setContentTitle("刷机完成")
            .setContentText("所有分区刷写成功")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager().notify(NOTIF_ID, n)
    }

    /** 取消刷机通知。 */
    fun cancel(ctx: Context) {
        ensureAppCtx(ctx)
        manager().cancel(NOTIF_ID)
    }

    /**
     * 若 init 未被调用 (例如子进程或单元测试), 用调用方 ctx 兜底。
     * 已初始化时仍优先使用 Application 上下文 (避免 Activity 销毁后泄漏)。
     */
    private fun ensureAppCtx(ctx: Context) {
        if (!::appCtx.isInitialized) {
            appCtx = ctx.applicationContext
            ensureChannel()
        }
    }
}
