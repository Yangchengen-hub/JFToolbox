package com.jifeng.toolbox.adb.protocol

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB sync: 文件传输协议 (push/pull)。
 * 协议: openStream("sync:"), 然后 SEND/RECV/DATA/DONE/OKAY/FAIL 二进制报文。
 * 每个 sync 子命令 = 8 字节: <4 字节命令 ASCII> <4 字节 LE 长度>。
 */
class AdbSync(private val conn: AdbConnection) {

    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** 推送本地文件到被控端。 */
    fun push(localPath: String, remotePath: String, mtime: Int = (System.currentTimeMillis() / 1000).toInt()): Boolean {
        val data = File(localPath).readBytes()
        val s = conn.openStream("sync:")
        try {
            // SEND: "SEND" + path_len + remote_path(",<mode>")
            val sendPath = "$remotePath,33206"  // 0o100666
            conn.writeStream(s, "SEND".toByteArray() + le32(sendPath.toByteArray().size) + sendPath.toByteArray())

            // DATA 块, 每块 ≤ 64KB
            var off = 0
            val chunk = 64 * 1024
            while (off < data.size) {
                val len = minOf(data.size - off, chunk)
                conn.writeStream(s, "DATA".toByteArray() + le32(len) + data.copyOfRange(off, off + len))
                off += len
            }
            // DONE
            conn.writeStream(s, "DONE".toByteArray() + le32(mtime))

            // 读结果: OKAY/FAIL
            val hdr = s.readBytes(8, 10_000) ?: return false
            val status = String(hdr, 0, 4)
            val msgLen = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (msgLen > 0) s.readBytes(msgLen, 5_000)
            return status == "OKAY"
        } finally {
            conn.closeStream(s)
        }
    }

    /** 从被控端拉取文件到本地。 */
    fun pull(remotePath: String, localPath: String): Boolean {
        val s = conn.openStream("sync:")
        try {
            val recvPath = remotePath.toByteArray()
            conn.writeStream(s, "RECV".toByteArray() + le32(recvPath.size) + recvPath)

            FileOutputStream(localPath).use { fos ->
                while (true) {
                    val hdr = s.readBytes(8, 30_000) ?: return false
                    val cmd = String(hdr, 0, 4)
                    val len = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (cmd == "DONE") break
                    if (cmd != "DATA") return false
                    if (len == 0) continue
                    var remaining = len
                    while (remaining > 0) {
                        val part = s.readBytes(remaining, 30_000) ?: return false
                        fos.write(part); remaining -= part.size
                    }
                }
            }
            return true
        } finally {
            conn.closeStream(s)
        }
    }
}
