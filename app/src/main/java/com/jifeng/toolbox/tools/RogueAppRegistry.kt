package com.jifeng.toolbox.tools

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 流氓软件 / 云控服务登记表 —— 智能冻结检索的数据源。
 *
 * 诚实方案:
 * - 酷安无公开 API, GitHub 有 API 但维护质量参差;
 * - 因此内置一份手工整理的常见流氓软件清单 (按品牌/类别分组);
 * - 提供 refreshFromRemote() 尝试从社区维护的 GitHub raw JSON 拉取最新清单, 失败兜底内置;
 * - match() 将被控端 `pm list packages` 结果与清单匹配, 生成建议冻结列表。
 *
 * 注意: 不收录 com.android.systemui / com.android.phone 等系统关键包, 冻结会导致变砖。
 */
object RogueAppRegistry {

    private const val TAG = "RogueAppRegistry"

    // 社区维护的流氓软件清单 (raw JSON)。占位仓库, 404/失败时回退内置。
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/jifeng-toolbox/rogue-apps-list/main/registry.json"

    // 缓存路径: /data/data/com.jifeng.toolbox/files/rogue_apps_cache.json
    private const val CACHE_DIR = "/data/data/com.jifeng.toolbox/files"
    private const val CACHE_FILE = "$CACHE_DIR/rogue_apps_cache.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class RogueApp(
        val packageName: String,
        val name: String,                // 中文名
        val category: Category,
        val severity: Severity,
        val reason: String               // 为什么建议冻结
    )

    enum class Category { ADWARE, TRACKER, BLOATWARE, CLOUD_CONTROL, DATA_COLLECTOR, SYSTEM_TWEAKER }
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    /**
     * 内置清单 —— 手工整理的常见流氓软件 / 云控服务 / 预装广告组件。
     * 已剔除 com.android.systemui / com.android.phone / com.android.settings 等系统关键包。
     */
    val BUILTIN_REGISTRY: List<RogueApp> = listOf(
        // ---------- 小米 / MIUI 系 ----------
        RogueApp("com.miui.systemAdSolution", "MIUI 系统广告", Category.ADWARE, Severity.HIGH,
            "MIUI 系统级广告组件, 负责推送通知栏与负一屏广告"),
        RogueApp("com.miui.player", "小米音乐", Category.BLOATWARE, Severity.LOW,
            "预装音乐播放器, 常驻后台联网拉取推荐"),
        RogueApp("com.miui.video", "小米视频", Category.BLOATWARE, Severity.MEDIUM,
            "预装视频应用, 含开屏广告与推荐流"),
        RogueApp("com.miui.yellowpage", "小米黄页", Category.BLOATWARE, Severity.MEDIUM,
            "黄页服务, 频繁推送商家推广信息"),
        RogueApp("com.xiaomi.gamecenter", "小米游戏中心", Category.BLOATWARE, Severity.MEDIUM,
            "游戏分发与广告 SDK, 后台拉活"),
        RogueApp("com.xiaomi.jr", "小米金融", Category.BLOATWARE, Severity.HIGH,
            "金融推广应用, 频繁推送借贷广告"),
        RogueApp("com.miui.hybrid", "MIUI 混合引擎", Category.CLOUD_CONTROL, Severity.MEDIUM,
            "云端下发混合应用, 可远程安装模块"),
        RogueApp("com.miui.bugreport", "MIUI 反馈", Category.DATA_COLLECTOR, Severity.MEDIUM,
            "用户行为与日志上报, 隐私敏感"),
        RogueApp("com.miui.msa.global", "小米安全联盟 (msa)", Category.TRACKER, Severity.HIGH,
            "设备标识与行为埋点 SDK, 跨应用追踪"),
        RogueApp("com.miui.analytics", "MIUI 分析", Category.DATA_COLLECTOR, Severity.HIGH,
            "后台采集使用习惯数据上报小米服务器"),
        RogueApp("com.xiaomi.market", "小米应用商店", Category.BLOATWARE, Severity.MEDIUM,
            "预装商店, 自动检查更新并推送推荐"),

        // ---------- 华为 / EMUI / HarmonyOS 系 ----------
        RogueApp("com.huawei.himovieoverseas", "华为视频 (海外)", Category.BLOATWARE, Severity.LOW,
            "预装视频应用, 推广内容流"),
        RogueApp("com.huawei.music", "华为音乐", Category.BLOATWARE, Severity.LOW,
            "预装音乐, 后台联网拉推荐"),
        RogueApp("com.huawei.appmarket", "华为应用市场", Category.BLOATWARE, Severity.MEDIUM,
            "预装商店, 推送推荐与自动更新"),
        RogueApp("com.huawei.hwid", "华为账号服务", Category.TRACKER, Severity.MEDIUM,
            "账号体系, 持续上报设备标识"),
        RogueApp("com.huawei.himovie", "华为视频 (国内)", Category.BLOATWARE, Severity.LOW,
            "预装视频应用, 含广告"),

        // ---------- OPPO / ColorOS 系 ----------
        RogueApp("com.coloros.gamespaceui", "ColorOS 游戏空间", Category.BLOATWARE, Severity.MEDIUM,
            "游戏中心 UI, 含推广位与广告"),
        RogueApp("com.coloros.weather2", "ColorOS 天气", Category.BLOATWARE, Severity.LOW,
            "预装天气, 含广告位"),
        RogueApp("com.coloros.market", "ColorOS 软件商店", Category.BLOATWARE, Severity.MEDIUM,
            "预装商店, 推送推荐与自动更新"),
        RogueApp("com.hecom.gp", "游戏推广 SDK", Category.ADWARE, Severity.HIGH,
            "游戏联运推广组件, 频繁弹窗"),

        // ---------- vivo / OriginOS 系 ----------
        RogueApp("com.iqoo.hardware.cover", "iQOO 盖板服务", Category.SYSTEM_TWEAKER, Severity.MEDIUM,
            "硬件盖板服务, 部分机型后台常驻"),
        RogueApp("com.vivo.browser", "vivo 浏览器", Category.BLOATWARE, Severity.MEDIUM,
            "预装浏览器, 主页含推广信息流"),
        RogueApp("com.vivo.appstore", "vivo 应用商店", Category.BLOATWARE, Severity.MEDIUM,
            "预装商店, 自动推送更新与推荐"),

        // ---------- 三星 / One UI 系 ----------
        RogueApp("com.samsung.android.aircommand", "三星 Air Command", Category.BLOATWARE, Severity.LOW,
            "S Pen 悬浮命令, 无 S Pen 时无用"),
        RogueApp("com.samsung.android.app.sbrowseredge", "三星浏览器边栏", Category.BLOATWARE, Severity.LOW,
            "Edge 边栏浏览器插件, 多数用户不用"),
        RogueApp("com.samsung.android.game.gamehome", "三星游戏中心", Category.BLOATWARE, Severity.LOW,
            "预装游戏启动器, 含推广"),
        RogueApp("com.samsung.android.app.sbrowser", "三星浏览器", Category.BLOATWARE, Severity.LOW,
            "预装浏览器, 主页含追踪像素"),

        // ---------- 第三方流氓 / 数据采集 / 广告 SDK ----------
        RogueApp("com.tencent.android.location", "腾讯定位服务", Category.TRACKER, Severity.HIGH,
            "后台持续定位 SDK, 跨应用追踪用户位置"),
        RogueApp("com.taobao.taobao", "淘宝", Category.ADWARE, Severity.MEDIUM,
            "频繁推送营销通知, 后台拉活耗电"),
        RogueApp("com.eg.android.AlipayGphone", "支付宝", Category.ADWARE, Severity.LOW,
            "营销推送频繁, 后台常驻 (按需冻结)"),
        RogueApp("com.sohu.sohuvideo", "搜狐视频", Category.ADWARE, Severity.HIGH,
            "开屏广告超长, 后台拉取推荐"),
        RogueApp("com.qihoo.browser", "360 浏览器", Category.ADWARE, Severity.HIGH,
            "主页广告满屏, 静默推送通知"),
        RogueApp("com.baidu.searchbox", "百度", Category.ADWARE, Severity.HIGH,
            "频繁推送通知与广告, 后台拉活"),
        RogueApp("com.ss.android.article.news", "今日头条", Category.ADWARE, Severity.MEDIUM,
            "营销推送频繁, 后台预拉取内容"),
        RogueApp("com.tencent.qqlive", "腾讯视频", Category.ADWARE, Severity.MEDIUM,
            "开屏广告长, 后台推送内容")
    )

    /**
     * 从远端 (GitHub raw JSON) 刷新清单。
     * 成功: 写入本地缓存并返回远端清单;
     * 失败 (网络/HTTP/解析): 返回 BUILTIN_REGISTRY, 不抛异常。
     */
    suspend fun refreshFromRemote(): List<RogueApp> = withContext(Dispatchers.IO) {
        try {
            Logger.i(TAG, "拉取远端清单: $REMOTE_URL")
            val req = Request.Builder()
                .url(REMOTE_URL)
                .header("User-Agent", "JFToolbox")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logger.w(TAG, "远端返回 HTTP ${resp.code}, 回退内置清单")
                    return@withContext BUILTIN_REGISTRY
                }
                val body = resp.body?.string() ?: run {
                    Logger.w(TAG, "远端响应为空, 回退内置清单")
                    return@withContext BUILTIN_REGISTRY
                }
                val parsed = parseJson(body)
                if (parsed.isEmpty()) {
                    Logger.w(TAG, "远端清单为空, 回退内置清单")
                    return@withContext BUILTIN_REGISTRY
                }
                // 写缓存
                runCatching {
                    File(CACHE_DIR).mkdirs()
                    File(CACHE_FILE).writeText(body)
                }.onFailure { Logger.w(TAG, "写缓存失败: ${it.message}") }
                Logger.i(TAG, "远端清单拉取成功: ${parsed.size} 条")
                parsed
            }
        } catch (e: Exception) {
            Logger.w(TAG, "远端拉取异常: ${e.message}, 回退内置清单")
            BUILTIN_REGISTRY
        }
    }

    /**
     * 读取上次缓存的远端清单 (若存在且可解析), 否则返回 null。
     */
    fun loadCached(): List<RogueApp>? {
        val f = File(CACHE_FILE)
        if (!f.exists()) return null
        return runCatching {
            parseJson(f.readText()).takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /**
     * 将已安装包名列表与流氓软件清单匹配, 生成"建议冻结列表"。
     * 默认使用内置清单, 可传入远端刷新后的清单。
     */
    fun match(installedPackages: List<String>, registry: List<RogueApp> = BUILTIN_REGISTRY): List<RogueApp> {
        if (installedPackages.isEmpty()) return emptyList()
        val installedSet = installedPackages.toSet()
        return registry.filter { it.packageName in installedSet }
    }

    /**
     * 解析远端 / 缓存的 JSON 为 RogueApp 列表。
     * JSON 格式: 数组, 每个元素含 packageName/name/category/severity/reason。
     */
    private fun parseJson(body: String): List<RogueApp> {
        val arr = JSONArray(body)
        val out = mutableListOf<RogueApp>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("packageName").trim()
            if (pkg.isBlank()) continue
            val name = o.optString("name").ifBlank { pkg }
            val category = runCatching { Category.valueOf(o.optString("category").uppercase()) }
                .getOrDefault(Category.BLOATWARE)
            val severity = runCatching { Severity.valueOf(o.optString("severity").uppercase()) }
                .getOrDefault(Severity.MEDIUM)
            val reason = o.optString("reason").ifBlank { "用户反馈的流氓软件" }
            out.add(RogueApp(pkg, name, category, severity, reason))
        }
        return out
    }
}
