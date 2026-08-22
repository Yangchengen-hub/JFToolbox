package com.jifeng.toolbox.tools

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 文件级加解密 v3 — 强化安全。
 *
 * 主要升级:
 *  - 默认算法改为 AES-256-GCM (认证加密, 防篡改)
 *  - PBKDF2 迭代次数从 65536 提升到 200000
 *  - 新增 SHA-256/SHA-512/MD5/CRC32 多种哈希
 *  - 文件头嵌入版本和算法, 向前兼容 JFC2
 *  - 新增文件夹批量加解密
 *
 * 输出格式 (JFC3):
 *  MAGIC(4) = "JFC3"
 *  VER(2)   = 0x0003
 *  ALGO_ID(2)
 *  KDF_ITER(4, big-endian)
 *  SALT(16)
 *  IV(12 or 16)
 *  CIPHERTEXT+TAG
 */
object FileCrypto {
    private const val MAGIC_V2 = "JFC2"
    private const val MAGIC_V3 = "JFC3"
    private const val CHUNK = 64 * 1024
    private const val ITERATIONS_V2 = 65536
    private const val ITERATIONS_V3 = 200000
    private const val GCM_TAG_BITS = 128

    enum class CryptoAlgorithm(
        val displayName: String,
        val cipherTransform: String,
        val keyLen: Int,
        val ivLen: Int,
        val id: Short,
        val isGcm: Boolean = false
    ) {
        AES_256_GCM("AES-256-GCM (推荐)", "AES/GCM/NoPadding", 32, 12, 0x10, isGcm = true),
        AES_256("AES-256-CBC", "AES/CBC/PKCS5Padding", 32, 16, 0x03),
        AES_192("AES-192-CBC", "AES/CBC/PKCS5Padding", 24, 16, 0x02),
        AES_128("AES-128-CBC", "AES/CBC/PKCS5Padding", 16, 16, 0x01),
        CHACHA20("ChaCha20-Poly1305", "ChaCha20-Poly1305", 32, 12, 0x07, isGcm = true),
        BLOWFISH("Blowfish-CBC", "Blowfish/CBC/PKCS5Padding", 16, 8, 0x06);

        companion object {
            fun fromId(id: Short): CryptoAlgorithm? = values().firstOrNull { it.id == id }
        }
    }

    data class CryptoResult(
        val ok: Boolean,
        val outputFile: File? = null,
        val outputText: String? = null,
        val message: String,
        val durationMs: Long = 0,
        val hashBefore: String = "",
        val hashAfter: String = ""
    )

    // ---------- 文件加解密 ----------

