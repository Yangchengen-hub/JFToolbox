package com.jifeng.toolbox.edl

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.jifeng.toolbox.core.Logger
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 9008 EDL (Emergency Download) 传输层。
 *
 * 9008 设备枚举为 Qualcomm HS-USB QDLoader 9008 (VID=05C6 PID=9008),
 * 通常暴露为 1 个 bulk OUT + 1 个 bulk IN 端点 (class=FF, subclass=FF, protocol=FF)。
 *
 * 通信复用 firehose 协议: 主机发 XML 指令文本, 设备回 XML 响应文本。
 * hexdata 段以 16 进制 ASCII 形式传输 (老协议) 或二进制 (firehose 1.x 配置后 ZLP 包)。
 */
class EdlTransport {

    private var conn: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null

    @Volatile var isOpen = false
        private set

    fun open(device: UsbDevice, rawConn: UsbDeviceConnection): Boolean {
        // 9008 设备识别: VID 0x05C6 (Qualcomm), PID 0x9008
        if (device.vendorId != 0x05C6 && device.productId != 0x9008) {
            // 宽松匹配: 只要接口特征符合
        }
        for (i in 0 until device.interfaceCount) {
            val it = device.getInterface(i)
            if (it.interfaceClass != 0xFF) continue
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
            Logger.i("EDL", "9008 传输已打开 (${device.vendorId}:${device.productId})")
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

    /** 发送原始字节 (firehose XML 文本或二进制数据)。 */
    fun sendRaw(data: ByteArray): Int {
        val c = conn ?: throw IOException("未打开")
        val ep = epOut ?: throw IOException("无 OUT 端点")
        val chunk = ep.maxPacketSize.coerceAtLeast(512)
        var off = 0
        while (off < data.size) {
            val len = minOf(data.size - off, chunk)
            val n = c.bulkTransfer(ep, data, off, len, TIMEOUT)
            if (n < 0) throw IOException("OUT 失败 offset=$off")
            off += n
            if (n == 0) break
        }
        return off
    }

    /** 发送 firehose XML 命令 (自动加 null 终止)。 */
    fun sendCommand(xml: String): Int {
        val bytes = xml.toByteArray()
        return sendRaw(bytes)
    }

    /** 阻塞读取直到收到完整 XML 响应 (以 </?xml> 或具名标签闭合为准)。 */
    fun receiveXml(timeoutMs: Int = TIMEOUT): String {
        val c = conn ?: throw IOException("未打开")
        val ep = epIn ?: throw IOException("无 IN 端点")
        val buf = ByteArray(4096)
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val n = c.bulkTransfer(ep, buf, buf.size, 2_000)
            if (n > 0) {
                out.write(buf, 0, n)
                // 检测 XML 闭合
                val s = out.toString(Charsets.UTF_8.name())
                if (s.contains("</data>") || s.contains("</response>") || s.contains("</log>") ||
                    s.contains("<response ") && s.contains("/>") ||
                    s.endsWith("\u0000")) {
                    return s.trim('\u0000').trim()
                }
            }
        }
        val s = out.toString(Charsets.UTF_8.name()).trim('\u0000').trim()
        return s
    }

    /** 读取二进制数据 (firehose 配置后 write/read data 的 raw 模式)。 */
    fun receiveBinary(expectedLen: Int, timeoutMs: Int = TIMEOUT): ByteArray {
        val c = conn ?: throw IOException("未打开")
        val ep = epIn ?: throw IOException("无 IN 端点")
        val out = ByteArrayOutputStream(expectedLen)
        val buf = ByteArray(ep.maxPacketSize.coerceAtLeast(512))
        val deadline = System.currentTimeMillis() + timeoutMs
        while (out.size() < expectedLen && System.currentTimeMillis() < deadline) {
            val n = c.bulkTransfer(ep, buf, buf.size, 2_000)
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object { private const val TIMEOUT = 30_000 }
}
