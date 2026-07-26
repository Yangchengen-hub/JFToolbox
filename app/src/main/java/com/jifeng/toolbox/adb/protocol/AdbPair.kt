package com.jifeng.toolbox.adb.protocol

import com.jifeng.toolbox.core.Logger
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * ADB 无线调试配对客户端 (Android 11+)。
 *
 * 实现 SPAKE2-EE 简化协议 (X25519 + HKDF + AES-256-GCM):
 *
 * 1. 客户端生成 X25519 临时密钥对 (C_priv, C_pub)
 * 2. 客户端生成 16 字节 peer_id (随机)
 * 3. 客户端发送: C_pub (32B) || peer_id (16B) = 48 字节
 * 4. 服务端返回: S_pub (32B) || peer_id (16B) = 48 字节
 * 5. 双方计算 shared = X25519(my_priv, peer_pub)
 * 6. 双方派生 key = HKDF-SHA256(shared, salt=pairingCode, info="adb_pair_v1", L=32)
 * 7. 客户端发送加密包: AES-256-GCM(key, nonce=0, AAD=peer_id, plain=C_pub||peer_id)
 * 8. 服务端返回加密包: AES-256-GCM(key, nonce=1, AAD=peer_id, plain=S_pub||peer_id)
 * 9. 客户端验证: 解密后比较 S_pub 与之前明文收到的一致
 *
 * 配对成功后, 服务端会把客户端 X25519 公钥写入 ~/.android/adbkey.pub (无线调试已激活端口直连)。
 *
 * 参考: AOSP system/core/adb/pairing_auth/pairing_auth.cpp + pairing_server.cpp
 */
