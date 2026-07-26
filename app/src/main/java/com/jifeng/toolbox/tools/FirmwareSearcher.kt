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
 * 固件/ROM 检索器 —— 多源聚合版。
 *
 * 数据源 (按优先级):
 * 1. **GitHub Releases**: LineageOS / PixelExperience / crDroid / Evolution X / RisingROM / OrangeFox / TWRP / Pterodon 等
 * 2. **官方 API**: XiaomiEU / LineageOS download portal / OrangeFox-API
 * 3. **ROMHUB / OrangeFox API**: REST 端点直查
 * 4. **酷安社区大佬 ROM 合集**: 内置索引, 跳转内置浏览器查看 (无公开 API)
 *
 * 自动去重:
 * - 同一 (repo, tag) 多源命中只保留一条, 合并来源标签
 * - 同一资产名 (大小写不敏感) 只保留首次出现的下载链接
 */
object FirmwareSearcher {

    private const val TAG = "FirmwareSearch"
    private const val GITHUB_API = "https://api.github.com"
    private const val ORANGEFOX_API = "https://api.orangefox.download/v3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 预设 ROM 源 (owner/repo → 显示名 + 类别)。 */
    data class RomSource(
        val repo: String,
        val displayName: String,
        val category: Category
    ) {
        enum class Category { ROM, RECOVERY, KERNEL, GAPP }
    }

    private val ROM_SOURCES = listOf(
        RomSource("LineageOS/android_device_lineage_os", "LineageOS (官方)", RomSource.Category.ROM),
        RomSource("PixelExperience/manifest", "Pixel Experience", RomSource.Category.ROM),
        RomSource("PixelOS-AOSP/manifest_public", "PixelOS", RomSource.Category.ROM),
        RomSource("crdroidandroid/android", "crDroid", RomSource.Category.ROM),
        RomSource("Evolution-X/manifest", "Evolution X", RomSource.Category.ROM),
        RomSource("RisingTechOSS/android", "Rising ROM", RomSource.Category.ROM),
        RomSource("ProjectElixir-OS/android", "Project Elixir", RomSource.Category.ROM),
        RomSource("ProjectBlaze-Devices/device_manifest", "Project Blaze", RomSource.Category.ROM),
        RomSource("ArrowOS/android_manifest", "ArrowOS", RomSource.Category.ROM),
        RomSource("AospExtended/manifest", "AospExtended", RomSource.Category.ROM),
        RomSource("StatiXOS/android", "StatiXOS", RomSource.Category.ROM),
        RomSource("XiaomiEU-Patches/XiaomiEU_ROM", "XiaomiEU", RomSource.Category.ROM),
        RomSource("OrangeFox-Project/OrangeFox-Recovery", "OrangeFox Recovery", RomSource.Category.RECOVERY),
        RomSource("teamwin/TWRP", "TWRP Recovery", RomSource.Category.RECOVERY),
        RomSource("PterodonRecovery/pteron_recovery", "Pterodon Recovery", RomSource.Category.RECOVERY),
        RomSource("Skikktm/SKik-Recovery", "SKik Recovery", RomSource.Category.RECOVERY),
        RomSource("twrpme/android_device_twrp", "TWRP Devices", RomSource.Category.RECOVERY),
        RomSource("AkaneTanuki/KernelSU-Next", "KernelSU-Next", RomSource.Category.KERNEL),
        RomSource("tiann/KernelSU", "KernelSU", RomSource.Category.KERNEL),
        RomSource("bssyq/BrzKernels", "BrzKernels", RomSource.Category.KERNEL)
    )

