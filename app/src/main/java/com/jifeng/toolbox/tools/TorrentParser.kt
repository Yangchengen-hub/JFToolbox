package com.jifeng.toolbox.tools

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * BitTorrent 种子文件 (.torrent) 解析器。
 *
 * 本工具**不实现 BT P2P 协议**, 仅做 bencode 解码与元信息提取,
 * 配合外部 BT 客户端完成实际下载。这样可以在不引入 libtorrent 等重量级依赖、
 * 不增加 APK 体积的前提下, 让用户查看种子内容并生成磁力链接。
 *
 * bencode 格式:
 *  - 整数:  i<number>e           例: i42e  i-3e
 *  - 字符串: <length>:<bytes>     例: 4:spam
 *  - 列表:  l<elements>e         例: l4:spami42ee
 *  - 字典:  d<key><value>...e    例: d3:cow3:moo4:spami42ee
 *           (BT 规范要求字典 key 按 raw bytes 字典序排列)
 *
 * info_hash = SHA1(info dict 的 bencoded 字节)。这里直接取原始字节范围,
 * 比重新 bencode 更可靠(避免原文件 key 顺序与重编码顺序不一致导致 hash 不同)。
 */
object TorrentParser {

    /** 种子解析结果。 */
    data class TorrentInfo(
        val name: String,
        val totalLength: Long,
        val pieceLength: Long,
        val pieceCount: Int,
        val files: List<TorrentFile>,
        val trackers: List<String>,
        val magnetUri: String,
        val infoHashHex: String,
        val createdAt: Long?,
        /** web seed (BEP 19 url-list) HTTP/HTTPS 直链, 可走分段下载器。 */
        val webSeedUrls: List<String> = emptyList()
    )

    /** 种子内单个文件条目。 */
    data class TorrentFile(val path: String, val length: Long)

    /** bencode 值的密封类。 */
    sealed class BValue {
        /** 字典。rawBytes 保存整个 dict 的原始 bencoded 字节(含 d...e), 用于 info_hash 计算。 */
        data class BDict(
            val value: Map<String, BValue>,
            val rawBytes: ByteArray = ByteArray(0)
        ) : BValue()
        data class BList(val value: List<BValue>) : BValue()
        data class BInt(val value: Long) : BValue()
        data class BStr(val value: ByteArray) : BValue()
    }

    /** 解析 torrent 文件。失败返回 null。 */
    fun parse(file: File): TorrentInfo? = runCatching { parseOrThrow(file.readBytes()) }.getOrNull()

    /** 解析 torrent 字节流。失败返回 null。 */
    fun parse(bytes: ByteArray): TorrentInfo? = runCatching { parseOrThrow(bytes) }.getOrNull()

    /**
     * 生成磁力链接: magnet:?xt=urn:btih:<40字符hex>&dn=<name>&tr=<tracker>...
     * 多个 tracker 各占一个 tr= 参数。
     */
    fun buildMagnetUri(info: TorrentInfo): String {
        val sb = StringBuilder("magnet:?xt=urn:btih:")
        sb.append(info.infoHashHex.lowercase())
        sb.append("&dn=").append(urlEnc(info.name))
        info.trackers.forEach { sb.append("&tr=").append(urlEnc(it)) }
        return sb.toString()
    }

    /** bencode 解码入口。 */
    fun bdecode(data: ByteArray): BValue = BencodeReader(data).read()

    /** bencode 编码 (主要用于测试; info_hash 优先使用原始字节)。 */
    fun bencode(value: BValue): ByteArray {
        val out = ByteArrayOutputStream()
        bencodeTo(value, out)
        return out.toByteArray()
    }

    // ---------- 内部实现 ----------

    private fun parseOrThrow(bytes: ByteArray): TorrentInfo {
        val root = bdecode(bytes)
        val dict = (root as? BValue.BDict)?.value
            ?: throw IllegalArgumentException("torrent 根节点不是 dict")
        val infoBDict = dict["info"] as? BValue.BDict
            ?: throw IllegalArgumentException("缺少 info 字段")
        val infoDict = infoBDict.value

        // info_hash 优先用原始字节, 没有则回退到重新 bencode
        val infoRaw = if (infoBDict.rawBytes.isNotEmpty()) infoBDict.rawBytes else bencode(infoBDict)
        val infoHash = sha1Hex(infoRaw)

        // 名称: 优先 name, 回退 name.utf-8
        val name = infoDict.bstr("name") ?: infoDict.bstr("name.utf-8") ?: "unknown"

        val pieceLength = infoDict.bint("piece length") ?: 0L
        val pieces = (infoDict["pieces"] as? BValue.BStr)?.value ?: ByteArray(0)
        val pieceCount = if (pieceLength > 0) pieces.size / 20 else 0  // SHA1 = 20 字节

        // 文件列表: 单文件模式 info.length, 多文件模式 info.files
        val files = mutableListOf<TorrentFile>()
        if (infoDict.containsKey("files")) {
            (infoDict["files"] as? BValue.BList)?.value?.forEach { f ->
                val fdict = (f as? BValue.BDict)?.value ?: return@forEach
                val length = fdict.bint("length") ?: 0L
                val pathSegs = (fdict["path.utf-8"] as? BValue.BList)?.value
                    ?: (fdict["path"] as? BValue.BList)?.value
                    ?: emptyList()
                val path = pathSegs.mapNotNull { (it as? BValue.BStr)?.asString() }
                    .joinToString("/")
                if (path.isNotBlank()) files.add(TorrentFile(path, length))
            }
        } else {
            files.add(TorrentFile(name, infoDict.bint("length") ?: 0L))
        }
        val totalLength = files.sumOf { it.length }

        // tracker: announce + announce-list (BEP 12 多 tracker 层级)
        val trackers = linkedSetOf<String>()
        dict.bstr("announce")?.let { trackers.add(it) }
        (dict["announce-list"] as? BValue.BList)?.value?.forEach { tier ->
            (tier as? BValue.BList)?.value?.forEach { t ->
                (t as? BValue.BStr)?.asString()?.let { trackers.add(it) }
            }
        }

        // web seed (BEP 19 url-list): 单文件为字符串, 多文件为列表
        val webSeeds = mutableListOf<String>()
        when (val ul = dict["url-list"]) {
            is BValue.BStr -> webSeeds.add(ul.asString())
            is BValue.BList -> ul.value.forEach {
                (it as? BValue.BStr)?.asString()?.let { s -> webSeeds.add(s) }
            }
            else -> {}
        }

        val createdAt = dict.bint("creation date")

        val info = TorrentInfo(
            name = name,
            totalLength = totalLength,
            pieceLength = pieceLength,
            pieceCount = pieceCount,
            files = files,
            trackers = trackers.toList(),
            magnetUri = "", // 占位, 下面填充
            infoHashHex = infoHash,
            createdAt = createdAt,
            webSeedUrls = webSeeds
        )
        return info.copy(magnetUri = buildMagnetUri(info))
    }

