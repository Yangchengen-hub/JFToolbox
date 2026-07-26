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
 * Firehose loader (prog_firehose_xxx.elf) 注册表与自动匹配。
 *
 * 9008 模式下, 设备需要先接收一个与芯片平台匹配的 firehose programmer 才能继续
 * 接收 rawprogram.xml / patch0.xml 等烧写指令。本对象维护主流芯片平台的 loader
 * 元数据库, 提供:
 *   - [match]            按平台字符串 (sm8650 / 8gen3 / mt6989 ...) 匹配
 *   - [detectPlatform]   通过 ADB getprop 自动探测被控端平台
 *   - [download]         从远程仓库 (GitHub release) 下载 loader 到本地缓存
 *   - [listLocal]        列出本地已缓存的 loader 文件
 *
 * 注意: downloadUrl 当前为占位地址, 实际部署时替换为真实仓库地址
 * (例如组织内部 LOSP / firehose-loaders 私有 release)。
 */
object FirehoseLoaderRegistry {

    /** 厂商分类。 */
    enum class Vendor { QUALCOMM, MEDIATEK, SAMSUNG, UNKNOWN }

    /**
     * 单个 loader 元数据。
     *
     * @param filename    约定文件名, 如 prog_firehose_sm8650.elf
     * @param chipset     显示用芯片代号, 如 SM8650
     * @param vendor      厂商
     * @param platforms   可匹配的别名列表 (ro.board.platform / 8gen3 等代号)
     * @param downloadUrl 远程下载地址 (GitHub release 占位, 实际部署时替换为真实仓库地址)
     * @param localPath   本地缓存路径 (下载后填充)
     * @param sizeBytes   预期字节数 (0 表示未知)
     */
    data class LoaderEntry(
        val filename: String,
        val chipset: String,
        val vendor: Vendor,
        val platforms: List<String>,
        val downloadUrl: String?,
        val localPath: String? = null,
        val sizeBytes: Long = 0
    )

