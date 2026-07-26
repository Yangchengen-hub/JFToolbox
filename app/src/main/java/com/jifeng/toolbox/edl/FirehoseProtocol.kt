package com.jifeng.toolbox.edl

import com.jifeng.toolbox.core.Logger

/** GPT 分区表项。 */
data class GptEntry(
    val name: String,
    val typeGuid: String,
    val uniqueGuid: String,
    val startLba: Long,
    val endLba: Long
)

/**
 * Firehose 协议封装。每个命令是 XML 文本, 设备返回 <response value="..."/> 等。
 *
 * 关键命令:
 *   <configure ZLPToggle/MaxPayloadSizeToTarget...>     配置传输参数
 *   <program SECTOR_START/num_partition_sectors/PAGESIZE/...> 写分区
 *   <read SECTOR_START/num_partition_sectors/PAGESIZE/...>     读分区
 *   <peek addr64/SizeInBytes/>  内存读取 (探针调试)
 *   <poke addr64/SizeInBytes/value64/>  内存写入
 *   <getstorageinfo/>  获取存储信息 (含分区数, 用于黑砖检测)
 *   <setactiveslot/>    A/B 槽位切换
 *   <power/>            重启
 */
class FirehoseProtocol(private val transport: EdlTransport) {

    @Volatile var maxPayloadToTarget: Int = 1024 * 1024  // 单次写数据上限
        private set

    /** 探测 firehose 是否存活。 */
    fun nop(): Boolean {
        transport.sendCommand("<?xml version=\"1.0\" ?><data><nop /></data>")
        val r = transport.receiveXml(5_000)
        return r.contains("value=\"ACK\"") || r.contains("value=\"OK\"")
    }

    /** 配置传输 (firehose 1.x 启用 ZLP + 大 payload)。 */
    fun configure(): Boolean {
        val xml = "<?xml version=\"1.0\" ?><data>" +
            "<configure MemoryName=\"ufs\" ZLPToggle=\"1\" " +
            "MaxPayloadSizeToTarget=\"1048576\" MaxPayloadSizeFromTarget=\"1048576\" " +
            "SkipStorageInit=\"0\" SkipWrite=\"0\" /></data>"
        transport.sendCommand(xml)
        val r = transport.receiveXml(10_000)
        val ok = r.contains("value=\"ACK\"")
        if (ok) {
            Regex("MaxPayloadSizeToTarget=\\\"(\\d+)\\\"").find(r)?.let {
                maxPayloadToTarget = it.groupValues[1].toInt()
            }
            Logger.i("Firehose", "配置成功, maxPayload=$maxPayloadToTarget")
        }
        return ok
    }

    /** 获取存储信息 (含 partition_count, 用于黑砖检测)。 */
    fun getStorageInfo(): StorageInfo? {
        transport.sendCommand("<?xml version=\"1.0\" ?><data><getstorageinfo /></data>")
        val r = transport.receiveXml(15_000)
        // 典型响应: <response value="ACK" ... total_sectors="..." ... partition_count="16" .../>
        val get = { k: String -> Regex("$k=\\\"(\\d+)\\\"").find(r)?.groupValues?.get(1)?.toLongOrNull() }
        val partitionCount = get("partition_count")
            ?: Regex("num_partitions=\\\"(\\d+)\\\"").find(r)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L
        val totalSectors = get("total_sectors") ?: 0L
        val sectorSize = get("sector_size") ?: 4096L
        return StorageInfo(partitionCount, totalSectors, sectorSize)
    }

    data class StorageInfo(val partitionCount: Long, val totalSectors: Long, val sectorSize: Long) {
        /** 黑砖判定: 分区数为 0 → 中了格机文件。 */
        val isBlackBrick: Boolean get() = partitionCount == 0L
        val totalBytes: Long get() = totalSectors * sectorSize
    }

    /** 写分区 (单段: 从 sectorStart 起 numSectors 个扇区)。数据以 hexdata 传输。 */
    fun programBinary(
        sectorStart: Long, numSectors: Long, sectorSize: Int,
        data: ByteArray, onProgress: (Int) -> Unit = {}
    ): Boolean {
        // 1. 发 program 头
        val head = "<?xml version=\"1.0\" ?><data>" +
            "<program SECTOR_START=\"$sectorStart\" " +
            "num_partition_sectors=\"$numSectors\" " +
            "PAGESIZE=\"$sectorSize\" " +
            "filename=\"data.bin\" /></data>"
        transport.sendCommand(head)
        // 设备返回 ACK 后开始发数据
        val ready = transport.receiveXml(10_000)
        if (!ready.contains("value=\"ACK\"")) {
            Logger.e("Firehose", "program 准备失败: $ready"); return false
        }
        // 2. 分块发送二进制 (使用 ZLP 配置后的 raw 模式)
        val chunkSize = (maxPayloadToTarget / sectorSize) * sectorSize  // 对齐扇区
        var off = 0
        while (off < data.size) {
            val len = minOf(data.size - off, chunkSize)
            transport.sendRaw(data.copyOfRange(off, off + len))
            off += len
            onProgress(off * 100 / data.size)
        }
        // 3. 收终态
        val done = transport.receiveXml(30_000)
        return done.contains("value=\"ACK\"")
    }

