package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB 文件同步 (sync: 协议), 用于 push/pull。
 * 协议: 在 sync: 流上发送 4 字节命令字 + 数据。
 *   SEND <path_len> <path> + DATA <len> <bytes> ... + DONE <mtime>
 *   RECV <path_len> <path> + 回读 DATA <len> <bytes> ... DONE / FAIL <msg>
 */
class AdbSync(private val conn: AdbConnection) {

    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** 推送本地文件到设备。 */
    fun push(localPath: String, remotePath: String, mtime: Int = (System.currentTimeMillis() / 1000).toInt()): Boolean {
        val local = File(localPath)
        if (!local.exists()) return false
        val s = conn.openStream("sync:")
        return try {
            val pathBytes = remotePath.toByteArray()
            // SEND
            s.writeWindow.acquire()  // openStream 后第一个写
            // 注意: writeWindow 已在 openStream 的 OKAY 时 release 过一次, 这里需 acquire
            // 实际上首次 writeWindow.acquire 会成功(初始 1)。但 openStream 没消耗它。
            // 这里逻辑: openStream 不写数据, 所以 writeWindow 仍是 1, acquire 成功
            conn.writeStream(s, cat("SEND", le32(pathBytes.size), pathBytes))
            // DATA 块
            val data = local.readBytes()
            var off = 0
            val chunk = 64 * 1024
            while (off < data.size) {
                val len = minOf(data.size - off, chunk)
                conn.writeStream(s, cat("DATA", le32(len), data.copyOfRange(off, off + len)))
                off += len
            }
            conn.writeStream(s, cat("DONE", le32(mtime)))
            // 读状态
            val status = s.readBytes(4, 5_000) ?: return false
            val lenBuf = s.readBytes(4, 5_000) ?: return false
            val msgLen = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
            val msg = if (msgLen > 0) String(s.readBytes(msgLen, 5_000) ?: ByteArray(0)) else ""
            if (String(status) == "OKAY") {
                Logger.i("AdbSync", "push $remotePath 成功 (${data.size} bytes)")
                true
            } else {
                Logger.e("AdbSync", "push 失败: $msg"); false
            }
        } catch (e: Exception) {
            Logger.e("AdbSync", "push 异常: ${e.message}"); false
        } finally {
            conn.closeStream(s)
        }
    }

    /** 从设备拉取文件到本地。 */
    fun pull(remotePath: String, localPath: String): Boolean {
        val s = conn.openStream("sync:")
        return try {
            val pathBytes = remotePath.toByteArray()
            conn.writeStream(s, cat("RECV", le32(pathBytes.size), pathBytes))
            FileOutputStream(localPath).use { fos ->
                while (true) {
                    val cmd = s.readBytes(4, 30_000) ?: return false
                    val lenBuf = s.readBytes(4, 5_000) ?: return false
                    val len = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
                    when (String(cmd)) {
                        "DATA" -> {
                            if (len == 0) continue
                            val payload = s.readBytes(len, 60_000) ?: return false
                            fos.write(payload)
                        }
                        "DONE" -> { fos.flush(); Logger.i("AdbSync", "pull $remotePath 成功"); return true }
                        "FAIL" -> {
                            val msg = if (len > 0) String(s.readBytes(len, 5_000) ?: ByteArray(0)) else ""
                            Logger.e("AdbSync", "pull 失败: $msg"); return false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("AdbSync", "pull 异常: ${e.message}"); false
        } finally {
            conn.closeStream(s)
        }
    }

    private fun cat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total); var off = 0
        for (p in parts) { System.arraycopy(p, 0, out, off, p.size); off += p.size }
        return out
    }

    private fun cat(vararg parts: Any): ByteArray =
        cat(*parts.map { when (it) { is ByteArray -> it; is String -> it.toByteArray(); else -> ByteArray(0) } }.toTypedArray())
}
