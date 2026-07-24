package com.jifeng.toolbox.fastboot

import android.content.Context
import android.hardware.usb.UsbDevice
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.notify.FlashNotificationManager
import com.jifeng.toolbox.usb.UsbDeviceManager
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

/**
 * Fastboot 卡刷包 (ZIP) 解析与全量刷入。
 *
 * 合法 fastboot 包特征 (任一):
 *   - 含 android-info.txt (经典 fastboot 包标识)
 *   - 含多个 .img 文件, 文件名即分区名 (如 boot.img / system.img)
 *
 * 全量刷入流程: 校验 → 逐分区 erase → download → flash, 实时回调进度。
 */
object FastbootFlasher {

    data class PartitionImage(val name: String, val size: Long, val entryName: String)

    data class Progress(val current: Int, val total: Int, val partition: String, val message: String, val ok: Boolean)

    /** 校验 ZIP 是否为合法 fastboot 卡刷包。 */
    fun validate(zipPath: String): Boolean {
        return try {
            ZipFile(File(zipPath)).use { zf ->
                val entries = zf.entries.toList().map { it.name }
                val hasInfo = entries.any { it.equals("android-info.txt", true) }
                val hasImgs = entries.any { it.endsWith(".img", true) }
                Logger.i("Validate", "entries=${entries.size} android-info=$hasInfo imgs=$hasImgs")
                hasInfo || hasImgs
            }
        } catch (e: Exception) { Logger.e("Validate", e.message ?: ""); false }
    }

