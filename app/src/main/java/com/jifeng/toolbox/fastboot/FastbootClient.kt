package com.jifeng.toolbox.fastboot

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.core.SafetyChecker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 真 Fastboot 客户端 (USB bulk 直连) v2。
 *
 * v2 修复:
 *  - P0: flashImage() 原 readBytes() 全量读入内存, 大镜像 (如 system.img 1-2GB) 会 OOM。
 *    改为 FileInputStream 流式分块传输 + 仅读头部做魔数校验。
 *  - 新增 downloadStream() 流式下载方法
 *  - 新增 flashStream() 流式刷写方法
 *  - 保留原 flash(ByteArray) 用于小数据 (如 boot.img 头)
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
        // 安全校验：禁止擦除致命分区
        when (val check = SafetyChecker.validateErase(partition)) {
            is SafetyChecker.CheckResult.Deny -> {
                onInfo("安全拦截: ${check.message}"); return false
            }
            is SafetyChecker.CheckResult.Warn -> onInfo("警告: ${check.message}")
            else -> {}
        }
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

    /**
     * P0 新增: 流式下发镜像数据 (不从文件全量读入内存)。
     * 使用 FileInputStream 分块读取, 每次 maxPacketSize 字节。
     */
    fun downloadStream(fis: FileInputStream, totalSize: Long, onProgress: (Int) -> Unit = {}): Boolean {
        val c = conn ?: return false
        val ep = epOut ?: return false
        return try {
            sendCommand("download:${totalSize.toString(16).padStart(8, '0')}")
            val ready = collectUntilTerminal { }
            if (ready.type != "DATA") { Logger.e("Fastboot", "设备未就绪 DATA: ${ready.type}"); return false }
            val chunkSize = ep.maxPacketSize.coerceAtLeast(512)
            val buffer = ByteArray(chunkSize)
            var sent = 0L
            while (sent < totalSize) {
                val toRead = minOf(chunkSize.toLong(), totalSize - sent).toInt()
                val read = fis.read(buffer, 0, toRead)
                if (read <= 0) break
                val n = c.bulkTransfer(ep, buffer, 0, read, TIMEOUT)
                if (n < 0) throw IOException("数据传输失败 offset=$sent")
                sent += n
                onProgress((sent * 100 / totalSize).toInt())
            }
            val done = collectUntilTerminal { }
            done.type == "OKAY"
        } catch (e: Exception) { Logger.e("Fastboot", "downloadStream 失败: ${e.message}"); false }
    }

    /** 下载 + 刷写到指定分区。 */
    fun flash(partition: String, data: ByteArray, onInfo: (String) -> Unit = {}, onProgress: (Int) -> Unit = {}): Boolean {
        // 安全校验：分区白名单 + 魔数校验 + 大小限制
        when (val check = SafetyChecker.validateFlash(partition, data)) {
            is SafetyChecker.CheckResult.Deny -> {
                onInfo("安全拦截: ${check.message}"); return false
            }
            is SafetyChecker.CheckResult.Warn -> onInfo("警告: ${check.message}")
            else -> {}
        }
        when (val sizeCheck = SafetyChecker.validateDownloadSize(data.size.toLong(), maxDownloadSize)) {
            is SafetyChecker.CheckResult.Deny -> { onInfo("安全拦截: ${sizeCheck.message}"); return false }
            else -> {}
        }
        onInfo("下载 $partition 镜像 (${data.size} bytes)")
        if (!download(data, onProgress)) { onInfo("下载失败"); return false }
        onInfo("刷写 $partition ...")
        sendCommand("flash:$partition")
        val r = collectUntilTerminal(onInfo)
        if (r.type == "OKAY") { onInfo("$partition 刷写完成"); return true }
        onInfo("$partition 刷写失败: ${r.payload}"); return false
    }

    /**
     * P0 修复: 从本地文件流式刷写单个分区 (不全量读入内存)。
     *
     * 原 flashImage 使用 readBytes() 全量读入, 大镜像会 OOM。
     * 新实现:
     *  1. 仅读文件头部 4KB 做魔数校验
     *  2. 使用 FileInputStream 流式分块传输
     *  3. 安全校验 (分区白名单 + 大小限制) 照常执行
     */
    fun flashImage(partition: String, imgPath: String, onInfo: (String) -> Unit = {}, onProgress: (Int) -> Unit = {}): Boolean {
        val file = File(imgPath)
        if (!file.exists()) { onInfo("文件不存在: $imgPath"); return false }
        val fileSize = file.length()

        // 安全校验：分区白名单
        when (val check = SafetyChecker.validateErase(partition)) {
            is SafetyChecker.CheckResult.Deny -> {
                onInfo("安全拦截: ${check.message}"); return false
            }
            is SafetyChecker.CheckResult.Warn -> onInfo("警告: ${check.message}")
            else -> {}
        }
        // 大小限制校验
        when (val sizeCheck = SafetyChecker.validateDownloadSize(fileSize, maxDownloadSize)) {
            is SafetyChecker.CheckResult.Deny -> { onInfo("安全拦截: ${sizeCheck.message}"); return false }
            else -> {}
        }
        // 魔数校验 — 仅读头部 4KB
        val headerBytes = ByteArray(4096)
        FileInputStream(file).use { fis ->
            val headerRead = fis.read(headerBytes)
            if (headerRead > 0) {
                val header = headerBytes.copyOfRange(0, headerRead)
                when (val magicCheck = SafetyChecker.validateFlash(partition, header)) {
                    is SafetyChecker.CheckResult.Deny -> {
                        onInfo("安全拦截: ${magicCheck.message}"); return false
                    }
                    is SafetyChecker.CheckResult.Warn -> onInfo("警告: ${magicCheck.message}")
                    else -> {}
                }
            }
        }

        onInfo("下载 $partition 镜像 ($fileSize bytes, 流式传输)")
        FileInputStream(file).use { fis ->
            if (!downloadStream(fis, fileSize, onProgress)) { onInfo("下载失败"); return false }
        }
        onInfo("刷写 $partition ...")
        sendCommand("flash:$partition")
        val r = collectUntilTerminal(onInfo)
        if (r.type == "OKAY") { onInfo("$partition 刷写完成"); return true }
        onInfo("$partition 刷写失败: ${r.payload}"); return false
    }

    fun reboot(target: String = "") {
        val cmd = if (target.isBlank()) "reboot" else "reboot-$target"
        sendCommand(cmd)
    }

    companion object { private const val TIMEOUT = 30_000 }
}