    private fun bencodeTo(value: BValue, out: ByteArrayOutputStream) {
        when (value) {
            is BValue.BInt -> {
                out.write('i'.code)
                out.write(value.value.toString().toByteArray(Charsets.US_ASCII))
                out.write('e'.code)
            }
            is BValue.BStr -> {
                out.write(value.value.size.toString().toByteArray(Charsets.US_ASCII))
                out.write(':'.code)
                out.write(value.value)
            }
            is BValue.BList -> {
                out.write('l'.code)
                value.value.forEach { bencodeTo(it, out) }
                out.write('e'.code)
            }
            is BValue.BDict -> {
                out.write('d'.code)
                // BT 规范要求 key 按 raw bytes 字典序排列
                value.value.toSortedMap(compareBy { it.toByteArray(Charsets.UTF_8) }).forEach { (k, v) ->
                    val kb = k.toByteArray(Charsets.UTF_8)
                    out.write(kb.size.toString().toByteArray(Charsets.US_ASCII))
                    out.write(':'.code)
                    out.write(kb)
                    bencodeTo(v, out)
                }
                out.write('e'.code)
            }
        }
    }

    /** bencode 递归下降解析器。 */
    private class BencodeReader(private val data: ByteArray) {
        private var pos = 0

        fun read(): BValue {
            if (pos >= data.size) throw IllegalArgumentException("bencode 意外结束")
            return when (val c = data[pos].toInt().toChar()) {
                'i' -> readInt()
                'l' -> readList()
                'd' -> readDict()
                in '0'..'9' -> readString()
                else -> throw IllegalArgumentException("bencode 无效字符 '$c' at $pos")
            }
        }

        private fun readInt(): BValue.BInt {
            pos++ // skip 'i'
            val start = pos
            while (pos < data.size && data[pos].toInt().toChar() != 'e') pos++
            if (pos >= data.size) throw IllegalArgumentException("int 未闭合")
            val num = String(data, start, pos - start, Charsets.US_ASCII).toLong()
            pos++ // skip 'e'
            return BValue.BInt(num)
        }

        private fun readString(): BValue.BStr {
            val lenStart = pos
            while (pos < data.size && data[pos].toInt().toChar() != ':') pos++
            if (pos >= data.size) throw IllegalArgumentException("字符串长度未闭合")
            val len = String(data, lenStart, pos - lenStart, Charsets.US_ASCII).toInt()
            pos++ // skip ':'
            if (len < 0 || pos + len > data.size) throw IllegalArgumentException("字符串越界")
            val bytes = data.copyOfRange(pos, pos + len)
            pos += len
            return BValue.BStr(bytes)
        }

        private fun readList(): BValue.BList {
            pos++ // skip 'l'
            val list = mutableListOf<BValue>()
            while (pos < data.size && data[pos].toInt().toChar() != 'e') {
                list.add(read())
            }
            if (pos >= data.size) throw IllegalArgumentException("list 未闭合")
            pos++ // skip 'e'
            return BValue.BList(list)
        }

        private fun readDict(): BValue.BDict {
            val start = pos          // 'd' 的位置
            pos++ // skip 'd'
            val map = linkedMapOf<String, BValue>()
            while (pos < data.size && data[pos].toInt().toChar() != 'e') {
                val key = readString().value
                val keyStr = String(key, Charsets.UTF_8)
                map[keyStr] = read()
            }
            if (pos >= data.size) throw IllegalArgumentException("dict 未闭合")
            pos++ // skip 'e'
            val raw = data.copyOfRange(start, pos)  // 含 d...e, 用于 info_hash
            return BValue.BDict(map, raw)
        }
    }

    // ---------- 工具方法 ----------

    private fun sha1Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(data)
            .joinToString("") { "%02x".format(it) }

    private fun urlEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun BValue.BStr.asString(): String = String(value, Charsets.UTF_8)

    private fun Map<String, BValue>.bstr(key: String): String? =
        (this[key] as? BValue.BStr)?.asString()

    private fun Map<String, BValue>.bint(key: String): Long? =
        (this[key] as? BValue.BInt)?.value
}
