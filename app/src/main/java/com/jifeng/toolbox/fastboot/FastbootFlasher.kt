package com.jifeng.toolbox.fastboot

import com.jifeng.toolbox.core.Logger
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

    /** 受保护分区: 误刷可能变砖, 刷前高亮提示。 */
    val PROTECTED = setOf(
        "modem", "radio", "persist", "nvdata", "nvram", "aboot", "sbl1", "tz", "rpm",
        "hboot", "bootloader", "xbl", "xbl_config", "abl", "aop", "hyp", "devcfg",
        "keymaster", "keystore", "frp", "misc"
    )
}
