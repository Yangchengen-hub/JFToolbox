package com.jifeng.toolbox.edl

import com.jifeng.toolbox.core.Logger

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
}
