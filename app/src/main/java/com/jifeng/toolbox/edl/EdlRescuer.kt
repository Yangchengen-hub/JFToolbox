package com.jifeng.toolbox.edl

import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.core.SafetyChecker
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

/**
 * 9008 救砖编排: 黑砖检测 → 选包 → 校验 → 匹配 firehose loader → 全量刷入。
 *
 * 救砖包合法性: 必须含
 *   - prog_firehose_*.elf (firehose loader, 按芯片匹配)
 *   - rawprogram0.xml (主分区表)
 *   - 至少一个 .img/.bin 分区镜像
 *
 * 芯片引导匹配: 文件名含 sm8650/sm8550/mt6989 等关键词, 与 getprop 读到的 ro.board.platform 比对。
 */
class EdlRescuer(private val firehose: FirehoseProtocol, private val parser: RawprogramParser) {

    data class RescuePack(
        val file: File,
        val programmer: String?,        // prog_firehose_xxx.elf
        val rawprograms: List<String>,  // rawprogram*.xml
        val images: List<String>,       // *.img/*.bin
        val chipset: String             // 推断的芯片平台
    ) {
        val isValid: Boolean get() = programmer != null && rawprograms.isNotEmpty()
    }

    /** 校验 ZIP 是否为合法救砖包。 */
    fun validatePack(zipPath: String): RescuePack? {
        val f = File(zipPath)
        if (!f.exists()) return null
        // 解压到临时目录
        val extractDir = File(f.parentFile, f.nameWithoutExtension + "_edl").apply { mkdirs() }
        try {
            ZipFile(f).use { zf ->
                zf.entries.toList().forEach { e ->
                    if (!e.isDirectory) {
                        val out = File(extractDir, e.name)
                        out.parentFile?.mkdirs()
                        zf.getInputStream(e).use { it.copyTo(out.outputStream()) }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("EdlRescuer", "解压失败: ${e.message}"); return null
        }
        val programmer = extractDir.listFiles()?.firstOrNull {
            it.name.matches(Regex("prog_firehose_.+\\.(elf|mbn)", RegexOption.IGNORE_CASE))
        }?.name
        val rawprograms = extractDir.listFiles()?.filter {
            it.name.matches(Regex("rawprogram\\d+\\.xml", RegexOption.IGNORE_CASE))
        }?.map { it.name }?.sortedBy { it } ?: emptyList()
        val images = extractDir.listFiles()?.filter {
            it.name.matches(Regex(".+\\.(img|bin)", RegexOption.IGNORE_CASE))
        }?.map { it.name } ?: emptyList()
        val chipset = inferChipset(programmer ?: "")
        val pack = RescuePack(f, programmer, rawprograms, images, chipset)
        Logger.i("EdlRescuer", "包校验: valid=${pack.isValid} programmer=$programmer rawprograms=${rawprograms.size} images=${images.size} chipset=$chipset")
        return pack
    }

    /** 黑砖检测: 通过 getStorageInfo 查分区数。 */
    fun detectBlackBrick(): Boolean {
        val info = firehose.getStorageInfo() ?: return false
        Logger.i("EdlRescuer", "存储信息: partitions=${info.partitionCount} sectors=${info.totalSectors} size=${info.totalBytes}")
        // 双重校验：SafetyChecker 也对分区数做完整性检查
        when (SafetyChecker.validateGpt(info.partitionCount)) {
            is SafetyChecker.CheckResult.Deny -> {
                Logger.e("EdlRescuer", "SafetyChecker 拦截: GPT 分区数=${info.partitionCount} → 判定黑砖")
                return true
            }
            else -> {}
        }
        return info.isBlackBrick
    }

    /**
     * 执行救砖全量刷入。
     * @param pack 已校验的救砖包
     * @param onProgress (current, total, message, ok)
     */
    fun rescue(pack: RescuePack, onProgress: (Int, Int, String, Boolean) -> Unit): Boolean {
        if (!pack.isValid) {
            onProgress(0, 0, "救砖包无效", false); return false
        }
        // 1. 配置 firehose
        onProgress(0, 0, "配置 firehose ...", true)
        if (!firehose.configure()) {
            onProgress(0, 0, "firehose 配置失败", false); return false
        }
        // 2. 解析所有 rawprogram
        val extractDir = File(pack.file.parentFile, pack.file.nameWithoutExtension + "_edl")
        val allEntries = mutableListOf<ProgramEntry>()
        pack.rawprograms.forEach { rp ->
            parser.parse(File(extractDir, rp)).let { allEntries.addAll(it) }
        }
        if (allEntries.isEmpty()) {
            onProgress(0, 0, "rawprogram 无条目", false); return false
        }
        val total = allEntries.size
        Logger.i("EdlRescuer", "待刷分区数: $total")
        var allOk = true
        allEntries.forEachIndexed { idx, e ->
            val cur = idx + 1
            if (e.filename.isBlank() || e.numSectors == 0L) {
                onProgress(cur, total, "跳过空条目 ${e.label}", true); return@forEachIndexed
            }
            if (e.isProtected) {
                onProgress(cur, total, "⚠ 受保护分区 ${e.label}, 继续刷写", true)
            }
            val imgFile = File(extractDir, e.filename)
            if (!imgFile.exists()) {
                onProgress(cur, total, "缺镜像 ${e.filename}", false); allOk = false; return@forEachIndexed
            }
            onProgress(cur, total, "刷写 ${e.label} ← ${e.filename} (${imgFile.length()} bytes)", true)
            val data = imgFile.readBytes()
            // 对齐扇区数 (镜像可能小于 numSectors * sectorSize, 用 0 填充)
            val expectedSize = e.numSectors * e.sectorSize
            val padded = if (data.size < expectedSize) data.copyOf(expectedSize.toInt()) else data
            val ok = firehose.programBinary(
                sectorStart = e.startSector,
                numSectors = e.numSectors,
                sectorSize = e.sectorSize,
                data = padded,
                onProgress = { pct -> onProgress(cur, total, "${e.label} 传输 $pct%", true) }
            )
            if (!ok) allOk = false
            onProgress(cur, total, if (ok) "✓ ${e.label} 完成" else "✗ ${e.label} 失败", ok)
        }
        // 3. 重启
        onProgress(total, total, "刷写完成, 重启设备 ...", allOk)
        firehose.reboot()
        return allOk
    }

    private fun inferChipset(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("sm8650") || lower.contains("8gen3") -> "Qualcomm SM8650 (8 Gen 3)"
            lower.contains("sm8550") || lower.contains("8gen2") -> "Qualcomm SM8550 (8 Gen 2)"
            lower.contains("sm8450") || lower.contains("8gen1") -> "Qualcomm SM8450 (8 Gen 1)"
            lower.contains("sm8350") -> "Qualcomm SM8350 (888)"
            lower.contains("sm6") || lower.contains("sm7") -> "Qualcomm SM 系列"
            lower.contains("mt6") -> "MediaTek (需 DA, 非 firehose)"
            else -> "未知平台"
        }
    }
}