    /**
     * 已知 firehose loader 数据库 (内置主流芯片)。
     *
     * 来源: GitHub 上的 LOSP / firehose-loader / QFIL 等公开仓库汇总的主流平台。
     * downloadUrl 当前为占位地址 `https://github.com/<placeholder>/firehose-loaders/...`,
     * 实际部署时替换为真实仓库地址。
     */
    val REGISTRY: List<LoaderEntry> = listOf(
        // ---------- Qualcomm Snapdragon 8 系列 ----------
        LoaderEntry(
            filename = "prog_firehose_sm8650.elf",
            chipset = "SM8650",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm8650", "8gen3", "pineapple", "pippen"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm8650.elf",
            sizeBytes = 1_200_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm8550.elf",
            chipset = "SM8550",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm8550", "8gen2", "kalama"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm8550.elf",
            sizeBytes = 1_150_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm8450.elf",
            chipset = "SM8450",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm8450", "8gen1", "taro"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm8450.elf",
            sizeBytes = 1_100_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm8350.elf",
            chipset = "SM8350",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm8350", "888", "lahaina"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm8350.elf",
            sizeBytes = 1_050_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm8250.elf",
            chipset = "SM8250",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm8250", "865", "kona"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm8250.elf",
            sizeBytes = 1_000_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm8150.elf",
            chipset = "SM8150",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm8150", "855", "msmnile"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm8150.elf",
            sizeBytes = 980_000L
        ),
        // ---------- Qualcomm Snapdragon 6/7 系列 ----------
        LoaderEntry(
            filename = "prog_firehose_sm6115.elf",
            chipset = "SM6115",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm6115", "bengal"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm6115.elf",
            sizeBytes = 900_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm6125.elf",
            chipset = "SM6125",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm6125", "trinket"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm6125.elf",
            sizeBytes = 920_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm6150.elf",
            chipset = "SM6150",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm6150", "sdm6150"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm6150.elf",
            sizeBytes = 940_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm7250.elf",
            chipset = "SM7250",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm7250", "765g", "lito"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm7250.elf",
            sizeBytes = 960_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_sm7325.elf",
            chipset = "SM7325",
            vendor = Vendor.QUALCOMM,
            platforms = listOf("sm7325", "778g", "yupik"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_sm7325.elf",
            sizeBytes = 970_000L
        ),
        // ---------- MediaTek Dimensity / Helio ----------
        // 注: MTK 平台实际使用 DA (Download Agent) 而非 firehose, 这里仅作元数据登记,
        //     救砖流程仍需走 MTK scatter + DA 协议 (本仓库 Phase 7 实现)
        LoaderEntry(
            filename = "prog_firehose_mt6989.elf",
            chipset = "MT6989",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mt6989", "d9300", "dimensity9300"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_mt6989.elf",
            sizeBytes = 800_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_mt6985.elf",
            chipset = "MT6985",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mt6985", "d9200", "dimensity9200"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_mt6985.elf",
            sizeBytes = 790_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_mt6878.elf",
            chipset = "MT6878",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mt6878", "d905", "dimensity905"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_mt6878.elf",
            sizeBytes = 720_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_mt6893.elf",
            chipset = "MT6893",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mt6893", "d1200", "dimensity1200"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_mt6893.elf",
            sizeBytes = 750_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_mt6889.elf",
            chipset = "MT6889",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mt6889", "d1100", "dimensity1100"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_mt6889.elf",
            sizeBytes = 740_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_mt6833.elf",
            chipset = "MT6833",
            vendor = Vendor.MEDIATEK,
            platforms = listOf("mt6833", "d700", "dimensity700"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_mt6833.elf",
            sizeBytes = 700_000L
        ),
        // ---------- Samsung Exynos ----------
        // 注: Exynos 走 Samsung USB DLM 协议, 非 firehose; 这里仅元数据登记
        LoaderEntry(
            filename = "prog_firehose_exynos2400.elf",
            chipset = "E2400",
            vendor = Vendor.SAMSUNG,
            platforms = listOf("e2400", "exynos2400", "s5e9945"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_exynos2400.elf",
            sizeBytes = 600_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_exynos2200.elf",
            chipset = "E2200",
            vendor = Vendor.SAMSUNG,
            platforms = listOf("e2200", "exynos2200", "s5e9925"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_exynos2200.elf",
            sizeBytes = 580_000L
        ),
        LoaderEntry(
            filename = "prog_firehose_exynos2100.elf",
            chipset = "E2100",
            vendor = Vendor.SAMSUNG,
            platforms = listOf("e2100", "exynos2100", "s5e9815"),
            downloadUrl = "https://github.com/<placeholder>/firehose-loaders/releases/download/v1/prog_firehose_exynos2100.elf",
            sizeBytes = 560_000L
        )
    )

    /**
     * 按芯片平台匹配 loader。
     *
     * @param platform 平台字符串, 可以是:
     *   - ro.board.platform 的原始值 (如 "sm8650")
     *   - EdlRescuer.inferChipset 返回的描述串 (如 "Qualcomm SM8650 (8 Gen 3)")
     *   - 别名 (如 "8gen3" / "pineapple")
     * @return 命中的 [LoaderEntry], 无匹配返回 null
     */
    fun match(platform: String): LoaderEntry? {
        val needle = normalize(platform)
        if (needle.isBlank()) return null
        // 1. 别名 / chipset 精确匹配
        REGISTRY.firstOrNull { entry ->
            entry.platforms.any { normalize(it) == needle } ||
                normalize(entry.chipset) == needle ||
                normalize(entry.filename).contains(needle)
        }?.let { return it }
        // 2. 模糊匹配: needle 中包含某别名, 或某别名包含 needle
        return REGISTRY.firstOrNull { entry ->
            entry.platforms.any {
                val p = normalize(it)
                p.length >= 3 && (needle.contains(p) || p.contains(needle))
            }
        }
    }

    /**
     * 通过 ADB getprop 自动探测被控端芯片平台。
     *
     * 探测顺序:
     *   1. ro.board.platform (最准确, 如 "sm8650")
     *   2. ro.hardware       (备用, 部分平台与 board.platform 一致)
     *   3. ro.soc.manufacturer + ro.soc.model (高通设备会暴露 "QTI" / "SM8650")
     *
     * 结果归一化: lowercase + 去空白。
     *
     * @param serial ADB 设备 serial (兼容旧 API, 实际忽略, 走当前 AdbManager 连接)
     * @return 平台字符串, 探测失败返回 null
     */
    suspend fun detectPlatform(serial: String): String? = withContext(Dispatchers.IO) {
        // 1. 主: ro.board.platform
        val primary = AdbManager.shell(serial, "getprop ro.board.platform")
            ?.trim()?.lowercase()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
        if (!primary.isNullOrBlank()) {
            Logger.i("FirehoseRegistry", "detectPlatform ro.board.platform=$primary")
            return@withContext primary
        }
        // 2. 备用: ro.hardware
        val hw = AdbManager.shell(serial, "getprop ro.hardware")
            ?.trim()?.lowercase()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
        if (!hw.isNullOrBlank()) {
            Logger.i("FirehoseRegistry", "detectPlatform ro.hardware=$hw")
            return@withContext hw
        }
        // 3. 备用: ro.soc.manufacturer + ro.soc.model
        val mfr = AdbManager.shell(serial, "getprop ro.soc.manufacturer")?.trim()?.lowercase().orEmpty()
        val model = AdbManager.shell(serial, "getprop ro.soc.model")?.trim()?.lowercase().orEmpty()
        if (model.isNotBlank() && !model.equals("unknown", true)) {
            val combined = "$mfr $model".trim().replace(Regex("\\s+"), " ")
            Logger.i("FirehoseRegistry", "detectPlatform soc=$combined")
            return@withContext combined
        }
        Logger.w("FirehoseRegistry", "detectPlatform 失败: ADB 未连接或 getprop 无值")
        null
    }

    /**
     * 下载 loader 到本地缓存目录。
     *
     * 使用 OkHttp 流式下载, 支持 progress 回调; 写入 .tmp 文件后原子 rename, 避免半文件污染缓存。
     *
     * @param entry     注册表条目 (必须有 downloadUrl)
     * @param cacheDir  本地缓存目录 (不存在则自动创建)
     * @param onProgress 进度回调 (0f - 1f), 总大小未知时不回调
     * @return 下载成功的 File, 失败返回 null
     */
    suspend fun download(
        entry: LoaderEntry,
        cacheDir: File,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val url = entry.downloadUrl
        if (url.isNullOrBlank()) {
            Logger.e("FirehoseRegistry", "download 失败: ${entry.filename} 无 downloadUrl")
            return@withContext null
        }
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val target = File(cacheDir, entry.filename)
        if (target.exists()) {
            Logger.i("FirehoseRegistry", "已缓存, 跳过下载: ${target.absolutePath}")
            return@withContext target
        }
        val tmp = File(cacheDir, entry.filename + ".tmp")
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logger.e("FirehoseRegistry", "下载失败: HTTP ${resp.code} ${resp.message}")
                    return@withContext null
                }
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()  // -1 if unknown
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                // rename 失败 (跨挂载点), 用 copy 兜底
                tmp.copyTo(target, overwrite = true); tmp.delete()
            }
            Logger.i("FirehoseRegistry", "下载完成: ${target.absolutePath} (${target.length()} bytes)")
            target
        } catch (e: Exception) {
            Logger.e("FirehoseRegistry", "下载异常: ${e.message}")
            tmp.delete()
            null
        }
    }

    /**
     * 列出本地已缓存的 loader 文件。
     *
     * @param cacheDir 缓存目录
     * @return 匹配 prog_firehose_*.(elf|mbn) 的文件列表, 按文件名排序
     */
    fun listLocal(cacheDir: File): List<File> {
        if (!cacheDir.exists()) return emptyList()
        return cacheDir.listFiles()?.filter {
            it.isFile && it.name.matches(
                Regex("prog_firehose_.+\\.(elf|mbn)", RegexOption.IGNORE_CASE)
            )
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    /** 字符串归一化: 小写 + 去空白, 便于平台别名比对。 */
    private fun normalize(s: String): String =
        s.lowercase().trim().replace(Regex("\\s+"), "")
}