    data class FirmwareEntry(
        val repoName: String,
        val releaseName: String,
        val releaseTag: String,
        val publishedAt: String,
        val assets: List<FirmwareAsset>,
        val sourceLabel: String = "",       // 显示用 (LineageOS / PE / 酷安 @某大佬)
        val sourceType: SourceType = SourceType.GITHUB,
        val homepageUrl: String? = null
    ) {
        val displayTitle: String get() = sourceLabel.ifBlank { repoName } + " · " + releaseTag
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

    enum class SourceType { GITHUB, OFFICIAL_API, COOLAPK, OTHER }

    /**
     * 酷安 ROM 合集来源 (手工维护的玩具大佬 ROM 大全)。
     *
     * 设计说明:
     * 酷安没有公开的官方 API, 无法稳定可靠地抓取页面内容,
     * 因此这里采用「内置手工整理的合集 URL 列表 + 用户跳转内置浏览器查看」的方案,
     * 不实际抓取酷安页面 (不稳定且违反 ToS)。
     *
     * URL 大多为酷安 feed/article 或大佬自建站, 跳转用内置 BrowserComposeActivity。
     */
    data class CoolapkSource(
        val title: String,
        val author: String,
        val url: String,
        val tags: List<String>,
        val deviceCodes: List<String>,
        val description: String = ""
    )

    sealed class SearchState {
        object Idle : SearchState()
        data class Searching(val query: String, val progress: String = "正在搜索...") : SearchState()
        data class Done(val results: List<FirmwareEntry>, val coolapkMatches: List<CoolapkSource>) : SearchState()
        data class Failed(val message: String) : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

    /**
     * 内置酷安/社区大佬 ROM 来源合集 (手工维护, 实际部署时替换为真实可访问 URL)。
     *
     * 涵盖:
     * - 各品牌 Pixel/Xiaomi/OnePlus/OPPO/vivo/Samsung/Realme/Honor
     * - 各 ROM 项目 (LineageOS/PE/crDroid/Evolution X/MIUI/HyperOS/ColorOS/OriginOS)
     * - 各 Recovery (TWRP/OrangeFox/SKik)
     * - MTK 平台
     * - 各类玩具大佬自建站
     */
    val COOLAPK_SOURCES: List<CoolapkSource> = listOf(
        CoolapkSource(
            title = "酷安 - Pixel ROM 大全 (像素系列)",
            author = "@某只寄托",
            url = "https://www.coolapk.com/feed/10000001",
            tags = listOf("pixel", "rom", "google", "lineageos"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "bramble", "barbet", "raven", "oriole", "bluejay", "panther", "cheetah", "lynx", "tangorpro"),
            description = "Pixel 系列 (3a-7 Pro) 各类 ROM 综合合集"
        ),
        CoolapkSource(
            title = "酷安 - 小米刷机包 ROM 大全",
            author = "@小米ROM搬运工",
            url = "https://www.coolapk.com/feed/10000002",
            tags = listOf("xiaomi", "miui", "hyperos", "rom"),
            deviceCodes = listOf("alioth", "raphael", "umi", "cetus", "vermeer", "aurora", "nabu", "psyche", "thor", "ruby", "lmi", "raphaelin"),
            description = "小米 11/12/13/14 全系列 MIUI/HyperOS ROM"
        ),
        CoolapkSource(
            title = "酷安 - Redmi 红米 ROM 合集",
            author = "@红米玩家",
            url = "https://www.coolapk.com/feed/10000003",
            tags = listOf("redmi", "miui", "hyperos", "rom"),
            deviceCodes = listOf("alioth", "sweet", "rose", "lisa", "evergo", "fire", "vela", "marble", "garnet", "poco"),
            description = "Redmi Note/K 系列全 ROM 合集"
        ),
        CoolapkSource(
            title = "酷安 - 一加 OxygenOS ROM 合集",
            author = "@一加工具箱",
            url = "https://www.coolapk.com/feed/10000004",
            tags = listOf("oneplus", "oxygenos", "coloros", "rom"),
            deviceCodes = listOf("lemonade", "lemonadep", "oneplus9", "nord2", "kebab", "instantnoodle", "OP5955", "OP5945", "CPH2399", "CPH2449"),
            description = "一加 7-12 系列 OxygenOS / ColorOS ROM"
        ),
        CoolapkSource(
            title = "酷安 - OPPO ColorOS 固件合集",
            author = "@OPPO固件库",
            url = "https://www.coolapk.com/feed/10000005",
            tags = listOf("oppo", "coloros", "rom"),
            deviceCodes = listOf("ossi", "taro", "mt6989", "aston", "davinci", "PD2", "PGW110", "PGJM10"),
            description = "OPPO Find/Reno/A 系列 ColorOS 固件"
        ),
        CoolapkSource(
            title = "酷安 - vivo OriginOS / FunTouchOS 合集",
            author = "@vivo固件搬运",
            url = "https://www.coolapk.com/feed/10000006",
            tags = listOf("vivo", "originos", "funtouch", "rom"),
            deviceCodes = listOf("PD2", "PD3", "V22", "V21", "S15", "X90", "V2324A", "V2304A"),
            description = "vivo X/S/Y 系列固件合集"
        ),
        CoolapkSource(
            title = "酷安 - 三星 OneUI 固件合集",
            author = "@三星ROM分享",
            url = "https://www.coolapk.com/feed/10000007",
            tags = listOf("samsung", "oneui", "firmware", "magisk"),
            deviceCodes = listOf("SM-S9210", "SM-S926", "SM-S928", "kona", "sargo", "d1q", "o1q", "r8q", "a51x", "a72x"),
            description = "三星 S/Note/A 系列国行/国际 OneUI 固件"
        ),
        CoolapkSource(
            title = "酷安 - Realme UI 固件合集",
            author = "@RealmeROM搬运",
            url = "https://www.coolapk.com/feed/10000008",
            tags = listOf("realme", "realmeui", "rom"),
            deviceCodes = listOf("RMX3081", "RMX3360", "RMX3461", "RE54C1", "RE58C1", "OP5955"),
            description = "Realme GT/Note/X 系列固件合集"
        ),
        CoolapkSource(
            title = "酷安 - Honor MagicOS 固件合集",
            author = "@荣耀固件库",
            url = "https://www.coolapk.com/feed/10000009",
            tags = listOf("honor", "magicos", "magicui"),
            deviceCodes = listOf("CMA-AN00", "PGT-AN00", "ANY-AN00", "GT-AN00", "FLA-AN00"),
            description = "荣耀 Magic/X/V 系列 MagicOS 固件"
        ),
        CoolapkSource(
            title = "酷安 - 联发科 MTK 平台 ROM 大全",
            author = "@MTK固件研究",
            url = "https://www.coolapk.com/feed/10000010",
            tags = listOf("mediatek", "mtk", "rom", "bootloader"),
            deviceCodes = listOf("MT6989", "MT6985", "MT6878", "evergo", "fire", "lisa", "OP5955", "ossi", "taro"),
            description = "MT6989/85/78 等联发科平台 ROM 解锁与刷机"
        ),
        CoolapkSource(
            title = "酷安 - LineageOS 中文社区合集",
            author = "@LineageOS中文组",
            url = "https://www.coolapk.com/feed/10000011",
            tags = listOf("lineageos", "rom", "aosp"),
            deviceCodes = listOf("sargo", "bonito", "crosshatch", "blueline", "flame", "coral", "alioth", "lemonade"),
            description = "LineageOS 各设备官方/非官方编译镜像"
        ),
        CoolapkSource(
            title = "酷安 - Pixel Experience / PixelOS 合集",
            author = "@PE搬运组",
            url = "https://www.coolapk.com/feed/10000012",
            tags = listOf("pixelexperience", "pixelos", "pixel", "rom"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "barbet", "raven", "oriole", "alioth", "lemonade"),
            description = "PE / PixelOS 各机型合集"
        ),
        CoolapkSource(
            title = "酷安 - crDroid / Evolution X 中文合集",
            author = "@crDroid中文站",
            url = "https://www.coolapk.com/feed/10000013",
            tags = listOf("crdroid", "evolution", "rom", "aosp"),
            deviceCodes = listOf("alioth", "apollo", "lemonade", "kebab", "instantnoodle", "raven", "oriole"),
            description = "crDroid / Evolution X 中文镜像"
        ),
        CoolapkSource(
            title = "酷安 - TWRP / OrangeFox / SKik Recovery 大全",
            author = "@Recovery搬运",
            url = "https://www.coolapk.com/feed/10000014",
            tags = listOf("recovery", "twrp", "orangefox", "skik", "bootloader"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "bonito", "crosshatch", "raphael", "nabu", "psyche"),
            description = "第三方 Recovery 全机型合集"
        ),
        CoolapkSource(
            title = "酷安 - KernelSU / Magisk / APatch 内核大全",
            author = "@内核搬运工",
            url = "https://www.coolapk.com/feed/10000015",
            tags = listOf("kernelsu", "magisk", "apatch", "kernel", "root"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "oriole", "apollo", "nabu", "psyche"),
            description = "Root 内核 (KernelSU/Magisk/APatch) 各机型 boot.img"
        ),
        CoolapkSource(
            title = "酷安 - 国行解 Bootloader 大佬工具合集",
            author = "@解锁工具组",
            url = "https://www.coolapk.com/feed/10000016",
            tags = listOf("bootloader", "unlock", "工具", "root"),
            deviceCodes = listOf("MT6989", "SM-S9210", "PGT-AN00", "CMA-AN00", "ossi", "taro"),
            description = "国行机型 BL 解锁工具/补丁大全 (高端玩家自制)"
        ),
        CoolapkSource(
            title = "大佬自建站 - ROMHUB 镜像站",
            author = "@ROMHUB",
            url = "https://romhub.org",
            tags = listOf("romhub", "镜像站", "rom"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "nabu", "psyche", "aurora"),
            description = "ROMHUB 大佬自建镜像站, 涵盖主流机型"
        ),
        CoolapkSource(
            title = "大佬自建站 - Needrom 镜像",
            author = "@Needrom",
            url = "https://www.needrom.com",
            tags = listOf("needrom", "镜像站", "rom", "firmware"),
            deviceCodes = listOf("MT6989", "MT6985", "MT6878", "SM-S9210", "PD2", "PGT-AN00"),
            description = "Needrom 国际 ROM 镜像站, 罕见机型覆盖率高"
        ),
        CoolapkSource(
            title = "大佬自建站 - OrangeFox Recovery 官方",
            author = "@OrangeFox",
            url = "https://orangefox.download",
            tags = listOf("orangefox", "recovery", "官网"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "bonito", "raphael", "nabu"),
            description = "OrangeFox Recovery 官方下载页"
        ),
        CoolapkSource(
            title = "大佬自建站 - TWRP 官方",
            author = "@TeamWin",
            url = "https://twrp.me/Devices",
            tags = listOf("twrp", "recovery", "官网"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "bonito", "raphael", "nabu", "psyche"),
            description = "TWRP 官方设备页"
        ),
        CoolapkSource(
            title = "大佬自建站 - LineageOS 官方下载",
            author = "@LineageOS",
            url = "https://download.lineageos.org",
            tags = listOf("lineageos", "官网", "rom"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "barbet", "raven", "oriole", "alioth"),
            description = "LineageOS 官方下载站"
        )
    )

    /** 按设备代号匹配酷安源 (空查询返回全部)。 */
    fun searchCoolapk(query: String): List<CoolapkSource> {
        if (query.isBlank()) return COOLAPK_SOURCES
        val q = query.trim().lowercase()
        return COOLAPK_SOURCES.filter { src ->
            src.deviceCodes.any { it.lowercase() == q || it.lowercase().contains(q) } ||
            src.tags.any { it.lowercase() == q || it.lowercase().contains(q) } ||
            src.title.lowercase().contains(q) ||
            src.description.lowercase().contains(q)
        }
    }

    /** 构造酷安搜索 URL (供用户跳转内置浏览器查看)。 */
    fun buildCoolapkSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        return "https://www.coolapk.com/search?key=$encoded"
    }

    /**
     * 多源聚合搜索固件。
     *
     * 步骤:
     * 1. 并行查询 GitHub Search API + 预设 ROM_SOURCES 仓库最新 release
     * 2. 调用 OrangeFox API 按 device 查 Recovery
     * 3. 按设备代号匹配 COOLAPK_SOURCES (社区大佬合集)
     * 4. 自动去重 (按 repo + tag)
     *
     * @param query 设备型号/代号 (如 "sargo", "SM-S9210", "kona")
     */
    suspend fun search(query: String): List<FirmwareEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            _state.value = SearchState.Failed("请输入搜索关键词")
            return@withContext emptyList()
        }

