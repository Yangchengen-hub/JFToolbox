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

    sealed class SearchState {
        object Idle : SearchState()
        data class Searching(val query: String) : SearchState()
        data class Done(val results: List<FirmwareEntry>) : SearchState()
        data class Failed(val message: String) : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state

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
