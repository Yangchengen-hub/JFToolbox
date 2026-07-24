package com.jifeng.toolbox.adb.protocol

import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 单个 ADB 流 (一个 shell:/sync:/tcp: 会话)。
 * 收到的 WRTE 载荷入队; 由 AdbConnection 的接收线程驱动。
 */
class AdbStream(val localId: Int, val dest: String) {

    @Volatile var remoteId: Int = 0
    @Volatile var closed: Boolean = false
        private set

    private val queue = LinkedBlockingQueue<ByteArray>()
    private val accum = ByteArrayOutputStream()

    /** 阻塞读取一行 (\n 结尾) 或到流结束。 */
    fun readLine(timeoutMs: Long = 5_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // 检查 accum 中是否已有换行
            val cur = accum.toByteArray()
            val nl = cur.indexOf('\n'.code.toByte())
            if (nl >= 0) {
                val line = String(cur, 0, nl)
                accum.reset()
                accum.write(cur, nl + 1, cur.size - nl - 1)
                return line
            }
            val chunk = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            accum.write(chunk)
        }
        return null
    }

    /** 读取全部剩余输出 (流关闭后可用)。 */
    fun readAll(): ByteArray {
        while (!closed || queue.isNotEmpty()) {
            val chunk = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
            accum.write(chunk)
        }
        return accum.toByteArray()
    }

    internal fun feed(data: ByteArray) {
        queue.offer(data)
    }

    internal fun markClosed() {
        closed = true
    }
}