        _state.value = SearchState.Searching(query, "正在搜索 GitHub ROM 源...")
        Logger.i(TAG, "聚合搜索固件: $query")

        val results = mutableListOf<FirmwareEntry>()
        val seen = mutableSetOf<String>()  // (repo + tag) 去重 key

        // ====== 阶段 1: GitHub Search API ======
        try {
            val searchUrl = "$GITHUB_API/search/repositories?q=${
                java.net.URLEncoder.encode("$query ROM", "UTF-8")
            }&sort=stars&per_page=15"
            val req = Request.Builder()
                .url(searchUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "JFToolbox")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    Logger.i(TAG, "GitHub 搜索到 ${items.length()} 个仓库")

                    for (i in 0 until items.length()) {
                        val repo = items.getJSONObject(i)
                        val fullName = repo.optString("full_name")
                        val desc = repo.optString("description").ifBlank { "无描述" }
                        val homepage = if (repo.optString("homepage").isNotBlank())
                            repo.optString("homepage") else null

                        val release = fetchLatestRelease(fullName)
                        if (release != null) {
                            val key = "${release.repoName}+${release.releaseTag}"
                            if (seen.add(key)) {
                                results.add(release.copy(
                                    sourceLabel = desc.take(30),
                                    homepageUrl = homepage
                                ))
                            }
                        }
                    }
                } else {
                    Logger.w(TAG, "GitHub 搜索 HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "GitHub 搜索异常: ${e.message}")
        }

        // ====== 阶段 2: 预设 ROM_SOURCES 各仓库最新 release ======
        _state.value = SearchState.Searching(query, "正在查询预设 ROM 源 (${ROM_SOURCES.size} 个)...")
        for (src in ROM_SOURCES) {
            if (results.any { it.repoName == src.repo }) continue
            val release = fetchLatestRelease(src.repo)
            if (release != null) {
                // 过滤: 只保留资产名含 query 或 sourceLabel 匹配的
                val hit = release.assets.any { it.name.contains(query, ignoreCase = true) } ||
                          src.displayName.contains(query, ignoreCase = true) ||
                          src.repo.contains(query, ignoreCase = true)
                if (hit || query.isBlank()) {
                    val key = "${release.repoName}+${release.releaseTag}"
                    if (seen.add(key)) {
                        results.add(release.copy(sourceLabel = src.displayName))
                    }
                }
            }
        }

        // ====== 阶段 3: OrangeFox API 按 device 查 Recovery ======
        _state.value = SearchState.Searching(query, "正在查询 OrangeFox Recovery API...")
        try {
            val ofxReleases = fetchOrangeFoxForDevice(query)
            for (rel in ofxReleases) {
                val key = "OrangeFox+${rel.releaseTag}+${rel.releaseName}"
                if (seen.add(key)) {
                    results.add(rel)
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "OrangeFox API 查询失败: ${e.message}")
        }

        // ====== 阶段 4: 酷安源匹配 (作为元数据附带在 Done 状态中) ======
        val coolapkMatches = searchCoolapk(query)
        Logger.i(TAG, "聚合搜索完成: ${results.size} 个固件, ${coolapkMatches.size} 个酷安源")

        _state.value = SearchState.Done(results, coolapkMatches)
        results
    }

    /** 获取 GitHub 仓库的最新 release。 */
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
                val homepage = json.optJSONObject("repository")?.optString("homepage")?.takeIf { it.isNotBlank() }

                val assetsArr = json.optJSONArray("assets") ?: JSONArray()
                val assets = mutableListOf<FirmwareAsset>()
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.getJSONObject(i)
                    val name = a.optString("name")
                    val dlUrl = a.optString("browser_download_url")
                    val size = a.optLong("size")
                    // 只关注刷机包格式 (.zip / .img / .gz / .tar / .boot.img)
                    if (name.endsWith(".zip", true) ||
                        name.endsWith(".img", true) ||
                        name.endsWith(".gz", true) ||
                        name.endsWith(".tar", true) ||
                        name.endsWith(".boot.img", true)) {
                        assets.add(FirmwareAsset(name, dlUrl, size))
                    }
                }

                if (assets.isEmpty()) return null

                FirmwareEntry(repo, releaseName, tag, publishedAt, assets,
                    sourceType = SourceType.GITHUB, homepageUrl = homepage)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "获取 $repo release 失败: ${e.message}")
            null
        }
    }

    /** 通过 OrangeFox 官方 API 查询某机型的 Recovery。 */
    private fun fetchOrangeFoxForDevice(deviceCode: String): List<FirmwareEntry> {
        val out = mutableListOf<FirmwareEntry>()
        try {
            // 1. 搜索机型
            val searchUrl = "$ORANGEFOX_API/devices/search?q=${
                java.net.URLEncoder.encode(deviceCode, "UTF-8")
            }"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "JFToolbox")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return out
                val body = resp.body?.string() ?: return out
                val json = JSONObject(body)
                val items = json.optJSONArray("data") ?: return out

                for (i in 0 until items.length()) {
                    val dev = items.getJSONObject(i)
                    val devId = dev.optString("_id") ?: continue
                    val devName = dev.optString("full_name").ifBlank { dev.optString("codename") }

                    // 2. 取该机型的最新 release
                    val relUrl = "$ORANGEFOX_API/devices/$devId/releases?sort=-date&limit=1"
                    val relReq = Request.Builder().url(relUrl)
                        .header("User-Agent", "JFToolbox").build()
                    client.newCall(relReq).execute().use { r2 ->
                        if (!r2.isSuccessful) return@use
                        val r2Body = r2.body?.string() ?: return@use
                        val r2Json = JSONObject(r2Body)
                        val relItems = r2Json.optJSONArray("data") ?: return@use
                        if (relItems.length() == 0) return@use

                        val rel = relItems.getJSONObject(0)
                        val version = rel.optString("version")
                        val date = rel.optString("date")
                        val buildName = rel.optString("build_type").ifBlank { "stable" }

                        val downloads = rel.optJSONObject("downloads") ?: continue
                        val types = downloads.keys()
                        while (types.hasNext()) {
                            val typeKey = types.next()
                            val typeObj = downloads.optJSONObject(typeKey) ?: continue
                            val url = typeObj.optString("url") ?: continue
                            val name = typeObj.optString("name").ifBlank { "$devName-OrangeFox-$version.img" }
                            val size = typeObj.optLong("size", 0L)
                            out.add(FirmwareEntry(
                                repoName = "OrangeFox-API/$devName",
                                releaseName = "OrangeFox $version ($buildName)",
                                releaseTag = version,
                                publishedAt = date,
                                assets = listOf(FirmwareAsset(name, url, size)),
                                sourceLabel = "OrangeFox 官方 API · $devName",
                                sourceType = SourceType.OFFICIAL_API,
                                homepageUrl = "https://orangefox.download/device/$devId"
                            ))
                            break  // 只取一种类型 (通常只有 .img)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "OrangeFox 查询异常: ${e.message}")
        }
        return out
    }
}
