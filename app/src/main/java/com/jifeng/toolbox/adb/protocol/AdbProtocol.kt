package com.jifeng.toolbox.adb.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * ADB 线协议常量与报文结构。基于 AOSP system/core/adb/protocol.txt 实现。
 * 所有多字节字段为小端序。每个报文 = 24 字节头 + [dataLength] 载荷。
 *
 * 真实传输路径: 控制端 UsbDeviceConnection.bulkTransfer ↔ 被控端 adbd。
 */
object AdbProtocol {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_AUTH = 0x48545541
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414552
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257

    // A_AUTH arg0 子类型
    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    const val VERSION = 0x01000000          // ADB 协议版本
    const val MAX_PAYLOAD = 1024 * 1024     // 单包最大载荷 1 MiB

    /** 主机端 CNXN banner: identity::features=...\0 */
    fun hostBanner(): String =
        "host::features=cmd,stat_v2,apex,shell_v2,abb\u0000"

    fun crc32(data: ByteArray): Int {
        val c = CRC32().apply { update(data) }
        return c.value.toInt()
    }
}

/**
 * ADB 报文: 6 × u32 头 (24 字节) + 载荷。
 * 头布局: command | arg0 | arg1 | data_length | data_crc32 | magic(=command^0xFFFFFFFF)
 */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray
) {
    fun encode(): ByteArray {
        val crc = AdbProtocol.crc32(data)
        val buf = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(data.size)
        buf.putInt(crc)
        buf.putInt(command xor 0xFFFFFFFF.toInt())
        buf.put(data)
        return buf.array()
    }

    data class Header(
        val command: Int, val arg0: Int, val arg1: Int,
        val dataLength: Int, val dataCrc: Int
    ) {
        fun verify(data: ByteArray): Boolean =
            data.size == dataLength && AdbProtocol.crc32(data) == dataCrc

        companion object {
            fun decode(b: ByteArray): Header {
                require(b.size >= 24) { "ADB 头必须 24 字节, 实际 ${b.size}" }
                val v = ByteBuffer.wrap(b, 0, 24).order(ByteOrder.LITTLE_ENDIAN)
                val cmd = v.int; val a0 = v.int; val a1 = v.int
                val len = v.int; val crc = v.int; val magic = v.int
                require(magic == (cmd xor 0xFFFFFFFF.toInt())) { "ADB magic 校验失败" }
                return Header(cmd, a0, a1, len, crc)
            }
        }
    }
}
