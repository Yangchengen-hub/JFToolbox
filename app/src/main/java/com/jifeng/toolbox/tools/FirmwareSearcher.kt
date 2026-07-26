package com.jifeng.toolbox.tools

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 固件/ROM 检索器 —— 从 GitHub Releases 搜索适配设备的刷机包。
 *
 * 数据源:
 * - GitHub API: 搜索已知 ROM 仓库的 releases (LineageOS, PixelExperience, crDroid 等)
 * - 按设备型号/代号匹配 release assets 中的 .img/.zip 文件
 *
 * 搜索策略:
 * - 用户输入型号 (如 "sargo", "SM-S9210", "MT6989")
 * - 在预设 ROM 仓库列表中查找含该关键词的 release
 * - 返回可下载的固件条目
 */
object FirmwareSearcher {

    private const val TAG = "FirmwareSearch"
    private const val GITHUB_API = "https://api.github.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 预设 ROM 源 (owner/repo → 显示名)。 */
    private val ROM_SOURCES = listOf(
        "LineageOS/android_device_lineage_os" to "LineageOS (官方)",
        "PixelExperience/manifest" to "Pixel Experience",
        "crdroidandroid/android" to "crDroid",
        "OrangeFox-Project/OrangeFox-Recovery" to "OrangeFox Recovery",
        "TeamWinRecovery/TWRP" to "TWRP Recovery"
    )

    data class FirmwareEntry(
        val repoName: String,
        val releaseName: String,
        val releaseTag: String,
        val publishedAt: String,
        val assets: List<FirmwareAsset>
    ) {
        val displayTitle: String get() = "$repoName · $releaseTag"
        val displayDate: String get() = publishedAt.substringBefore("T").ifBlank { "未知日期" }
    }

