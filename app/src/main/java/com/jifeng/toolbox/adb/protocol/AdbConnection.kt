package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB 连接: 握手 + RSA 授权 + 接收线程分发 + 流写流控。
 *
 * 握手状态机:
 *   host → CNXN(VERSION, MAX, banner)
 *   device → CNXN (无授权) | AUTH(AUTH_TOKEN, token)
 *   AUTH_TOKEN → host 回 AUTH_SIGNATURE; 被拒则回 AUTH_RSAPUBLICKEY
 *   device → CNXN (用户在屏上同意后)
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

    fun connect(): Boolean = try {
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

    private fun handleAuth() {
        repeat(4) {
            val msg = transport.receive()
            when (msg.command) {
                AdbProtocol.A_CNXN -> return
                AdbProtocol.A_AUTH -> when (msg.arg0) {
                    AdbProtocol.AUTH_TOKEN -> {
                        transport.send(AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_SIGNATURE, 0,
                            keys.signToken(msg.data)))
                    }
                    else -> {
                        val pub = (keys.adbPublicKeyBase64() + "\u0000极风工具箱").toByteArray()
                        transport.send(AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, pub))
                    }
                }
                else -> throw RuntimeException("握手期间意外报文 0x${msg.command.toString(16)}")
            }
        }
        throw RuntimeException("握手超时: 需在被控端屏幕同意 USB 调试")
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
                        AdbProtocol.A_OKAY -> {
                            streams[msg.arg1]?.let { it.remoteId = msg.arg0; it.onOkay() }
                        }
                        AdbProtocol.A_CLSE -> streams.remove(msg.arg1)?.markClosed()
                        else -> {}
                    }
                } catch (e: Exception) {
                    if (connected) Logger.w("AdbConn", "接收异常: ${e.message}")
                    break
                }
            }
        }, "adb-rx").apply { isDaemon = true; start() }
    }

    fun openStream(dest: String): AdbStream {
        val id = nextLocalId.getAndIncrement()
        val s = AdbStream(id, dest)
        streams[id] = s
        transport.send(AdbMessage(AdbProtocol.A_OPEN, id, 0, (dest + '\u0000').toByteArray()))
        return s
    }

    /** 向流写入数据 (遵循 ADB 流控, 等待 OKAY)。 */
    fun writeStream(stream: AdbStream, data: ByteArray, timeoutMs: Long = 10_000) {
        var off = 0
        val chunkSize = 64 * 1024
        while (off < data.size) {
            val len = minOf(data.size - off, chunkSize)
            if (!stream.writeWindow.tryAcquire(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                throw RuntimeException("ADB 流控超时")
            if (stream.remoteId == 0) {
                // 等首个 OKAY 建立 remoteId
                Thread.sleep(20)
            }
            transport.send(AdbMessage(AdbProtocol.A_WRTE, stream.localId, stream.remoteId,
                data.copyOfRange(off, off + len)))
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

    /** 执行 shell 命令, 返回全部输出。 */
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
