package com.jifeng.toolbox.edl

import com.jifeng.toolbox.core.Logger
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Qualcomm rawprogram.xml 解析。每条 <program> 描述一个分区段:
 *   - partition_number / label / filename / start_sector / num_sectors / physical_partition_number
 * patch0.xml 中的 <program> 用于打补丁 (UPDATE/CLEAR/ZERO), 救砖流程一般应用。
 *
 * 救砖包结构 (QFIL 输出):
 *   rawprogram0.xml   主分区表
 *   rawprogram1.xml   (如有, 多 LUN/多物理分区)
 *   patch0.xml        补丁段
 *   prog_firehose_xxx.elf  firehose loader (需先加载到 9008 设备内存)
 *   *.img / *.bin     各分区镜像
 */
data class ProgramEntry(
    val partitionNumber: Int,
    val label: String,
    val filename: String,
    val startSector: Long,
    val numSectors: Long,
    val physicalPartition: Int,
    val sectorSize: Int,
    val isProtected: Boolean
) {
    val sizeBytes: Long get() = numSectors * sectorSize
}

class RawprogramParser {

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isValidating = false; isNamespaceAware = false
    }

    /** 解析 rawprogram*.xml, 返回所有 <program> 条目。 */
    fun parse(xmlFile: File): List<ProgramEntry> {
        if (!xmlFile.exists()) return emptyList()
        return try {
            val doc = factory.newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("program")
            (0 until nodes.length).mapNotNull { i ->
                val el = nodes.item(i) as? Element ?: return@mapNotNull null
                val label = el.getAttribute("label") ?: ""
                ProgramEntry(
                    partitionNumber = el.getAttribute("physical_partition_number").ifBlank { "0" }.toInt(),
                    label = label,
                    filename = el.getAttribute("filename") ?: "",
                    startSector = el.getAttribute("start_sector").toLongOrNull() ?: 0L,
                    numSectors = el.getAttribute("num_partition_sectors").toLongOrNull() ?: 0L,
                    physicalPartition = el.getAttribute("physical_partition_number").ifBlank { "0" }.toInt(),
                    sectorSize = el.getAttribute("SECTOR_SIZE_IN_BYTES").toIntOrNull() ?: 4096,
                    isProtected = label in PROTECTED_PARTITIONS
                )
            }
        } catch (e: Exception) {
            Logger.e("Rawprogram", "解析 ${xmlFile.name} 失败: ${e.message}"); emptyList()
        }
    }

    /** 智能编辑: 调整 rawprogram.xml 中的 start_sector / filename (用于移植/适配)。 */
    fun editEntries(entries: List<ProgramEntry>, edits: Map<String, ProgramEntry>): List<ProgramEntry> =
        entries.map { edits[it.label] ?: it }

    /** 列出包内所有 rawprogram*.xml 文件。 */
    fun listRawprogramFiles(zipDir: File): List<File> =
        zipDir.listFiles { f -> f.name.matches(Regex("rawprogram\\d+\\.xml", RegexOption.IGNORE_CASE)) }
            ?.sortedBy { it.name } ?: emptyList()

    /** 受保护分区标签 (误刷极易变砖)。 */
    val PROTECTED_PARTITIONS = setOf(
        "modem", "modem_st1", "modem_st2", "rfic", "cdma", "persistent",
        "xbl", "xbl_config", "xbl_ramdump", "abl", "aop", "devcfg", "cmnlib",
        "cmnlib64", "keymaster", "keymasterbak", "keystore", "frp", "misc",
        "toolsfv", "limits", "multiimgoem", "qupfw", "rawdump", "multiimgqti",
        "secdata", "logdump", "logfs", "catecontentvfs", "cateelemvfs",
        "storsec", "dip", "apdp", "msadp", "dtbo"
    )
}