    data class FirmwareAsset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long
    ) {
        val sizeFormatted: String
            get() = when {
                sizeBytes >= 1_000_000_000 -> "%.2f GB".format(sizeBytes / 1_000_000_000.0)
                sizeBytes >= 1_000_000 -> "%.1f MB".format(sizeBytes / 1_000_000.0)
                else -> "${sizeBytes / 1_000} KB"
            }
    }

    /**
     * 酷安 ROM 合集来源 (手工维护)。
     *
     * 设计说明:
     * 酷安没有公开的官方 API, 无法稳定可靠地抓取页面内容,
     * 因此这里采用「内置手工整理的合集 URL 列表 + 用户跳转浏览器查看」的诚实方案,
     * 不实际抓取酷安页面 (不稳定且违反 ToS)。
     *
     * @param title       合集标题, 如 "酷安 - Pixel ROM 合集"
     * @param author      作者, 如 "@某只寄托"
     * @param url         酷安 feed/article URL
     * @param tags        标签, 如 ["pixel", "rom"]
     * @param deviceCodes 适配的设备代号, 用于匹配用户输入, 如 ["sargo", "redfin"]
     */
    data class CoolapkSource(
        val title: String,
        val author: String,
        val url: String,
        val tags: List<String>,
        val deviceCodes: List<String>
    )

    sealed class SearchState {
        object Idle : SearchState()
        data class Searching(val query: String) : SearchState()
        data class Done(val results: List<FirmwareEntry>) : SearchState()
        data class Failed(val message: String) : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    /**
     * 内置酷安 ROM 来源合集 (手工维护)。
     *
     * 注意:
     * - 下面的 URL 使用占位格式 https://www.coolapk.com/feed/<id>,
     *   实际部署时需要替换为真实可访问的酷安 feed/article URL。
     * - 酷安无公开 API, 这里仅作为索引跳转, 不抓取页面内容。
     * - 每条目标注适配的设备代号, 方便按用户输入匹配。
     */
    val COOLAPK_SOURCES: List<CoolapkSource> = listOf(
        CoolapkSource(
            title = "酷安 - Pixel ROM 合集",
            author = "@某只寄托",
            url = "https://www.coolapk.com/feed/10000001", // 实际部署时替换为真实URL
            tags = listOf("pixel", "rom", "google"),
            deviceCodes = listOf("sargo", "redfin", "bramble", "barbet", "raven", "oriole")
        ),
        CoolapkSource(
            title = "酷安 - 小米刷机包合集",
            author = "@小米ROM搬运工",
            url = "https://www.coolapk.com/feed/10000002", // 实际部署时替换为真实URL
            tags = listOf("xiaomi", "miui", "hyperos"),
            deviceCodes = listOf("alioth", "raphael", "umi", "cetus", "vermeer", "aurora")
        ),
        CoolapkSource(
            title = "酷安 - Redmi 红米ROM合集",
            author = "@红米玩家",
            url = "https://www.coolapk.com/feed/10000003", // 实际部署时替换为真实URL
            tags = listOf("redmi", "miui", "hyperos"),
            deviceCodes = listOf("alioth", "sweet", "rose", "lisa", "evergo", "fire")
        ),
        CoolapkSource(
            title = "酷安 - 一加 OxygenOS 合集",
            author = "@一加工具箱",
            url = "https://www.coolapk.com/feed/10000004", // 实际部署时替换为真实URL
            tags = listOf("oneplus", "oxygenos", "rom"),
            deviceCodes = listOf("lemonade", "lemonadep", "oneplus9", "nord2", "kebab", "instantnoodle")
        ),
        CoolapkSource(
            title = "酷安 - OPPO ColorOS 合集",
            author = "@OPPO固件库",
            url = "https://www.coolapk.com/feed/10000005", // 实际部署时替换为真实URL
            tags = listOf("oppo", "coloros"),
            deviceCodes = listOf("ossi", "taro", "mt6989", "aston", "davinci", "PD2")
        ),
        CoolapkSource(
            title = "酷安 - vivo OriginOS 合集",
            author = "@vivo固件搬运",
            url = "https://www.coolapk.com/feed/10000006", // 实际部署时替换为真实URL
            tags = listOf("vivo", "originos", "funtouch"),
            deviceCodes = listOf("PD2", "PD3", "V22", "V21", "S15", "X90")
        ),
        CoolapkSource(
            title = "酷安 - 三星 OneUI 固件合集",
            author = "@三星ROM分享",
            url = "https://www.coolapk.com/feed/10000007", // 实际部署时替换为真实URL
            tags = listOf("samsung", "oneui", "firmware"),
            deviceCodes = listOf("SM-S9210", "SM-S926", "SM-S928", "kona", "sargo", "d1q")
        ),
        CoolapkSource(
            title = "酷安 - 联发科平台 ROM 合集",
            author = "@MTK固件研究",
            url = "https://www.coolapk.com/feed/10000008", // 实际部署时替换为真实URL
            tags = listOf("mediatek", "mtk", "rom"),
            deviceCodes = listOf("MT6989", "MT6985", "MT6878", "evergo", "fire", "lisa")
        ),
        CoolapkSource(
            title = "酷安 - LineageOS 中文社区合集",
            author = "@LineageOS中文组",
            url = "https://www.coolapk.com/feed/10000009", // 实际部署时替换为真实URL
            tags = listOf("lineageos", "rom", "aosp"),
            deviceCodes = listOf("sargo", "bonito", "crosshatch", "blueline", "flame", "coral")
        ),
        CoolapkSource(
            title = "酷安 - Pixel Experience 合集",
            author = "@PE搬运组",
            url = "https://www.coolapk.com/feed/10000010", // 实际部署时替换为真实URL
            tags = listOf("pixelexperience", "pixel", "rom"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "barbet", "raven", "oriole")
        ),
        CoolapkSource(
            title = "酷安 - crDroid 中文合集",
            author = "@crDroid中文站",
            url = "https://www.coolapk.com/feed/10000011", // 实际部署时替换为真实URL
            tags = listOf("crdroid", "rom", "aosp"),
            deviceCodes = listOf("alioth", "apollo", "lemonade", "kebab", "instantnoodle")
        ),
        CoolapkSource(
            title = "酷安 - Recovery (TWRP/OrangeFox) 合集",
            author = "@Recovery搬运",
            url = "https://www.coolapk.com/feed/10000012", // 实际部署时替换为真实URL
            tags = listOf("recovery", "twrp", "orangefox"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "bonito", "crosshatch")
        )
    )

    /**
     * 按设备代号匹配酷安源。
     *
     * 匹配规则:
     * - 用户输入的 query 与 deviceCodes 列表进行大小写不敏感的精确/包含匹配;
     * - query 与 tags 中的标签匹配也算命中;
     * - 空查询返回全部酷安源 (便于浏览)。
     *
     * @param query 设备型号/代号 (如 "sargo")
     * @return 命中的酷安源列表
     */
    fun searchCoolapk(query: String): List<CoolapkSource> {
        if (query.isBlank()) return COOLAPK_SOURCES
        val q = query.trim().lowercase()
        return COOLAPK_SOURCES.filter { src ->
            src.deviceCodes.any { it.lowercase() == q || it.lowercase().contains(q) } ||
            src.tags.any { it.lowercase() == q || it.lowercase().contains(q) } ||
            src.title.lowercase().contains(q)
        }
    }

    /**
     * 构造酷安搜索 URL (供用户跳转浏览器查看)。
     *
     * 酷安站内搜索的 URL 形如:
     *   https://www.coolapk.com/search?key=<keyword>
     *
     * @param query 搜索关键词
     * @return 已编码的酷安搜索 URL
     */
    fun buildCoolapkSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        return "https://www.coolapk.com/search?key=$encoded"
    }

    /**
     * 搜索固件。
     * @param query 设备型号/代号 (如 "sargo", "SM-S9210", "kona")
     */
    suspend fun search(query: String): List<FirmwareEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            _state.value = SearchState.Failed("请输入搜索关键词")
            return@withContext emptyList()
        }

        _state.value = SearchState.Searching(query)
        Logger.i(TAG, "搜索固件: $query")

        val results = mutableListOf<FirmwareEntry>()

        // 使用 GitHub Search API 搜索含关键词的 releases
        // GET /search/repositories?q={query}+topic:android+rom
        try {
            val searchUrl = "$GITHUB_API/search/repositories?q=${java.net.URLEncoder.encode(query, "UTF-8")}%20ROM&sort=stars&per_page=10"
            val req = Request.Builder()
                .url(searchUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "JFToolbox")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logger.e(TAG, "GitHub 搜索失败: HTTP ${resp.code}")
                    _state.value = SearchState.Failed("GitHub API 返回 ${resp.code}")
                    return@withContext emptyList()
                }

                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: JSONArray()

                for (i in 0 until items.length()) {
                    val repo = items.getJSONObject(i)
                    val fullName = repo.optString("full_name")
                    val desc = repo.optString("description").ifBlank { "无描述" }

                    // 获取该仓库的最新 release
                    val release = fetchLatestRelease(fullName)
                    if (release != null) {
                        results.add(release)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "搜索异常: ${e.message}")
            _state.value = SearchState.Failed("搜索失败: ${e.message}")
            return@withContext emptyList()
        }

        // 也搜索预设 ROM 源
        for ((repo, displayName) in ROM_SOURCES) {
            if (results.any { it.repoName == repo }) continue
            val release = fetchLatestRelease(repo)
            if (release != null && release.assets.any { it.name.contains(query, ignoreCase = true) }) {
                results.add(release)
            }
        }

        Logger.i(TAG, "搜索完成: ${results.size} 个结果")
        _state.value = SearchState.Done(results)
        results
    }

    private fun fetchLatestRelease(repo: String): FirmwareEntry? {
        return try {
            val url = "$GITHUB_API/repos/$repo/releases/latest"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "JFToolbox")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)

                val releaseName = json.optString("name").ifBlank { json.optString("tag_name") }
                val tag = json.optString("tag_name")
                val publishedAt = json.optString("published_at")

                val assetsArr = json.optJSONArray("assets") ?: JSONArray()
                val assets = mutableListOf<FirmwareAsset>()
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.getJSONObject(i)
                    val name = a.optString("name")
                    val dlUrl = a.optString("browser_download_url")
                    val size = a.optLong("size")
                    // 只关注刷机包格式
                    if (name.endsWith(".zip", true) || name.endsWith(".img", true) || name.endsWith(".gz", true)) {
                        assets.add(FirmwareAsset(name, dlUrl, size))
                    }
                }

                if (assets.isEmpty()) return null

                FirmwareEntry(repo, releaseName, tag, publishedAt, assets)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "获取 $repo release 失败: ${e.message}")
            null
        }
    }
}