class AdbPair(
    private val host: String,
    private val pairPort: Int,        // 配对端口 (设备 "无线调试" → "与设备配对" 显示的端口)
    private val pairingCode: String   // 6 位配对码
) {

    private val random = SecureRandom()

    /** 配对结果。 */
    data class Result(
        val success: Boolean,
        val peerPublicKey: ByteArray? = null,  // 服务端 X25519 公钥 (32B, 可作 ADB key 保存)
        val message: String
    )

    /**
     * 执行配对流程。
     *
     * @return [Result] 配对结果
     */
    fun pair(): Result {
        if (pairingCode.length != 6 || !pairingCode.all { it.isDigit() }) {
            return Result(false, message = "配对码必须是 6 位数字")
        }

        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, pairPort), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            Logger.i(TAG, "已连接配对端口 $host:$pairPort")

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. 生成 X25519 密钥对
            val keyPair = generateX25519KeyPair()
            val ourPriv = keyPair.private as X25519PrivateKeyParameters
            val ourPub = keyPair.public as X25519PublicKeyParameters
            val ourPubBytes = ourPub.encoded
            val ourPeerId = ByteArray(16).also { random.nextBytes(it) }

            // 2. 发送 our_pub || our_peer_id (48B)
            val outPacket = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
                .put(ourPubBytes)
                .put(ourPeerId)
                .array()
            output.write(outPacket)
            output.flush()
            Logger.d(TAG, "发送客户端 pubkey+peerId (${outPacket.size}B)")

            // 3. 接收 server_pub || server_peer_id (48B)
            val inPacket = readExact(input, 48)
            val serverPubBytes = inPacket.copyOfRange(0, 32)
            val serverPeerId = inPacket.copyOfRange(32, 48)
            Logger.d(TAG, "收到服务端 pubkey+peerId")

            // 4. 计算 X25519 共享密钥
            val serverPub = X25519PublicKeyParameters(serverPubBytes, 0)
            val sharedSecret = ByteArray(32)
            val privBytes = ourPriv.encoded
            val agreement = X25519Agreement()
            agreement.init(X25519PrivateKeyParameters(privBytes, 0))
            agreement.calculateAgreement(serverPub, sharedSecret, 0)
            Logger.d(TAG, "X25519 共享密钥已建立")

            // 5. HKDF-SHA256 派生 32B AES 密钥
            val aesKey = hkdfSha256(
                secret = sharedSecret,
                salt = pairingCode.toByteArray(Charsets.US_ASCII),
                info = HKDF_INFO.toByteArray(Charsets.US_ASCII),
                length = 32
            )

            // 6. 客户端 → 服务端: AES-256-GCM(key, nonce=0, AAD=serverPeerId, plain=ourPub||ourPeerId)
            //    密文 = 48B 加密 + 16B tag = 64B
            val clientNonce = longToNonce(0L)
            val clientPlain = ByteBuffer.allocate(48)
                .put(ourPubBytes)
                .put(ourPeerId)
                .array()
            val clientCipher = aesGcmEncrypt(
                key = aesKey,
                nonce = clientNonce,
                aad = serverPeerId,
                plain = clientPlain
            )
            output.write(clientCipher)
            output.flush()
            Logger.d(TAG, "发送加密包 (${clientCipher.size}B)")

            // 7. 服务端 → 客户端: AES-256-GCM(key, nonce=1, AAD=ourPeerId, plain=serverPub||serverPeerId)
            val serverNonce = longToNonce(1L)
            val serverCipher = readExact(input, 64)
            val serverPlain = try {
                aesGcmDecrypt(
                    key = aesKey,
                    nonce = serverNonce,
                    aad = ourPeerId,
                    cipher = serverCipher
                )
            } catch (e: Exception) {
                return Result(false, message = "解密服务端响应失败 (配对码错误?): ${e.message}")
            }

            // 8. 验证: 解密出的 serverPub 应等于之前明文收到的
            val decryptedServerPub = serverPlain.copyOfRange(0, 32)
            val decryptedServerPeerId = serverPlain.copyOfRange(32, 48)
            if (!decryptedServerPub.contentEquals(serverPubBytes)) {
                return Result(false, message = "公钥校验失败: 解密结果与明文不匹配")
            }
            if (!decryptedServerPeerId.contentEquals(serverPeerId)) {
                return Result(false, message = "peer_id 校验失败")
            }

            Logger.i(TAG, "✅ 配对成功 server_pub=${serverPubBytes.size}B")
            Result(
                success = true,
                peerPublicKey = serverPubBytes,
                message = "配对成功, 可直接连接无线调试 active 端口"
            )
        } catch (e: Exception) {
            Logger.e(TAG, "配对失败: ${e.message}")
            Result(false, message = "配对失败: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // ---------- X25519 ----------

    private fun generateX25519KeyPair(): org.bouncycastle.crypto.AsymmetricCipherKeyPair {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))
        return gen.generateKeyPair()
    }

    // ---------- HKDF-SHA256 ----------

    private fun hkdfSha256(
        secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int
    ): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(secret, salt, info))
        val out = ByteArray(length)
        gen.generateBytes(out, 0, length)
        return out
    }

    // ---------- AES-256-GCM ----------

    private fun aesGcmEncrypt(
        key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray
    ): ByteArray {
        val cipher = GCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plain.size))
        var off = cipher.processBytes(plain, 0, plain.size, out, 0)
        off += cipher.doFinal(out, off)
        return out.copyOfRange(0, off)
    }

    private fun aesGcmDecrypt(
        key: ByteArray, nonce: ByteArray, aad: ByteArray, cipher: ByteArray
    ): ByteArray {
        val c = GCMBlockCipher(AESEngine())
        c.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(c.getOutputSize(cipher.size))
        var off = c.processBytes(cipher, 0, cipher.size, out, 0)
        off += c.doFinal(out, off)
        return out.copyOfRange(0, off)
    }

    /** 12 字节 GCM nonce, 由 64-bit counter 填零到 96-bit。 */
    private fun longToNonce(counter: Long): ByteArray =
        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(counter)
            .putInt(0)
            .array()

    private fun readExact(ins: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(out, off, n - off)
            if (r < 0) throw IOException("配对端口 EOF (offset=$off/$n)")
            off += r
        }
        return out
    }

    companion object {
        private const val TAG = "AdbPair"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val HKDF_INFO = "adb pair v1"
    }
}