    fun encryptFile(
        input: File, output: File, password: String,
        algorithm: CryptoAlgorithm = CryptoAlgorithm.AES_256_GCM,
        iterations: Int = ITERATIONS_V3,
        onProgress: (Float) -> Unit = {}
    ): CryptoResult {
        val start = System.currentTimeMillis()
        val hashBefore = fileHash(input, "SHA-256")
        return try {
            val iv = ByteArray(algorithm.ivLen).also { SecureRandom().nextBytes(it) }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt, algorithm.keyLen, iterations, algorithm)
            val cipher = Cipher.getInstance(algorithm.cipherTransform).apply {
                if (algorithm.isGcm) init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                else init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            }
            FileOutputStream(output).use { out ->
                out.write(MAGIC_V3.toByteArray())
                out.write(byteArrayOf(0x00, 0x03))
                out.write(byteArrayOf((algorithm.id.toInt() shr 8).toByte(), algorithm.id.toByte()))
                out.write(byteArrayOf(
                    (iterations shr 24).toByte(), (iterations shr 16).toByte(),
                    (iterations shr 8).toByte(), iterations.toByte()
                ))
                out.write(salt); out.write(iv)
                FileInputStream(input).use { inn ->
                    val total = input.length(); var read = 0L; val buf = ByteArray(CHUNK)
                    while (true) {
                        val n = inn.read(buf); if (n <= 0) break
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
            val hashAfter = fileHash(output, "SHA-256")
            CryptoResult(true, output, null, "加密完成 (${algorithm.displayName})",
                System.currentTimeMillis() - start, hashBefore, hashAfter)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "加密失败: ${e.message}",
                System.currentTimeMillis() - start, hashBefore, "")
        }
    }

    fun decryptFile(
        input: File, output: File, password: String,
        onProgress: (Float) -> Unit = {}
    ): CryptoResult {
        val start = System.currentTimeMillis()
        val hashBefore = fileHash(input, "SHA-256")
        return try {
            FileInputStream(input).use { inn ->
                val magic = ByteArray(4)
                if (inn.read(magic) != 4) return CryptoResult(false, null, null, "文件过短",
                    System.currentTimeMillis() - start, hashBefore, "")
                val magicStr = String(magic, Charsets.US_ASCII)
                val (algo, iterations, ivLen) = when (magicStr) {
                    MAGIC_V3 -> {
                        val ver = ByteArray(2).also { inn.read(it) }
                        val algoIdBytes = ByteArray(2).also { inn.read(it) }
                        val iterBytes = ByteArray(4).also { inn.read(it) }
                        val algoId = (((algoIdBytes[0].toInt() and 0xFF) shl 8) or
                                (algoIdBytes[1].toInt() and 0xFF)).toShort()
                        val iter = ((iterBytes[0].toInt() and 0xFF) shl 24) or
                                ((iterBytes[1].toInt() and 0xFF) shl 16) or
                                ((iterBytes[2].toInt() and 0xFF) shl 8) or
                                (iterBytes[3].toInt() and 0xFF)
                        val a = CryptoAlgorithm.fromId(algoId)
                            ?: return CryptoResult(false, null, null, "未知算法 ID: $algoId",
                                System.currentTimeMillis() - start, hashBefore, "")
                        Triple(a, iter, a.ivLen)
                    }
                    MAGIC_V2 -> {
                        val algoIdBytes = ByteArray(2).also { inn.read(it) }
                        val algoId = (((algoIdBytes[0].toInt() and 0xFF) shl 8) or
                                (algoIdBytes[1].toInt() and 0xFF)).toShort()
                        val a = CryptoAlgorithm.fromId(algoId)
                            ?: return CryptoResult(false, null, null, "未知算法 (V2): $algoId",
                                System.currentTimeMillis() - start, hashBefore, "")
                        Triple(a, ITERATIONS_V2, a.ivLen)
                    }
                    else -> return CryptoResult(false, null, null, "非 JFC 加密文件",
                        System.currentTimeMillis() - start, hashBefore, "")
                }

                val salt = ByteArray(16).also { inn.read(it) }
                val iv = ByteArray(ivLen).also { inn.read(it) }
                val key = deriveKey(password, salt, algo.keyLen, iterations, algo)
                val cipher = Cipher.getInstance(algo.cipherTransform).apply {
                    if (algo.isGcm) init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    else init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                }

                FileOutputStream(output).use { out ->
                    val total = input.length() - 4 - 2 - 2 - (if (magicStr == MAGIC_V3) 4 else 0) - 16 - ivLen
                    var read = 0L; val buf = ByteArray(CHUNK)
                    while (true) {
                        val n = inn.read(buf); if (n <= 0) break
                        read += n
                        val dec = cipher.update(buf, 0, n)
                        if (dec != null && dec.isNotEmpty()) out.write(dec)
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                    val tail = try { cipher.doFinal() }
                    catch (e: Exception) {
                        return CryptoResult(false, null, null, "解密失败 (密码错误或文件损坏)",
                            System.currentTimeMillis() - start, hashBefore, "")
                    }
                    if (tail.isNotEmpty()) out.write(tail)
                }
            }
            onProgress(1f)
            val hashAfter = fileHash(output, "SHA-256")
            CryptoResult(true, output, null, "解密完成",
                System.currentTimeMillis() - start, hashBefore, hashAfter)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "解密失败: ${e.message}",
                System.currentTimeMillis() - start, hashBefore, "")
        }
    }

    // ---------- 文本加解密 ----------

    fun encryptText(text: String, password: String,
                    algorithm: CryptoAlgorithm = CryptoAlgorithm.AES_256_GCM): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            val iv = ByteArray(algorithm.ivLen).also { SecureRandom().nextBytes(it) }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt, algorithm.keyLen, ITERATIONS_V3, algorithm)
            val cipher = Cipher.getInstance(algorithm.cipherTransform).apply {
                if (algorithm.isGcm) init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                else init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            }
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            val headerLen = 4 + 2 + 2 + 4 + 16 + algorithm.ivLen
            val header = ByteArray(headerLen)
            var p = 0
            MAGIC_V3.toByteArray().copyInto(header, p); p += 4
            header[p++] = 0x00; header[p++] = 0x03
            header[p++] = (algorithm.id.toInt() shr 8).toByte()
            header[p++] = algorithm.id.toByte()
            header[p++] = (ITERATIONS_V3 shr 24).toByte()
            header[p++] = (ITERATIONS_V3 shr 16).toByte()
            header[p++] = (ITERATIONS_V3 shr 8).toByte()
            header[p++] = ITERATIONS_V3.toByte()
            salt.copyInto(header, p); p += 16
            iv.copyInto(header, p)
            val combined = header + encrypted
            val encoded = android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            CryptoResult(true, null, encoded, "文本加密完成 (${algorithm.displayName})",
                System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "加密失败: ${e.message}",
                System.currentTimeMillis() - start)
        }
    }

    fun decryptText(encoded: String, password: String): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            if (combined.size < 24) return CryptoResult(false, null, null, "文本过短",
                System.currentTimeMillis() - start)
            val magic = String(combined, 0, 4, Charsets.US_ASCII)
            if (magic != MAGIC_V3 && magic != MAGIC_V2)
                return CryptoResult(false, null, null, "非 JFC 加密文本",
                    System.currentTimeMillis() - start)
            var p = 4
            if (magic == MAGIC_V3) p += 2  // skip version
            val algoId = (((combined[p].toInt() and 0xFF) shl 8) or
                    (combined[p+1].toInt() and 0xFF)).toShort()
            p += 2
            val iterations = if (magic == MAGIC_V3) {
                val v = ((combined[p].toInt() and 0xFF) shl 24) or
                        ((combined[p+1].toInt() and 0xFF) shl 16) or
                        ((combined[p+2].toInt() and 0xFF) shl 8) or
                        (combined[p+3].toInt() and 0xFF)
                p += 4; v
            } else ITERATIONS_V2
            val algo = CryptoAlgorithm.fromId(algoId)
                ?: return CryptoResult(false, null, null, "未知算法",
                    System.currentTimeMillis() - start)
            val salt = combined.copyOfRange(p, p + 16); p += 16
            val iv = combined.copyOfRange(p, p + algo.ivLen); p += algo.ivLen
            val ct = combined.copyOfRange(p, combined.size)
            val key = deriveKey(password, salt, algo.keyLen, iterations, algo)
            val cipher = Cipher.getInstance(algo.cipherTransform).apply {
                if (algo.isGcm) init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                else init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            }
            val dec = cipher.doFinal(ct)
            CryptoResult(true, null, String(dec, Charsets.UTF_8), "文本解密完成",
                System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, null, "解密失败: ${e.message}",
                System.currentTimeMillis() - start)
        }
    }

    // ---------- 哈希 ----------

    fun fileHash(file: File, algorithm: String = "SHA-256"): String = try {
        val md = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { inn ->
            val buf = ByteArray(CHUNK)
            while (true) { val n = inn.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "hash-failed: ${e.message}" }

    fun textHash(text: String, algorithm: String = "SHA-256"): String = try {
        val md = MessageDigest.getInstance(algorithm)
        md.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "hash-failed" }

    fun crc32(file: File): Long = try {
        val crc = java.util.zip.CRC32()
        FileInputStream(file).use { inn ->
            val buf = ByteArray(CHUNK)
            while (true) { val n = inn.read(buf); if (n <= 0) break; crc.update(buf, 0, n) }
        }
        crc.value
    } catch (_: Exception) { 0L }

    // ---------- 批量文件夹 ----------

    fun encryptFolder(folder: File, password: String,
                      algorithm: CryptoAlgorithm = CryptoAlgorithm.AES_256_GCM,
                      onFile: (File) -> Unit = {}): List<CryptoResult> {
        val results = mutableListOf<CryptoResult>()
        folder.walkTopDown().filter { it.isFile }.forEach { f ->
            val out = File(f.absolutePath + ".jfc")
            onFile(f)
            results.add(encryptFile(f, out, password, algorithm))
        }
        return results
    }

    // ---------- 内部 ----------

    private fun deriveKey(password: String, salt: ByteArray, keyLen: Int,
                          iterations: Int, algo: CryptoAlgorithm): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLen * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        val algoName = when {
            algo.cipherTransform.startsWith("ChaCha") -> "ChaCha20"
            algo.cipherTransform.startsWith("Blowfish") -> "Blowfish"
            else -> "AES"
        }
        return SecretKeySpec(derived, algoName)
    }
}
