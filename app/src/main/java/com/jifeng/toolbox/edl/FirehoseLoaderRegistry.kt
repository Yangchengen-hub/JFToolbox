package com.jifeng.toolbox.edl

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Firehose loader (prog_firehose_xxx.elf) 注册表与自动匹配 v2。
 *
 * v2: 下载源改为公开 GitHub raw 仓库 (bkerler/Loaders), 多源容灾,
 *     不再有 <placeholder> 占位地址。
 */
object FirehoseLoaderRegistry {

    enum class Vendor { QUALCOMM, MEDIATEK, SAMSUNG, UNKNOWN }

    data class LoaderEntry(
        val filename: String,
        val chipset: String,
        val vendor: Vendor,
        val platforms: List<String>,
        val downloadUrls: List<String>,
        val localPath: String? = null,
        val sizeBytes: Long = 0
    ) {
        // 兼容旧代码: 取第一个 URL
        val downloadUrl: String? get() = downloadUrls.firstOrNull()
    }

    private const val SRC_MAIN = "https://raw.githubusercontent.com/bkerler/Loaders/master"
    private const val SRC_MISC = "https://raw.githubusercontent.com/nickcharron/EDL_Firehose_Loaders/master"
    private const val SRC_GITLAB = "https://gitlab.com/nickcharron/EDL_Firehose_Loaders/-/raw/master"

    private fun qc(vararg names: String, chipset: String, platforms: List<String>, size: Long = 1_000_000L): LoaderEntry {
        val primary = names.first()
        val urls = mutableListOf<String>()
        names.forEach { n ->
            val dir = n.removePrefix("prog_firehose_").substringBeforeLast(".")
            urls.add("$SRC_MAIN/$dir/$n")
        }
        // fallback mirrors
        val dir = primary.removePrefix("prog_firehose_").substringBeforeLast(".")
        urls.add("$SRC_MISC/$dir/$primary")
        urls.add("$SRC_GITLAB/$dir/$primary")
        return LoaderEntry(primary, chipset, Vendor.QUALCOMM, platforms, urls.distinct(), sizeBytes = size)
    }

    val REGISTRY: List<LoaderEntry> = listOf(
        // Snapdragon 8 系列
        qc("prog_firehose_sm8650.elf", chipset = "SM8650 (8 Gen 3)",
            platforms = listOf("sm8650", "8gen3", "pineapple", "pippen"), size = 1_200_000),
        qc("prog_firehose_ddr.elf", "prog_firehose_sm8550.elf", chipset = "SM8550 (8 Gen 2)",
            platforms = listOf("sm8550", "8gen2", "kalama"), size = 1_150_000),
        qc("prog_firehose_sm8475.elf", chipset = "SM8475 (8+ Gen 1)",
            platforms = listOf("sm8475", "8plusgen1", "taro"), size = 1_100_000),
        qc("prog_firehose_sm8450.elf", chipset = "SM8450 (8 Gen 1)",
            platforms = listOf("sm8450", "8gen1", "taro"), size = 1_100_000),
        qc("prog_firehose_sm8350.elf", chipset = "SM8350 (888)",
            platforms = listOf("sm8350", "888", "lahaina"), size = 1_050_000),
        qc("prog_firehose_sm8250.elf", chipset = "SM8250 (865)",
            platforms = listOf("sm8250", "865", "kona"), size = 1_000_000),
        qc("prog_firehose_sm8150.elf", chipset = "SM8150 (855)",
            platforms = listOf("sm8150", "855", "msmnile"), size = 980_000),
        qc("prog_firehose_sdm845.elf", chipset = "SDM845 (845)",
            platforms = listOf("sdm845", "845"), size = 950_000),
        qc("prog_firehose_msm8998.elf", chipset = "MSM8998 (835)",
            platforms = listOf("msm8998", "835"), size = 920_000),
        qc("prog_firehose_msm8996.elf", chipset = "MSM8996 (820)",
            platforms = listOf("msm8996", "820"), size = 880_000),
        // Snapdragon 7 系列
        qc("prog_firehose_sm7475.elf", chipset = "SM7475 (7+ Gen 2)",
            platforms = listOf("sm7475", "7gen2", "divar"), size = 980_000),
        qc("prog_firehose_sm7325.elf", chipset = "SM7325 (778G)",
            platforms = listOf("sm7325", "778g", "yupik", "cupid"), size = 970_000),
        qc("prog_firehose_sm7250.elf", chipset = "SM7250 (765G)",
            platforms = listOf("sm7250", "765g", "lito"), size = 960_000),
        qc("prog_firehose_sm7150.elf", chipset = "SM7150 (730G)",
            platforms = listOf("sm7150", "730g"), size = 950_000),
        // Snapdragon 6 系列
        qc("prog_firehose_sm6375.elf", chipset = "SM6375 (695)",
            platforms = listOf("sm6375", "695", "holi"), size = 900_000),
        qc("prog_firehose_sm6225.elf", chipset = "SM6225 (680/685)",
            platforms = listOf("sm6225", "680", "685", "bengal"), size = 880_000),
        qc("prog_firehose_sm6125.elf", chipset = "SM6125 (665)",
            platforms = listOf("sm6125", "665", "trinket"), size = 920_000),
        qc("prog_firehose_sm6150.elf", chipset = "SM6150 (675)",
            platforms = listOf("sm6150", "675", "sdm675", "talos"), size = 940_000),
        qc("prog_firehose_sm6115.elf", chipset = "SM6115 (662/460)",
            platforms = listOf("sm6115", "662", "460", "bengal"), size = 900_000),
        // Snapdragon 4 系列
        qc("prog_firehose_sdm439.elf", chipset = "SDM439 (439)",
            platforms = listOf("sdm439", "439", "msm8937"), size = 800_000),
        qc("prog_firehose_msm8937.elf", chipset = "MSM8937 (430/435/625)",
            platforms = listOf("msm8937", "430", "435", "625"), size = 780_000),
        qc("prog_firehose_msm8916.elf", chipset = "MSM8916 (410)",
            platforms = listOf("msm8916", "410"), size = 720_000),
        // MTK (DA — Download Agent, 非 firehose, 仅元数据登记)
        LoaderEntry(
            filename = "MTK_AllInOne_DA.bin",
            chipset = "MTK 通用 DA",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mtk", "mt6989", "mt6985", "mt6877", "mt6893", "mt6889", "mt6833"),
            downloadUrls = listOf(
                "https://raw.githubusercontent.com/bkerler/Loaders/master/mtk/MTK_AllInOne_DA.bin",
                "$SRC_GITLAB/mtk/MTK_AllInOne_DA.bin"
            ),
            sizeBytes = 2_000_000
        )
    )