    /** 解析 ZIP, 列出待刷分区镜像。 */
    fun listPartitions(zipPath: String): List<PartitionImage> {
        return try {
            ZipFile(File(zipPath)).use { zf ->
                zf.entries.toList()
                    .filter { it.name.endsWith(".img", true) && !it.isDirectory }
                    .map {
                        val part = File(it.name).nameWithoutExtension.lowercase()
                        PartitionImage(part, it.size, it.name)
                    }
                    .sortedBy { it.name }
            }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * 全量刷入 ZIP 内所有分区镜像。
     * @param onProgress 每步回调 (current/total/partition/message/ok)
     * @return 是否全部成功
     */
    fun flashZip(zipPath: String, client: FastbootClient, onProgress: (Progress) -> Unit): Boolean {
        if (!client.isOpen) { onProgress(Progress(0, 0, "", "Fastboot 未连接", false)); return false }
        if (!validate(zipPath)) { onProgress(Progress(0, 0, "", "不是合法 fastboot 卡刷包", false)); return false }

        val parts = listPartitions(zipPath)
        if (parts.isEmpty()) { onProgress(Progress(0, 0, "", "包内无 .img 镜像", false)); return false }

        var allOk = true
        parts.forEachIndexed { idx, p ->
            val cur = idx + 1
            onProgress(Progress(cur, parts.size, p.name, "开始处理 ${p.entryName}", true))
            try {
                ZipFile(File(zipPath)).use { zf ->
                    val entry = zf.getEntry(p.entryName) ?: return@use
                    val data = zf.getInputStream(entry).use { it.readBytes() }
                    // 受保护分区警告
                    if (p.name in PROTECTED) {
                        onProgress(Progress(cur, parts.size, p.name, "⚠ 受保护分区 ${p.name}, 谨慎", true))
                    }
                    // erase (可选, 某些分区 erase 会失败但不影响 flash)
                    client.erase(p.name) { msg -> onProgress(Progress(cur, parts.size, p.name, msg, true)) }
                    val ok = client.flash(p.name, data,
                        onInfo = { msg -> onProgress(Progress(cur, parts.size, p.name, msg, true)) },
                        onProgress = { pct -> onProgress(Progress(cur, parts.size, p.name, "传输 $pct%", true)) })
                    if (!ok) allOk = false
                    onProgress(Progress(cur, parts.size, p.name, if (ok) "✓ 完成" else "✗ 失败", ok))
                }
            } catch (e: Exception) {
                allOk = false
                onProgress(Progress(cur, parts.size, p.name, "异常: ${e.message}", false))
            }
        }
        onProgress(Progress(parts.size, parts.size, "", if (allOk) "全部刷写完成" else "部分失败", allOk))
        return allOk
    }

    /**
     * 高阶刷写入口 (UI 调用): 打开 fastboot 设备 → 校验 → 逐分区 erase/download/flash →
     * 通知栏进度 → 完成后 reboot。
     *
     * 调用方须确保设备已进 bootloader 模式 (UsbDeviceManager.isFastbootDevice(device) == true)。
     * 此方法内部管理 FastbootClient 生命周期与 FlashNotificationManager 通知, 不可在主线程调用。
     *
     * @param ctx 用于 UsbDeviceManager 与通知栏 (任意 Context, 内部取 applicationContext)
     * @param device 已检测到的 fastboot USB 设备
     * @param zipPath 卡刷包本地路径
     * @param onProgress (partitionName, current, total) 每步进度回调 (current 从 1 起)
     * @param onLog 日志回调 (含 [cur/total] 前缀, 可直接显示)
     * @param rebootOnSuccess 全部成功后是否自动 reboot 设备
     * @return 是否全部刷写成功
     */
    fun flash(
        ctx: Context,
        device: UsbDevice,
        zipPath: String,
        onProgress: (partitionName: String, current: Int, total: Int) -> Unit,
        onLog: (msg: String) -> Unit,
        rebootOnSuccess: Boolean = true
    ): Boolean {
        val usbMgr = UsbDeviceManager.get(ctx)

        // 1. 校验卡刷包 (在打开设备前快速失败)
        onLog("校验卡刷包: ${File(zipPath).name}")
        if (!validate(zipPath)) {
            onLog("❌ 不是合法 fastboot 卡刷包")
            FlashNotificationManager.flashFailed(ctx, "校验", "不是合法 fastboot 卡刷包")
            return false
        }
        val parts = listPartitions(zipPath)
        if (parts.isEmpty()) {
            onLog("❌ 包内无 .img 镜像")
            FlashNotificationManager.flashFailed(ctx, "包", "包内无镜像")
            return false
        }
        onLog("✅ 校验通过: 合法 fastboot 卡刷包, 含 ${parts.size} 个分区镜像")

        // 2. USB 权限检查 (复用主页已授权的权限; 未授权则请求并提示用户重试)
        if (!usbMgr.hasPermission(device)) {
            onLog("请求 fastboot 设备 USB 权限: ${device.deviceName} vid=${device.vendorId} pid=${device.productId}")
            usbMgr.requestFastbootPermission(device)
            onLog("请在系统弹窗中授权后, 再次点击「开始刷写」")
            FlashNotificationManager.flashFailed(ctx, "设备", "等待 USB 权限授权")
            return false
        }

        // 3. 打开 USB 设备 + FastbootClient
        val rawConn = usbMgr.openDevice(device)
        if (rawConn == null) {
            onLog("❌ 打开 USB 设备失败 (权限被撤销或设备已拔出)")
            FlashNotificationManager.flashFailed(ctx, "设备", "打开 USB 设备失败")
            return false
        }
        val client = FastbootClient()
        if (!client.open(device, rawConn)) {
            onLog("❌ Fastboot 接口打开失败 (设备可能未真正进入 bootloader)")
            client.close()
            FlashNotificationManager.flashFailed(ctx, "设备", "Fastboot 接口打开失败")
            return false
        }
        onLog("✅ Fastboot 设备已连接: ${device.deviceName}")

        // 4. 启动通知 + 真实刷写
        FlashNotificationManager.startFlash(ctx, parts.size)
        var allOk = false
        try {
            allOk = flashZip(zipPath, client) { p ->
                // 日志回调: 带 [cur/total] 与分区名前缀
                if (p.message.isNotBlank()) onLog(buildString {
                    if (p.total > 0) append("[${p.current}/${p.total}] ")
                    if (p.partition.isNotBlank()) append("${p.partition}: ")
                    append(p.message)
                })
                // 进度回调 + 通知栏更新 (仅分区级进度, 传输百分比不刷通知以免淹没)
                if (p.partition.isNotBlank() && p.total > 0) {
                    onProgress(p.partition, p.current, p.total)
                    FlashNotificationManager.updateProgress(ctx, p.partition, p.current, p.total)
                }
                // 单分区失败 → 失败通知 (不立即 return, 继续尝试后续分区由调用方决定)
                if (p.message.startsWith("✗ 失败") && p.partition.isNotBlank()) {
                    FlashNotificationManager.flashFailed(ctx, p.partition, p.message)
                }
            }
        } catch (e: Exception) {
            onLog("❌ 刷写异常: ${e.message}")
            FlashNotificationManager.flashFailed(ctx, "刷写", e.message ?: "未知异常")
        } finally {
            // 5. 成功则 reboot, 然后关闭 client
            if (allOk && rebootOnSuccess) {
                try {
                    onLog("全部成功, 重启设备...")
                    client.reboot()
                } catch (e: Exception) {
                    Logger.w("Fastboot", "reboot 失败 (可手动重启): ${e.message}")
                }
            }
            client.close()
        }

        // 6. 收尾通知
        if (allOk) {
            onLog("✅ 全部刷写完成, 设备已重启")
            FlashNotificationManager.flashSuccess(ctx)
        } else {
            onLog("⚠ 部分分区刷写失败, 请查看上方日志")
            // 失败通知已在单分区失败时发出; 若整体异常则已在 catch 中发出
        }
        return allOk
    }

    /** 受保护分区: 误刷可能变砖, 刷前高亮提示。 */
    val PROTECTED = setOf(
        "modem", "radio", "persist", "nvdata", "nvram", "aboot", "sbl1", "tz", "rpm",
        "hboot", "bootloader", "xbl", "xbl_config", "abl", "aop", "hyp", "devcfg",
        "keymaster", "keystore", "frp", "misc"
    )
}
