package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB 连接: 完成握手 + RSA 授权, 并驱动接收线程分发 WRTE 到各 AdbStream。
 *
 * 握手状态机:
 *   host → CNXN(VERSION, MAX, banner)
 *   device → CNXN (无授权) | AUTH(AUTH_TOKEN, token)
 *   若 AUTH_TOKEN: host → AUTH(AUTH_SIGNATURE, sign(token)); 失败则 host → AUTH(AUTH_RSAPUBLICKEY, pubkey)
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
        // 最多 3 轮: token → signature → pubkey
        repeat(3) {
            val msg = transport.receive()
            when (msg.command) {
                AdbProtocol.A_CNXN -> { return }   // 已连上
                AdbProtocol.A_AUTH -> when (msg.arg0) {
                    AdbProtocol.AUTH_TOKEN -> {
                        val sig = keys.signToken(msg.data)
                        transport.send(AdbMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_SIGNATURE, 0, sig))
                    }
                    else -> {
                        // signature 被拒, 回传公钥请求授权
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
                        AdbProtocol.A_WRTE -> streams[msg.arg1]?.feed(msg.data)
                            ?.also { transport.send(AdbMessage(AdbProtocol.A_OKAY, msg.arg1, msg.arg0, ByteArray(0))) }
                        AdbProtocol.A_OKAY -> streams[msg.arg1]?.let { it.remoteId = msg.arg0 }
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

    /** 打开 shell 流。dest 形如 "shell:getprop" 或 "shell:ls -l /"。 */
    fun openStream(dest: String): AdbStream {
        val id = nextLocalId.getAndIncrement()
        val s = AdbStream(id, dest)
        streams[id] = s
        transport.send(AdbMessage(AdbProtocol.A_OPEN, id, 0, (dest + '\u0000').toByteArray()))
        return s
    }

    /** 执行 shell 命令并返回全部输出 (同步, 命令结束后流自动关闭)。 */
    fun shell(cmd: String, timeoutMs: Long = 15_000): String {
        val s = openStream("shell:$cmd")
        val out = s.readAll().also { _ -> }
        // readAll 在 CLSE 后返回; 若超时手动关闭
        return String(out).trim()
    }

    fun close() {
        connected = false
        streams.values.forEach { it.markClosed() }
        streams.clear()
        transport.release()
    }
}
