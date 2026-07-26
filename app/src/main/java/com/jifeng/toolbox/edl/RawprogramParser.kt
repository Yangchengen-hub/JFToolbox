package com.jifeng.toolbox.edl

import com.jifeng.toolbox.core.Logger
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

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

    /** 将 entries 重新序列化为 rawprogram.xml 写回磁盘 (保留 XML 头、data 根节点、program 顺序)。 */
    fun writeXml(file: File, entries: List<ProgramEntry>): Boolean {
        return try {
            val doc = factory.newDocumentBuilder().newDocument()
            val root = doc.createElement("data")
            doc.appendChild(root)
            entries.forEach { e ->
                val prog = doc.createElement("program")
                prog.setAttribute("SECTOR_SIZE_IN_BYTES", e.sectorSize.toString())
                prog.setAttribute("label", e.label)
                prog.setAttribute("filename", e.filename)
                prog.setAttribute("start_sector", e.startSector.toString())
                prog.setAttribute("num_partition_sectors", e.numSectors.toString())
                prog.setAttribute("physical_partition_number", e.physicalPartition.toString())
                root.appendChild(prog)
            }
            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
            file.outputStream().use { os ->
                transformer.transform(DOMSource(doc), StreamResult(os))
            }
            Logger.i("Rawprogram", "写回 ${file.name}: ${entries.size} 条")
            true
        } catch (e: Exception) {
            Logger.e("Rawprogram", "写回 ${file.name} 失败: ${e.message}"); false
        }
    }

    /**
     * 智能编辑单条: 支持调整 start_sector / filename / num_partition_sectors。
     * 自动校验 numSectors 非负、与其它条目不重叠; 重叠返回 null 并 Logger.w。
     */
    fun editEntrySmart(
        orig: ProgramEntry, mods: Map<String, String>, entries: List<ProgramEntry>
    ): ProgramEntry? {
        var newStart = orig.startSector
        var newNum = orig.numSectors
        var newFile = orig.filename
        mods["start_sector"]?.toLongOrNull()?.let { newStart = it }
        mods["num_partition_sectors"]?.toLongOrNull()?.let { newNum = it }
        mods["filename"]?.let { newFile = it }
        if (newNum < 0) {
            Logger.w("Rawprogram", "num_partition_sectors 不能为负: $newNum"); return null
        }
        if (newNum > 0) {
            val newEnd = newStart + newNum
            entries.filter { it.label != orig.label && it.numSectors > 0 }.forEach { other ->
                val otherEnd = other.startSector + other.numSectors
                if (newStart < otherEnd && other.startSector < newEnd) {
                    Logger.w("Rawprogram", "重叠: ${orig.label} [$newStart,$newEnd) 与 ${other.label} [${other.startSector},$otherEnd)")
                    return null
                }
            }
        }
        return orig.copy(startSector = newStart, numSectors = newNum, filename = newFile)
    }

    /** 添加新条目, label 重复时拒绝。 */
    fun addEntry(entries: MutableList<ProgramEntry>, newEntry: ProgramEntry): Boolean {
        if (entries.any { it.label == newEntry.label }) {
            Logger.w("Rawprogram", "label 已存在: ${newEntry.label}"); return false
        }
        entries.add(newEntry); return true
    }

    /** 按 label 删除条目。 */
    fun removeEntry(entries: MutableList<ProgramEntry>, label: String): Boolean {
        val idx = entries.indexOfFirst { it.label == label }
        if (idx < 0) { Logger.w("Rawprogram", "未找到 label: $label"); return false }
        entries.removeAt(idx); return true
    }

    /**
     * 校验整体布局: 检测重叠、空洞、越界。
     * @param totalSectors >0 时检测越界, =0 时跳过
     */
    fun validateLayout(entries: List<ProgramEntry>, totalSectors: Long = 0L): List<String> {
        val warnings = mutableListOf<String>()
        val sorted = entries.filter { it.numSectors > 0 }.sortedBy { it.startSector }
        // 重叠检测
        for (i in 0 until sorted.size - 1) {
            val cur = sorted[i]; val next = sorted[i + 1]
            val curEnd = cur.startSector + cur.numSectors
            if (curEnd > next.startSector) {
                warnings.add("重叠: ${cur.label} 终点 $curEnd > ${next.label} 起点 ${next.startSector}")
            }
        }
        // 空洞检测
        for (i in 0 until sorted.size - 1) {
            val cur = sorted[i]; val next = sorted[i + 1]
            val curEnd = cur.startSector + cur.numSectors
            if (curEnd < next.startSector) {
                warnings.add("空洞: ${cur.label} 与 ${next.label} 之间 ${next.startSector - curEnd} 扇区未分配")
            }
        }
        // 越界检测
        if (totalSectors > 0) {
            sorted.forEach { e ->
                val end = e.startSector + e.numSectors
                if (end > totalSectors) {
                    warnings.add("越界: ${e.label} 终点 $end > totalSectors $totalSectors")
                }
            }
        }
        return warnings
    }

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