    fun match(platform: String): LoaderEntry? {
        val needle = normalize(platform)
        if (needle.isBlank()) return null
        REGISTRY.firstOrNull { e ->
            e.platforms.any { normalize(it) == needle } ||
                normalize(e.chipset) == needle ||
                normalize(e.filename).contains(needle)
        }?.let { return it }
        return REGISTRY.firstOrNull { e ->
            e.platforms.any {
                val p = normalize(it)
                p.length >= 3 && (needle.contains(p) || p.contains(needle))
            }
        }
    }

    suspend fun detectPlatform(serial: String): String? = withContext(Dispatchers.IO) {
        val primary = AdbManager.shell(serial, "getprop ro.board.platform")
            ?.trim()?.lowercase()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
        if (!primary.isNullOrBlank()) return@withContext primary
        val hw = AdbManager.shell(serial, "getprop ro.hardware")
            ?.trim()?.lowercase()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
        if (!hw.isNullOrBlank()) return@withContext hw
        val mfr = AdbManager.shell(serial, "getprop ro.soc.manufacturer")?.trim().orEmpty()
        val model = AdbManager.shell(serial, "getprop ro.soc.model")?.trim().orEmpty()
        if (model.isNotBlank() && !model.equals("unknown", true)) "$mfr $model".trim() else null
    }

    /**
     * 多源下载: 依次尝试 [LoaderEntry.downloadUrls], 第一个成功即返回。
     */
    suspend fun download(
        entry: LoaderEntry,
        cacheDir: File,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val target = File(cacheDir, entry.filename)
        if (target.exists() && target.length() > 0) {
            onProgress(1f)
            return@withContext target
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        for ((idx, url) in entry.downloadUrls.withIndex()) {
            val tmp = File(cacheDir, "${entry.filename}.part$idx")
            try {
                Logger.i("FirehoseRegistry", "尝试源 ${idx + 1}/${entry.downloadUrls.size}: $url")
                val req = Request.Builder().url(url)
                    .header("User-Agent", "JFToolbox/2.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Logger.w("FirehoseRegistry", "HTTP ${resp.code} ${resp.message}")
                        return@use
                    }
                    val body = resp.body ?: return@use
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (tmp.exists() && tmp.length() > 1024) {
                    if (target.exists()) target.delete()
                    if (tmp.renameTo(target)) {
                        Logger.i("FirehoseRegistry", "下载完成: ${target.name} (${target.length()} bytes) from 源${idx + 1}")
                        return@withContext target
                    } else {
                        tmp.copyTo(target, overwrite = true); tmp.delete()
                        Logger.i("FirehoseRegistry", "下载完成 (copy 兜底): ${target.name}")
                        return@withContext target
                    }
                }
            } catch (e: Exception) {
                Logger.w("FirehoseRegistry", "源${idx + 1}失败: ${e.message}")
            } finally {
                tmp.delete()
            }
        }
        Logger.e("FirehoseRegistry", "所有下载源均失败: ${entry.filename}")
        null
    }

    fun listLocal(cacheDir: File): List<File> {
        if (!cacheDir.exists()) return emptyList()
        return cacheDir.listFiles()?.filter {
            it.isFile && it.name.matches(Regex("prog_firehose_.+\\.(elf|mbn|bin)", RegexOption.IGNORE_CASE))
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    private fun normalize(s: String): String =
        s.lowercase().trim().replace(Regex("\\s+"), "")
}
