package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 基于 TCP Socket 的 ADB transport (无线调试 / adb over tcp)。
 *
 * 适用场景:
 * - 被控端 `adb tcpip 5555` 后直连 5555 端口 (Android 10 及以下经典模式)
 * - 被控端 Android 11+ 无线调试激活后的 active 端口
 *
 * 不依赖 UsbDeviceConnection, 报文格式与 USB 路径一致 (24B 头 + 载荷, 小端, CRC32)。
 * 握手 (CNXN/AUTH) 仍由 AdbConnection.connect() 驱动, 本类只负责字节流收发。
 */
class TcpAdbTransport(
    private val host: String,
    private val port: Int
) : AdbTransport() {

    private val socket = Socket()
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Volatile var opened = false
        private set

    /** 建立 TCP 连接 (不含 ADB 握手; 握手由 AdbConnection.connect 负责)。 */
    fun open() {
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.tcpNoDelay = true
            input = socket.getInputStream()
            output = socket.getOutputStream()
            opened = true
            Logger.i(TAG, "TCP 已连接 $host:$port")
        } catch (e: Exception) {
            opened = false
            throw IOException("TCP 连接 $host:$port 失败: ${e.message}", e)
        }
    }

    override fun send(msg: AdbMessage) {
        val out = output ?: throw IOException("TCP 未 open")
        try {
            out.write(msg.encode())
            out.flush()
        } catch (e: Exception) {
            throw IOException("TCP 写失败: ${e.message}", e)
        }
    }

    override fun receive(): AdbMessage {
        val ins = input ?: throw IOException("TCP 未 open")
        val header = readExact(ins, 24)
        val h = AdbMessage.Header.decode(header)
        val data = if (h.dataLength > 0) readExact(ins, h.dataLength) else ByteArray(0)
        require(h.verify(data)) { "ADB CRC 校验失败" }
        return AdbMessage(h.command, h.arg0, h.arg1, data)
    }

    private fun readExact(ins: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(out, off, n - off)
            if (r < 0) throw IOException("TCP 读 EOF (offset=$off/$n)")
            off += r
        }
        return out
    }

    override fun release() {
        opened = false
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket.close() } catch (_: Exception) {}
        Logger.i(TAG, "TCP 已关闭 $host:$port")
    }

    // TODO: Android 11+ 无线调试需先 `pair host:port code` 完成 SPAKE2 配对,
    //       当前实现仅支持已激活的 5555 端口直连。配对流程参考 AOSP pairing_auth。

    companion object {
        private const val TAG = "TcpAdbTransport"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
