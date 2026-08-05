package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.core.SafetyChecker
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB 文件同步 (sync: 协议), 用于 push/pull。
 *
 * v2 修复:
 *  - P0: push() 原 readBytes() 全量读入内存, 大文件 (如系统镜像) 会 OOM。
 *    改为 FileInputStream 流式分块读取, 每次 64KB。
 *  - push() 成功日志改为显示文件实际大小 (不再依赖内存中的 data.size)
 */
class AdbSync(private val conn: AdbConnection) {

    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** 推送本地文件到设备 (流式分块, 不全量读入内存)。 */
    fun push(localPath: String, remotePath: String, mtime: Int = (System.currentTimeMillis() / 1000).toInt()): Boolean {
        val local = File(localPath)
        if (!local.exists()) return false
        // 路径安全校验：防目录穿越
        when (SafetyChecker.validateRemotePath(remotePath)) {
            is SafetyChecker.CheckResult.Deny -> { Logger.e("AdbSync", "push 路径校验失败: $remotePath"); return false }
            else -> {}
        }
        val s = conn.openStream("sync:")
        var totalSent = 0L
        return try {
            val pathBytes = remotePath.toByteArray()
            // SEND
            s.writeWindow.acquire()
            conn.writeStream(s, cat("SEND", le32(pathBytes.size), pathBytes))

            // DATA 块 — 流式分块读取, 避免 OOM
            val chunkSize = 64 * 1024
            val buffer = ByteArray(chunkSize)
            FileInputStream(local).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read <= 0) break
                    val chunk = if (read == chunkSize) buffer else buffer.copyOfRange(0, read)
                    conn.writeStream(s, cat("DATA", le32(read), chunk))
                    totalSent += read
                }
            }
            conn.writeStream(s, cat("DONE", le32(mtime)))

            // 读状态
            val status = s.readBytes(4, 5_000) ?: return false
            val lenBuf = s.readBytes(4, 5_000) ?: return false
            val msgLen = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
            val msg = if (msgLen > 0) String(s.readBytes(msgLen, 5_000) ?: ByteArray(0)) else ""
            if (String(status) == "OKAY") {
                Logger.i("AdbSync", "push $remotePath 成功 ($totalSent bytes)")
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
        // 路径安全校验：防目录穿越
        when (SafetyChecker.validateRemotePath(remotePath)) {
            is SafetyChecker.CheckResult.Deny -> { Logger.e("AdbSync", "pull 路径校验失败: $remotePath"); return false }
            else -> {}
        }
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
            false
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