    fun reboot(): Boolean {
        transport.sendCommand("<?xml version=\"1.0\" ?><data><power value=\"reset\" /></data>")
        return true  // 设备会断开, 无需等响应
    }

    /** 读取 GPT 头部 numSectors 个扇区 (默认 34, 覆盖 LBA 0-33)。UFS 默认 4096 字节扇区。 */
    fun readGpt(numSectors: Long = 34): ByteArray? {
        val sectorSize = 4096  // UFS 默认; eMMC 为 512
        val xml = "<?xml version=\"1.0\" ?><data>" +
            "<read SECTOR_START=\"0\" num_partition_sectors=\"$numSectors\" " +
            "PAGESIZE=\"$sectorSize\" /></data>"
        transport.sendCommand(xml)
        val ready = transport.receiveXml(10_000)
        if (!ready.contains("value=\"ACK\"")) {
            Logger.e("Firehose", "read GPT 准备失败: $ready"); return null
        }
        val expected = numSectors.toInt() * sectorSize
        return transport.receiveBinary(expected, 30_000)
    }

    /** 解析 GPT 返回分区列表 (LBA 0 保护 MBR, LBA 1 头部, LBA 2-33 分区表项)。 */
    fun getGptPartitionList(): List<GptEntry> {
        val data = readGpt() ?: return emptyList()
        // 检测 GPT 头部 (LBA 1): 512 字节扇区 → 偏移 512; 4096 字节扇区 → 偏移 4096
        val headerOffset = when {
            data.size >= 520 && String(data, 512, 8, Charsets.US_ASCII) == "EFI PART" -> 512
            data.size >= 4104 && String(data, 4096, 8, Charsets.US_ASCII) == "EFI PART" -> 4096
            else -> { Logger.e("Firehose", "GPT 头部签名未找到"); return emptyList() }
        }
        val lbaSize = headerOffset.toLong()
        val entryLba = readLongLE(data, headerOffset + 72)
        val numEntries = readIntLE(data, headerOffset + 80)
        val entrySize = readIntLE(data, headerOffset + 84)
        if (numEntries <= 0 || entrySize <= 0) {
            Logger.e("Firehose", "GPT 分区表参数异常: num=$numEntries size=$entrySize"); return emptyList()
        }
        val entriesOffset = (entryLba * lbaSize).toInt()
        val result = mutableListOf<GptEntry>()
        for (i in 0 until numEntries) {
            val off = entriesOffset + i * entrySize
            if (off + 56 > data.size) break
            val typeGuid = formatGuid(data, off)
            // typeGuid 全零 = 空条目
            if (typeGuid.replace("-", "").all { it == '0' }) continue
            val uniqueGuid = formatGuid(data, off + 16)
            val startLba = readLongLE(data, off + 32)
            val endLba = readLongLE(data, off + 40)
            val name = readUtf16LE(data, off + 56, 72).trimEnd('\u0000')
            result.add(GptEntry(name, typeGuid, uniqueGuid, startLba, endLba))
        }
        Logger.i("Firehose", "GPT 解析: ${result.size} 个分区")
        return result
    }

    private fun readLongLE(data: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((data[off + i].toLong() and 0xFF) shl (i * 8))
        return v
    }

    private fun readIntLE(data: ByteArray, off: Int): Int {
        var v = 0
        for (i in 0 until 4) v = v or ((data[off + i].toInt() and 0xFF) shl (i * 8))
        return v
    }

    /** GUID 混合字节序: 前 3 组小端, 后 2 组大端。 */
    private fun formatGuid(data: ByteArray, off: Int): String {
        fun h(b: Byte) = (b.toInt() and 0xFF).toString(16).padStart(2, '0')
        val p1 = "${h(data[off + 3])}${h(data[off + 2])}${h(data[off + 1])}${h(data[off + 0])}"
        val p2 = "${h(data[off + 5])}${h(data[off + 4])}"
        val p3 = "${h(data[off + 7])}${h(data[off + 6])}"
        val p4 = "${h(data[off + 8])}${h(data[off + 9])}"
        val p5 = (0 until 6).joinToString("") { h(data[off + 10 + it]) }
        return "$p1-$p2-$p3-$p4-$p5"
    }

    private fun readUtf16LE(data: ByteArray, off: Int, len: Int): String {
        val sb = StringBuilder()
        val n = len / 2
        for (i in 0 until n) {
            val lo = data[off + i * 2].toInt() and 0xFF
            val hi = data[off + i * 2 + 1].toInt() and 0xFF
            val c = (hi shl 8) or lo
            if (c == 0) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }
}
