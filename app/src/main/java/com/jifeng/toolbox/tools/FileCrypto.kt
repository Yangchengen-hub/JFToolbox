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
 * 文件级加解密: AES-256-CBC + PBKDF2 密钥派生, 流式分块处理避免 OOM。
 *
 * 输出格式 = MAGIC(4B "JFC1") || IV(16B) || ciphertext
 * 其中 IV 同时作为 PBKDF2-HMAC-SHA256 (10000 轮, 32 字节) 的 salt, 保证每次加密派生出的密钥均不同。
 *
 * 诚实声明: AES-256-CBC 是标准对称加密算法, 不存在「100% 解密任意加密」的算法,
 * 也不存在「加密后任何工具无法破解且文件正常运行」的方案。本工具仅提供合规的对称加密能力。
 */
object FileCrypto {
    private const val MAGIC = "JFC1"
    private const val IV_LEN = 16
    private const val KEY_LEN = 32
    private const val ITERATIONS = 10000
    private const val CHUNK = 8 * 1024
    private const val ALGO = "AES/CBC/PKCS5Padding"

    data class CryptoResult(
        val ok: Boolean,
        val outputFile: File?,
        val message: String,
        val durationMs: Long
    )

    /** AES-256-CBC 文件加密, 输出格式 = MAGIC(4B) || IV(16B) || ciphertext。 */
    fun encryptFile(
        input: File,
        output: File,
        password: String,
        onProgress: (Float) -> Unit = {}
    ): CryptoResult {
        val start = System.currentTimeMillis()
        return try {
            val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, iv)
            val cipher = Cipher.getInstance(ALGO).apply {
                init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            }
            FileOutputStream(output).use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
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
            CryptoResult(true, output, "加密完成", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, "加密失败: ${e.message}", System.currentTimeMillis() - start)
        }
    }

    /** AES-256-CBC 文件解密, 校验 MAGIC 头后用 password 派生密钥解密。 */
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
                    return CryptoResult(false, null, "非 JFC1 加密文件", System.currentTimeMillis() - start)
                }
                val iv = ByteArray(IV_LEN)
                if (inn.read(iv) != IV_LEN) {
                    return CryptoResult(false, null, "文件头损坏", System.currentTimeMillis() - start)
                }
                val key = deriveKey(password, iv)
                val cipher = Cipher.getInstance(ALGO).apply {
                    init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                }
                FileOutputStream(output).use { out ->
                    val total = input.length() - 4 - IV_LEN
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
                        return CryptoResult(
                            false, null,
                            "解密失败 (密码错误或文件损坏): ${e.message}",
                            System.currentTimeMillis() - start
                        )
                    }
                    if (tail.isNotEmpty()) out.write(tail)
                }
            }
            onProgress(1f)
            CryptoResult(true, output, "解密完成", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            CryptoResult(false, null, "解密失败: ${e.message}", System.currentTimeMillis() - start)
        }
    }

    /** 文件指纹 (SHA-256, 用于校验完整性)。 */
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

    /** 文件类型识别 (魔数检测, 失败时回退到扩展名)。 */
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
        // 魔数未匹配, 回退到扩展名
        val ext = file.extension.lowercase()
        return FileFormat.values().firstOrNull { it.extensions.contains(ext) } ?: FileFormat.UNKNOWN
    }

    // PBKDF2-HMAC-SHA256 派生 AES-256 密钥, salt 复用 IV (每次加密均随机)
    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
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
