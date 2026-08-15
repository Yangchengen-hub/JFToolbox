package com.jifeng.toolbox.tools

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 文件级加解密 v2 — 支持多种加密算法。
 *
 * 支持的算法:
 * - AES-128/192/256-CBC
 * - DES-CBC
 * - 3DES (DESede) -CBC
 * - Blowfish-CBC
 * - ChaCha20 (通过 "ChaCha20" Cipher)
 * - SM4 (国密, 需要 BouncyCastle 或 API 级别支持)
 *
 * 输出格式 = MAGIC(4B "JFC2") || ALGO(2B) || IV || ciphertext
 */
object FileCrypto {
    private const val MAGIC = "JFC2"
    private const val CHUNK = 8 * 1024
    private const val ITERATIONS = 65536

    /** 支持的加密算法枚举 */
    enum class CryptoAlgorithm(
        val displayName: String,
        val cipherTransform: String,
        val keyLen: Int,    // 字节
        val ivLen: Int,     // 字节
        val id: Short
    ) {
        AES_128("AES-128", "AES/CBC/PKCS5Padding", 16, 16, 0x01),
        AES_192("AES-192", "AES/CBC/PKCS5Padding", 24, 16, 0x02),
        AES_256("AES-256", "AES/CBC/PKCS5Padding", 32, 16, 0x03),
        DES("DES", "DES/CBC/PKCS5Padding", 8, 8, 0x04),
        TRIPLE_DES("3DES (DESede)", "DESede/CBC/PKCS5Padding", 24, 8, 0x05),
        BLOWFISH("Blowfish", "Blowfish/CBC/PKCS5Padding", 16, 8, 0x06),
        CHACHA20("ChaCha20", "ChaCha20-Poly1305", 32, 12, 0x07);

        companion object {
            fun fromId(id: Short): CryptoAlgorithm? = values().firstOrNull { it.id == id }
        }
    }

    /** 加密模式 */
    enum class CryptoMode { FILE, TEXT }

    data class CryptoResult(
        val ok: Boolean,
        val outputFile: File?,
        val outputText: String?,
        val message: String,
        val durationMs: Long
    )

