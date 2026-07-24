package com.jifeng.toolbox.adb.protocol

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.jifeng.toolbox.core.Logger
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * USB Host 传输层: 封装 ADB 报文的收发。
 *
 * ADB USB 接口识别 (AOSP usb_vendors.c / transport_usb.c):
 *   bInterfaceClass=0xFF, bInterfaceSubClass=0x42, bInterfaceProtocol=0x01
 * 两个 bulk 端点: 一个 IN (device→host), 一个 OUT (host→device)。
 *
 * Fastboot 协议复用本类结构, 仅 protocol 字段不同 (0x03), 见 FastbootClient。
 */
class AdbTransport(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint
) {
    private val inBuf = ByteArrayOutputStream()
    private val inRaw = ByteArray(MAX_RAW)

    fun send(msg: AdbMessage) {
        val payload = msg.encode()
        var off = 0
        val chunk = epOut.maxPacketSize.coerceAtLeast(512)
        while (off < payload.size) {
            val len = minOf(payload.size - off, chunk)
            val n = connection.bulkTransfer(epOut, payload, off, len, TIMEOUT_MS)
            if (n < 0) throw IOException("USB bulk OUT 失败 (offset=$off)")
            off += n
            if (n == 0) break
        }
    }

    /** 阻塞读取一个完整 ADB 报文 (头 + 载荷)。 */
    fun receive(): AdbMessage {
        val header = readExact(24)
        val h = AdbMessage.Header.decode(header)
        val data = if (h.dataLength > 0) readExact(h.dataLength) else ByteArray(0)
        require(h.verify(data)) { "ADB CRC 校验失败" }
        return AdbMessage(h.command, h.arg0, h.arg1, data)
    }

    private fun readExact(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            if (inBuf.size() == 0) fillBuffer()
            val available = inBuf.size().toInt()
            if (available == 0) throw IOException("USB bulk IN EOF")
            val take = minOf(n - off, available)
            val tmp = inBuf.toByteArray()
            System.arraycopy(tmp, 0, out, off, take)
            inBuf.reset()
            if (take < available) inBuf.write(tmp, take, available - take)
            off += take
        }
        return out
    }

    private fun fillBuffer() {
        val n = connection.bulkTransfer(epIn, inRaw, inRaw.size, TIMEOUT_MS)
        if (n > 0) inBuf.write(inRaw, 0, n)
    }

    fun release() {
        try { connection.releaseInterface(iface) } catch (_: Exception) {}
        try { connection.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val MAX_RAW = 1024 * 1024 + 24

        /** ADB 接口特征 */
        const val IFACE_CLASS = 0xFF
        const val IFACE_SUBCLASS = 0x42
        const val IFACE_PROTOCOL_ADB = 0x01
        const val IFACE_PROTOCOL_FASTBOOT = 0x03

        /**
         * 在 UsbDevice 上定位 ADB 接口并打开传输通道。
         * @param protocol 0x01=ADB, 0x03=Fastboot
         */
        fun open(
            device: UsbDevice,
            conn: UsbDeviceConnection,
            protocol: Int = IFACE_PROTOCOL_ADB
        ): AdbTransport? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != IFACE_CLASS ||
                    iface.interfaceSubclass != IFACE_SUBCLASS ||
                    iface.interfaceProtocol != protocol) continue

                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
                }
                if (epIn == null || epOut == null) continue
                if (!conn.claimInterface(iface, true)) {
                    Logger.w("AdbTransport", "claimInterface 失败")
                    continue
                }
                return AdbTransport(conn, iface, epIn, epOut)
            }
            return null
        }
    }
}
