package com.jifeng.toolbox.adb.protocol

import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 单个 ADB 流 (一个 shell:/sync:/tcp: 会话)。
 * 统一缓冲区, 支持 readLine / readBytes(二进制) / readAll 三种读取。
 * writeWindow 实现 ADB WRTE/OKAY 流控: 每发一个 WRTE 须等对端 OKAY 才能发下一个。
 */
class AdbStream(val localId: Int, val dest: String) {

    @Volatile var remoteId: Int = 0
    @Volatile var closed: Boolean = false
        private set

    /** 写窗口: 初始 1, 发 WRTE 前 acquire, 收到 OKAY 后 release。 */
    val writeWindow = Semaphore(1)

    private val queue = LinkedBlockingQueue<ByteArray>()
    private val buf = ByteArrayOutputStream()

    internal fun feed(data: ByteArray) { queue.offer(data) }
    internal fun markClosed() { closed = true }
    internal fun onOkay() { writeWindow.release() }

    private fun drain(timeoutMs: Long): Boolean {
        val chunk = queue.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return false
        buf.write(chunk); return true
    }

    private fun snapshot(): ByteArray = buf.toByteArray()

    /** 读取恰好 n 字节 (二进制, sync 协议用)。 */
    fun readBytes(n: Int, timeoutMs: Long = 10_000): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (buf.size() < n) {
            if (System.currentTimeMillis() >= deadline) return null
            val remain = deadline - System.currentTimeMillis()
            if (!drain(minOf(200, remain.coerceAtLeast(1)))) {
                if (closed && queue.isEmpty()) return null
            }
        }
        val all = snapshot()
        val out = all.copyOfRange(0, n)
        buf.reset(); buf.write(all, n, all.size - n)
        return out
    }

    /** 读取一行 (\\n 结尾), shell 输出用。 */
    fun readLine(timeoutMs: Long = 5_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val cur = snapshot()
            val nl = cur.indexOf('\n'.code.toByte())
            if (nl >= 0) {
                val line = String(cur, 0, nl)
                buf.reset(); buf.write(cur, nl + 1, cur.size - nl - 1)
                return line
            }
            if (!drain(200) && closed && queue.isEmpty()) {
                return if (cur.isNotEmpty()) { buf.reset(); String(cur) } else null
            }
        }
        return null
    }

    /** 读取全部剩余输出 (流关闭后返回)。 */
    fun readAll(timeoutMs: Long = 15_000): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!(closed && queue.isEmpty())) {
            if (System.currentTimeMillis() > deadline) break
            drain(300)
        }
        return snapshot().also { buf.reset() }
    }
}
