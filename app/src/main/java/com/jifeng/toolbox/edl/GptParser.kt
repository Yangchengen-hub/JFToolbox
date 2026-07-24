package com.jifeng.toolbox.edl

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GPT (GUID Partition Table) 解析。用于:
 *   - 分区表编辑器 (查看分区)
 *   - 黑砖检测: GPT 头损坏/分区数为 0 → 中了格机文件
 *
 * GPT 结构:
 *   LBA0:  Protective MBR
 *   LBA1:  GPT Header (signature "EFI PART")
 *   LBA2..: Partition entries (128 个 × 128 字节, 含 type_guid/unique_guid/first_lba/last_lba/attributes/name)
 */
data class GptPartition(
    val index: Int,
    val name: String,
    val typeGuid: String,
    val firstLba: Long,
    val lastLba: Long,
    val attributes: Long
) {
    val sizeLba: Long get() = lastLba - firstLba + 1
}

data class GptHeader(
    val signature: String,
    val revision: Int,
    val headerSize: Int,
    val crc32: Int,
    val currentLba: Long,
    val backupLba: Long,
    val firstUsableLba: Long,
    val lastUsableLba: Long,
    val diskGuid: String,
    val partitionEntryLba: Long,
    val numPartitions: Int,
    val partitionEntrySize: Int
)

data class GptTable(val header: GptHeader?, val partitions: List<GptPartition>) {
    /** 黑砖判定: GPT 头缺失或分区数为 0 → 中了格机文件导致黑砖。 */
    val isBlackBrick: Boolean
        get() = header == null || header.numPartitions == 0 || partitions.isEmpty()
}

class GptParser {

    /**
     * 解析从设备读出的 GPT 区域 (LBA0 + LBA1 + 至少一个分区条目块)。
     * @param gptBytes 至少 2 * sectorSize 字节 (含 MBR + GPT 头)
     * @param sectorSize 扇区大小 (通常 4096 或 512)
     * @param gptEntriesBytes 分区条目区数据 (可单独读 LBA2+)
     */
    fun parse(gptBytes: ByteArray, sectorSize: Int, gptEntriesBytes: ByteArray? = null): GptTable {
        if (gptBytes.size < 2 * sectorSize) return GptTable(null, emptyList())
        val buf = ByteBuffer.wrap(gptBytes).order(ByteOrder.LITTLE_ENDIAN)
        // 跳过 protective MBR (LBA0)
        buf.position(sectorSize)
        val sig = String(gptBytes, sectorSize, 8, Charsets.US_ASCII)
        if (sig != "EFI PART") {
 // 主 GPT 头损坏, 尝试备份 GPT (位于磁盘末尾, 这里不直接读, 留上层处理)
            return GptTable(null, emptyList())
        }
        val header = GptHeader(
            signature = sig,
            revision = buf.int,
            headerSize = buf.int,
            crc32 = buf.int,
            currentLba = buf.long,
            backupLba = buf.long,
            firstUsableLba = buf.long,
            lastUsableLba = buf.long,
            diskGuid = readGuid(buf),
            partitionEntryLba = buf.long,
            numPartitions = buf.int,
            partitionEntrySize = buf.int
        )
        // 分区条目
        val entriesData = gptEntriesBytes ?: gptBytes.copyOfRange(2 * sectorSize, minOf(gptBytes.size, 2 * sectorSize + header.numPartitions * header.partitionEntrySize))
        val parts = parseEntries(entriesData, header.numPartitions, header.partitionEntrySize)
        return GptTable(header, parts)
    }

    private fun parseEntries(data: ByteArray, count: Int, entrySize: Int): List<GptPartition> {
        val list = mutableListOf<GptPartition>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            if (buf.position() + entrySize > data.size) break
            val startPos = buf.position()
            val typeGuid = readGuid(buf)
            val uniqueGuid = readGuid(buf)
            val firstLba = buf.long
            val lastLba = buf.long
            val attrs = buf.long
            // 名字: UTF-16LE, 72 字节 (36 chars)
            val nameBytes = ByteArray(72)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_16LE).trimEnd('\u0000')
            // 跳到下一条目 (entrySize 可能 > 128)
            buf.position(startPos + entrySize)
            if (typeGuid == "00000000-0000-0000-0000-000000000000") continue
            list.add(GptPartition(i, name, typeGuid, firstLba, lastLba, attrs))
        }
        return list
    }

    private fun readGuid(buf: ByteBuffer): String {
        val d1 = Integer.reverseBytes(buf.int)
        val d2 = java.lang.Short.reverseBytes(buf.short).toInt() and 0xFFFF
        val d3 = java.lang.Short.reverseBytes(buf.short).toInt() and 0xFFFF
        val b1 = byteToHex(buf.get())
        val b2 = byteToHex(buf.get())
        val b3 = byteToHex(buf.get())
        val b4 = byteToHex(buf.get())
        val b5 = byteToHex(buf.get())
        val b6 = byteToHex(buf.get())
        return String.format("%08x-%04x-%04x-%s%s-%s%s%s%s%s%s",
            d1, d2, d3, b1, b2, b3, b4, b5, b6, byteToHex(buf.get()), byteToHex(buf.get()))
    }

    private fun byteToHex(b: Byte): String = String.format("%02x", b.toInt() and 0xFF)
}
