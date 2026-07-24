package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB 连接: 握手 + RSA 授权 + 接收线程分发。USB 点对点, 一次只连一台被控端。
 *
 * 握手状态机:
 *   host → CNXN(VERSION, MAX, banner)
 *   device → CNXN(无授权) | AUTH(AUTH_TOKEN, token)
 *   token: host → AUTH(AUTH_SIGNATURE, sign(token)); 被拒则 host → AUTH(AUTH_RSAPUBLICKEY, pubkey)
 *   device → CNXN(用户屏上同意后)
 */
class AdbConnection(
    private val transport: AdbTransport,
    private val keys: AdbKeyManager
) {
    @Volatile var connected = false
        private set

    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val nextLocalId = AtomicInteger(1)
    private var rxThread: Thread? = null

    fun connect(): Boolean {
        return try {
            transport.send(AdbMessage(AdbProtocol.A_CNXN, AdbProtocol.VERSION, AdbProtocol.MAX_PAYLOAD,
                AdbProtocol.hostBanner().toByteArray()))
            handleAuth()
            connected = true
            startReceiver()
            Logger.i("AdbConn", "已连接, 授权完成")
            true
        } catch (e: Exception) {
            Logger.e("AdbConn", "连接失败: ${e.message}")
            false
        }
    }

    private fun handleAuth() {
        repeat(3) {
            val msg = transport.receive()
            when (msg.command) {
                AdbProtocol.A_CNXN -> return
                AdbProtocol.A_AUTH -> when (msg.arg0) {
                    AdbProtocol.AUTH_TOKEN -> {
                        val sig = keys.signToken(msg.data)
                        transport.send(AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_SIGNATURE, 0, sig))
                    }
                    else -> {
                        val pub = (keys.adbPublicKeyBase64() + "\u0000极风工具箱").toByteArray()
                        transport.send(AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, pub))
                    }
                }
                else -> throw RuntimeException("握手期意外报文 0x${msg.command.toString(16)}")
            }
        }
        throw RuntimeException("握手超时: 请在被控端屏幕同意 USB 调试")
    }

    private fun startReceiver() {
        rxThread = Thread({
            while (connected) {
                try {
                    val msg = transport.receive()
                    when (msg.command) {
                        AdbProtocol.A_WRTE -> {
                            streams[msg.arg1]?.let { s ->
                                s.feed(msg.data)
                                transport.send(AdbMessage(AdbProtocol.A_OKAY, msg.arg1, msg.arg0, ByteArray(0)))
                            }
                        }
                        AdbProtocol.A_OKAY -> streams[msg.arg1]?.let { s ->
                            s.remoteId = msg.arg0
                            s.onOkay()
                        }
                        AdbProtocol.A_CLSE -> streams.remove(msg.arg1)?.markClosed()
                        AdbProtocol.A_CNXN -> { /* 重连 */ }
                        else -> {}
                    }
                } catch (e: Exception) {
                    if (connected) Logger.w("AdbConn", "接收线程异常: ${e.message}")
                    break
                }
            }
        }, "adb-rx").apply { isDaemon = true; start() }
    }

    /** 打开流。dest 如 "shell:getprop" / "sync:" / "tcp:5555"。 */
    fun openStream(dest: String): AdbStream {
        val id = nextLocalId.getAndIncrement()
        val s = AdbStream(id, dest)
        streams[id] = s
        transport.send(AdbMessage(AdbProtocol.A_OPEN, id, 0, (dest + '\u0000').toByteArray()))
        // 等待对端 OKAY 拿到 remoteId
        val deadline = System.currentTimeMillis() + 5_000
        while (s.remoteId == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        if (s.remoteId == 0) throw RuntimeException("打开流超时: $dest")
        return s
    }

    /** 向流写数据 (遵守 WRTE/OKAY 流控, 自动分片)。 */
    fun writeStream(stream: AdbStream, data: ByteArray) {
        var off = 0
        val maxChunk = 64 * 1024  // ADB 默认窗口
        while (off < data.size) {
            val len = minOf(data.size - off, maxChunk)
            val chunk = data.copyOfRange(off, off + len)
            stream.writeWindow.acquire()  // 等上一个 WRTE 的 OKAY
            transport.send(AdbMessage(AdbProtocol.A_WRTE, stream.localId, stream.remoteId, chunk))
            off += len
        }
    }

    fun closeStream(stream: AdbStream) {
        if (stream.closed) return
        try {
            transport.send(AdbMessage(AdbProtocol.A_CLSE, stream.localId, stream.remoteId, ByteArray(0)))
        } catch (_: Exception) {}
        streams.remove(stream.localId)?.markClosed()
    }

    /** 执行 shell 命令, 返回全部输出 (命令结束后流自动 CLSE)。 */
    fun shell(cmd: String, timeoutMs: Long = 15_000): String {
        val s = openStream("shell:$cmd")
        val out = s.readAll(timeoutMs)
        closeStream(s)
        return String(out).trim()
    }

    fun close() {
        connected = false
        streams.values.forEach { it.markClosed() }
        streams.clear()
        transport.release()
    }
}
