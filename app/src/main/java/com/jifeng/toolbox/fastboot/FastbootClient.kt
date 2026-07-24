package com.jifeng.toolbox.fastboot

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.jifeng.toolbox.core.Logger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 真 Fastboot 客户端 (USB bulk 直连)。
 * 协议: 命令为 ASCII 文本 (最大 64 字节, null 填充); 响应为 4 字节类型 + 60 字节载荷。
 * 响应类型: INFO(进度, 可多条) / OKAY(成功终态) / FAIL(失败终态) / DATA(可接收数据)。
 *
 * 前置条件: 被控端必须已进入 bootloader (fastboot mode)。
 */
class FastbootClient {

    private var conn: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var maxDownloadSize: Long = 256L * 1024 * 1024  // 默认 256MB, 后续 getvar 查询

    @Volatile var isOpen = false
        private set

    fun open(device: UsbDevice, rawConn: UsbDeviceConnection): Boolean {
        for (i in 0 until device.interfaceCount) {
            val it = device.getInterface(i)
            if (it.interfaceClass != 0xFF || it.interfaceSubclass != 0x42 || it.interfaceProtocol != 0x03) continue
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (e in 0 until it.endpointCount) {
                val ep = it.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
            }
            if (inEp == null || outEp == null) continue
            if (!rawConn.claimInterface(it, true)) continue
            conn = rawConn; iface = it; epIn = inEp; epOut = outEp
            isOpen = true
            // 查询设备最大下载尺寸
            getvar("max-download-size")?.toLongOrNull()?.let { maxDownloadSize = it }
            Logger.i("Fastboot", "已打开, max-download=$maxDownloadSize")
            return true
        }
        return false
    }

    fun close() {
        iface?.let { conn?.releaseInterface(it) }
        conn?.close()
        conn = null; iface = null; epIn = null; epOut = null
        isOpen = false
    }

    private fun sendCommand(cmd: String) {
        val c = conn ?: throw IOException("未打开")
        val ep = epOut ?: throw IOException("无 OUT 端点")
        val buf = ByteArray(64)
        val bytes = cmd.toByteArray().copyInto(buf, endIndex = minOf(cmd.length, 64))
        val n = c.bulkTransfer(ep, buf, buf.size, TIMEOUT)
        if (n < 0) throw IOException("发送命令失败: $cmd")
    }

    private data class Response(val type: String, val payload: String)

    private fun receive(): Response {
        val c = conn ?: throw IOException("未打开")
        val ep = epIn ?: throw IOException("无 IN 端点")
        val buf = ByteArray(64)
        val n = c.bulkTransfer(ep, buf, buf.size, TIMEOUT)
        if (n < 4) throw IOException("响应过短: $n")
        val type = String(buf, 0, 4)
        val payload = String(buf, 4, minOf(60, n - 4)).trim('\u0000')
        return Response(type, payload)
    }

    /** 收集响应直到终态 (OKAY/FAIL/DATA), 中途 INFO 回调。 */
    private fun collectUntilTerminal(onInfo: (String) -> Unit): Response {
        while (true) {
            val r = receive()
            when (r.type) {
                "INFO" -> onInfo(r.payload)
                "OKAY", "FAIL", "DATA" -> return r
                else -> Logger.w("Fastboot", "未知响应类型: ${r.type}")
            }
        }
    }

    // ---------- 命令 ----------

    fun getvar(name: String): String? {
        return try {
            sendCommand("getvar:$name")
            val r = collectUntilTerminal { }
            if (r.type == "OKAY") r.payload.ifBlank { null } else null
        } catch (e: Exception) { Logger.w("Fastboot", "getvar $name 失败: ${e.message}"); null }
    }

    fun erase(partition: String, onInfo: (String) -> Unit = {}): Boolean {
        return try {
            sendCommand("erase:$partition")
            collectUntilTerminal(onInfo).type == "OKAY"
        } catch (e: Exception) { onInfo("erase 失败: ${e.message}"); false }
    }

    /** 下发镜像数据 (download 命令 + 分块传输)。 */
    fun download(data: ByteArray, onProgress: (Int) -> Unit = {}): Boolean {
        val c = conn ?: return false
        val ep = epOut ?: return false
        return try {
            sendCommand("download:${data.size.toString(16).padStart(8, '0')}")
            val ready = collectUntilTerminal { }
            if (ready.type != "DATA") { Logger.e("Fastboot", "设备未就绪 DATA: ${ready.type}"); return false }
            val chunk = ep.maxPacketSize.coerceAtLeast(512)
            var off = 0
            while (off < data.size) {
                val len = minOf(data.size - off, chunk)
                val n = c.bulkTransfer(ep, data, off, len, TIMEOUT)
                if (n < 0) throw IOException("数据传输失败 offset=$off")
                off += n
                onProgress((off * 100 / data.size))
            }
            val done = collectUntilTerminal { }
            done.type == "OKAY"
        } catch (e: Exception) { Logger.e("Fastboot", "download 失败: ${e.message}"); false }
    }

    /** 下载 + 刷写到指定分区。 */
    fun flash(partition: String, data: ByteArray, onInfo: (String) -> Unit = {}, onProgress: (Int) -> Unit = {}): Boolean {
        if (data.size > maxDownloadSize) {
            onInfo("镜像 ${data.size} 超过设备最大下载尺寸 $maxDownloadSize"); return false
        }
        onInfo("下载 $partition 镜像 (${data.size} bytes)")
        if (!download(data, onProgress)) { onInfo("下载失败"); return false }
        onInfo("刷写 $partition ...")
        sendCommand("flash:$partition")
        val r = collectUntilTerminal(onInfo)
        if (r.type == "OKAY") { onInfo("$partition 刷写完成"); return true }
        onInfo("$partition 刷写失败: ${r.payload}"); return false
    }

    /** 从本地文件刷写单个分区。 */
    fun flashImage(partition: String, imgPath: String, onInfo: (String) -> Unit = {}, onProgress: (Int) -> Unit = {}): Boolean {
        val data = java.io.File(imgPath).readBytes()
        return flash(partition, data, onInfo, onProgress)
    }

    fun reboot(target: String = "") {
        val cmd = if (target.isBlank()) "reboot" else "reboot-$target"
        sendCommand(cmd)
    }

    companion object { private const val TIMEOUT = 30_000 }
}