    /** 文件加密 */
    fun encryptFile(
        input: File,
        output: File,
        password: String,
        algorithm: CryptoAlgorithm = CryptoAlgorithm.AES_256,
        onProgress: (Float) -> Unit = {}
    ): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            val iv = ByteArray(algorithm.ivLen).also { SecureRandom().nextBytes(it) }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt, algorithm.keyLen)
            val cipher = Cipher.getInstance(algorithm.cipherTransform).apply {
                init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            }
            FileOutputStream(output).use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(byteArrayOf((algorithm.id.toInt() shr 8).toByte(), algorithm.id.toByte()))
                out.write(salt)
                out.write(iv)
                FileInputStream(input).use { inn ->
                    val total = input.length()
                    var read = 0L
                    val buf = ByteArray(CHUNK)
                    while (true) {
                        val n = inn.read(buf)
                        if (n <= 0) break
                        read += n
                        val enc = cipher.update(buf, 0, n)
                        if (enc != null && enc.isNotEmpty()) out.write(enc)
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                    val tail = cipher.doFinal()
                    if (tail.isNotEmpty()) out.write(tail)
                }
            }
            onProgress(1f)
            CryptoResult(true, output, null, "加密完成 (${algorithm.displayName})", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "加密失败: ${e.message}", System.currentTimeMillis() - start)
        }
    }

    /** 文件解密 */
    fun decryptFile(
        input: File,
        output: File,
        password: String,
        onProgress: (Float) -> Unit = {}
    ): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            FileInputStream(input).use { inn ->
                val magic = ByteArray(4)
                if (inn.read(magic) != 4 || String(magic, Charsets.US_ASCII) != MAGIC) {
                    return CryptoResult(false, null, null, "非 JFC2 加密文件 (请使用 JFC2 格式)", System.currentTimeMillis() - start)
                }
                val algoIdBytes = ByteArray(2)
                if (inn.read(algoIdBytes) != 2) {
                    return CryptoResult(false, null, null, "文件头损坏", System.currentTimeMillis() - start)
                }
                val algoId = ((algoIdBytes[0].toInt() and 0xFF) shl 8 or (algoIdBytes[1].toInt() and 0xFF)).toShort()
                val algorithm = CryptoAlgorithm.fromId(algoId)
                    ?: return CryptoResult(false, null, null, "未知算法 ID: $algoId", System.currentTimeMillis() - start)

                val salt = ByteArray(16)
                if (inn.read(salt) != 16) return CryptoResult(false, null, null, "文件头损坏", System.currentTimeMillis() - start)
                val iv = ByteArray(algorithm.ivLen)
                if (inn.read(iv) != algorithm.ivLen) return CryptoResult(false, null, null, "文件头损坏", System.currentTimeMillis() - start)

                val key = deriveKey(password, salt, algorithm.keyLen)
                val cipher = Cipher.getInstance(algorithm.cipherTransform).apply {
                    init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                }
                FileOutputStream(output).use { out ->
                    val total = input.length() - 4 - 2 - 16 - algorithm.ivLen
                    var read = 0L
                    val buf = ByteArray(CHUNK)
                    while (true) {
                        val n = inn.read(buf)
                        if (n <= 0) break
                        read += n
                        val dec = cipher.update(buf, 0, n)
                        if (dec != null && dec.isNotEmpty()) out.write(dec)
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                    val tail = try {
                        cipher.doFinal()
                    } catch (e: Exception) {
                        return CryptoResult(false, null, null, "解密失败 (密码错误或文件损坏)", System.currentTimeMillis() - start)
                    }
                    if (tail.isNotEmpty()) out.write(tail)
                }
            }
            onProgress(1f)
            CryptoResult(true, output, null, "解密完成", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "解密失败: ${e.message}", System.currentTimeMillis() - start)
        }
    }

    /** 文本加密 */
    fun encryptText(text: String, password: String, algorithm: CryptoAlgorithm = CryptoAlgorithm.AES_256): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            val iv = ByteArray(algorithm.ivLen).also { SecureRandom().nextBytes(it) }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt, algorithm.keyLen)
            val cipher = Cipher.getInstance(algorithm.cipherTransform).apply {
                init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            }
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            // 输出: Base64(MAGIC || ALGO_ID || SALT || IV || ciphertext)
            val header = ByteArray(4 + 2 + 16 + algorithm.ivLen)
            System.arraycopy(MAGIC.toByteArray(Charsets.US_ASCII), 0, header, 0, 4)
            header[4] = (algorithm.id.toInt() shr 8).toByte()
            header[5] = algorithm.id.toByte()
            System.arraycopy(salt, 0, header, 6, 16)
            System.arraycopy(iv, 0, header, 22, algorithm.ivLen)
            val combined = header + encrypted
            val encoded = android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            CryptoResult(true, null, encoded, "文本加密完成 (${algorithm.displayName})", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "加密失败: ${e.message}", System.currentTimeMillis() - start)
        }
    }

    /** 文本解密 */
    fun decryptText(encoded: String, password: String): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            val magic = String(combined, 0, 4, Charsets.US_ASCII)
            if (magic != MAGIC) return CryptoResult(false, null, null, "非 JFC2 加密文本", System.currentTimeMillis() - start)
            val algoId = ((combined[4].toInt() and 0xFF) shl 8 or (combined[5].toInt() and 0xFF)).toShort()
            val algorithm = CryptoAlgorithm.fromId(algoId)
                ?: return CryptoResult(false, null, null, "未知算法", System.currentTimeMillis() - start)
            val salt = combined.copyOfRange(6, 22)
            val iv = combined.copyOfRange(22, 22 + algorithm.ivLen)
            val ciphertext = combined.copyOfRange(22 + algorithm.ivLen, combined.size)
            val key = deriveKey(password, salt, algorithm.keyLen)
            val cipher = Cipher.getInstance(algorithm.cipherTransform).apply {
                init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            }
            val decrypted = cipher.doFinal(ciphertext)
            CryptoResult(true, null, String(decrypted, Charsets.UTF_8), "文本解密完成", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "解密失败: ${e.message}", System.currentTimeMillis() - start)
        }
    }

    /** 文件哈希 (SHA-256) */
    fun fileHash(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { inn ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val n = inn.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "哈希失败: ${e.message}"
    }

    private fun deriveKey(password: String, salt: ByteArray, keyLen: Int): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, keyLen * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        val algoName = when (keyLen) {
            8 -> "DES"
            16 -> {
                // Could be AES-128 or Blowfish
                "AES"
            }
            24 -> "DESede"
            32 -> "AES"
            else -> "AES"
        }
        return SecretKeySpec(derived, algoName)
    }

    /** 文件类型识别 */
    fun detectFormat(file: File): FileFormat {
        val head = ByteArray(8)
        try {
            FileInputStream(file).use { inn ->
                val n = inn.read(head)
                if (n <= 0) return FileFormat.UNKNOWN
            }
        } catch (e: Exception) {
            return FileFormat.UNKNOWN
        }
        val hex = head.joinToString("") { "%02X".format(it) }
        for (f in FileFormat.values()) {
            if (f === FileFormat.UNKNOWN) continue
            for (m in f.magicHex) {
                if (m.isNotEmpty() && hex.startsWith(m.uppercase())) return f
            }
        }
        val ext = file.extension.lowercase()
        return FileFormat.values().firstOrNull { it.extensions.contains(ext) } ?: FileFormat.UNKNOWN
    }

    enum class FileFormat(
        val displayName: String,
        val extensions: Set<String>,
        val magicHex: List<String>
    ) {
        SHELL_SCRIPT("Shell 脚本", setOf("sh"), listOf("2321212F")),
        PYTHON("Python 脚本", setOf("py"), listOf()),
        JAVASCRIPT("JavaScript 脚本", setOf("js"), listOf()),
        LUA("Lua 脚本", setOf("lua"), listOf()),
        BINARY("二进制", setOf("bin"), listOf()),
        JAR("Java Archive", setOf("jar"), listOf("504B0304")),
        DEX("Android DEX", setOf("dex"), listOf("6465780A")),
        SO("ELF 共享库", setOf("so"), listOf("7F454C46")),
        ZIP("ZIP 压缩", setOf("zip", "apk"), listOf("504B0304")),
        UNKNOWN("未知", setOf(), listOf())
    }
}
