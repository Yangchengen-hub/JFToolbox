package com.jifeng.toolbox.adb.protocol

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * ADB 授权所需的 RSA-2048 密钥对管理。
 *
 * 流程:
 * 1. 控制端首次连接, 被控端 adbd 返回 AUTH_TOKEN (随机 20 字节)
 * 2. 控制端用本地私钥对 token 做 PKCS#1 v1.5 SHA-1 签名, 回传 AUTH_SIGNATURE
 * 3. 若被控端无此公钥记录, 回传 AUTH_TOKEN; 控制端回传 AUTH_RSAPUBLICKEY (android_pubkey 格式)
 * 4. 被控端弹"允许 USB 调试"对话框, 用户同意后回 CNXN, 公钥写入 ~/.android/adbkey.pub
 *
 * 公钥格式遵循 AOSP android_pubkey (system/core/libcrypto_utils/android_pubkey.c)。
 */
class AdbKeyManager(context: Context) {

    private val keyDir = File(context.filesDir, "adb").apply { mkdirs() }
    private val privFile = File(keyDir, "adb_key")
    private val pubFile = File(keyDir, "adb_pubkey")

    val keyPair: KeyPair

    init {
        keyPair = loadOrGenerate()
    }

    private fun loadOrGenerate(): KeyPair {
        privFile.takeIf { it.exists() }?.let { f ->
            try {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(f.readText(), Base64.NO_WRAP)))
                val pub = pubFile.takeIf { it.exists() }?.let {
                    kf.generatePublic(X509EncodedKeySpec(Base64.decode(it.readText(), Base64.NO_WRAP)))
                }
                return KeyPair(pub ?: keyPairFromPrivate(priv), priv)
            } catch (_: Exception) { /* fallthrough, 重新生成 */ }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        privFile.writeText(Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
        pubFile.writeText(Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
        return kp
    }

    private fun keyPairFromPrivate(priv: java.security.PrivateKey) =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(priv.encoded)) // 简化回退

    /** 对 AUTH_TOKEN 做 PKCS#1 v1.5 SHA-1 with RSA 签名 (ADB 历史约定)。 */
    fun signToken(token: ByteArray): ByteArray {
        val s = Signature.getInstance("SHA1withRSA")
        s.initSign(keyPair.private)
        s.update(token)
        return s.sign()
    }

    /**
     * 输出 android_pubkey 格式公钥 (base64), 用于 AUTH_RSAPUBLICKEY 报文。
 * 结构 (AOSP android_pubkey.c):
     *   uint32 mod_size (LE, 字, 2048-bit=64)
     *   uint32 n0inv   (LE, -n mod 2^32 的反)
     *   uint32 rr[64]  (LE, R^2 mod n, R=2^2048)
     *   uint32 exponent(LE, 65537)
     *   uint8  modulus[256] (BE)
     */
    fun adbPublicKeyBase64(): String {
        val pub = keyPair.public as RSAPublicKey
        val n = pub.modulus
        val e = pub.publicExponent
        val out = ByteArrayOutputStream()

        out.write(le32(MOD_SIZE_WORDS))

        val two32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = n.mod(two32).modInverse(two32).negate().mod(two32)
        out.write(le32(n0inv.toInt()))

        val r = BigInteger.ONE.shiftLeft(2048)
        val rr = r.multiply(r).mod(n)
        out.write(toLittleEndian(rr, MOD_SIZE_BYTES))

        out.write(le32(e.toInt()))

        val modBe = stripLeadingZero(n.toByteArray())
        require(modBe.size <= MOD_SIZE_BYTES) { "modulus 过长" }
        val modPadded = ByteArray(MOD_SIZE_BYTES)
        System.arraycopy(modBe, 0, modPadded, MOD_SIZE_BYTES - modBe.size, modBe.size)
        out.write(modPadded)

        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun le32(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun toLittleEndian(v: BigInteger, len: Int): ByteArray {
        val be = stripLeadingZero(v.toByteArray())
        val out = ByteArray(len)
        System.arraycopy(be, 0, out, len - be.size, be.size)
        var i = 0; var j = len - 1
        while (i < j) { val t = out[i]; out[i] = out[j]; out[j] = t; i++; j-- }
        return out
    }

    private fun stripLeadingZero(b: ByteArray): ByteArray =
        if (b.isNotEmpty() && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b

    companion object {
        private const val MOD_SIZE_WORDS = 64   // 2048/32
        private const val MOD_SIZE_BYTES = 256  // 2048/8
    }
}
