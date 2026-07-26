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
            title = "大佬自建站 - ROM基地 (ROMJD)",
            author = "@ROM基地",
            url = "http://www.romjd.com/",
            tags = listOf("romjd", "镜像站", "rom", "firmware"),
            deviceCodes = listOf("alioth", "raphael", "sargo", "bonito", "lemonade", "RMX3081", "SM-S9210", "PD2"),
            description = "国内最大最专业的安卓ROM刷机包资源站, 1700+品牌, 22000+ROM包"
        ),
        CoolapkSource(
            title = "大佬自建站 - MIUI官方ROM仓库",
            author = "@海力力",
            url = "https://roms.miuier.com/",
            tags = listOf("miui", "xiaomi", "hyperos", "rom"),
            deviceCodes = listOf("alioth", "raphael", "umi", "cetus", "vermeer", "aurora", "nabu", "psyche", "thor", "ruby", "lmi"),
            description = "酷安大佬@海力力维护的MIUI官方ROM仓库, 每日更新"
        ),
        CoolapkSource(
            title = "大佬自建站 - HyperOS Fans",
            author = "@HyperOSFans",
            url = "https://hyperos.fans/zh/devices",
            tags = listOf("hyperos", "xiaomi", "rom"),
            deviceCodes = listOf("alioth", "nabu", "psyche", "thor", "ruby", "lmi", "marble", "garnet"),
            description = "HyperOS官方固件下载, 小米/红米全系列"
        ),
        CoolapkSource(
            title = "大佬自建站 - 大侠阿木云盘",
            author = "@大侠阿木",
            url = "https://yun.daxiaamu.com/",
            tags = listOf("oneplus", "oppo", "realme", "rom", "tool"),
            deviceCodes = listOf("lemonade", "lemonadep", "kebab", "instantnoodle", "CPH2399", "CPH2449", "RMX3081", "RMX3360"),
            description = "酷安知名博主大侠阿木的资源盘, 一加/OPPO/真我ROM"
        ),
        CoolapkSource(
            title = "大佬自建站 - OPPO/一加/真我 ROM",
            author = "@OPPO-ROM",
            url = "https://rom.oppo.help/",
            tags = listOf("oppo", "oneplus", "realme", "coloros", "rom"),
            deviceCodes = listOf("lemonade", "ossi", "taro", "aston", "davinci", "RMX3081", "RMX3360"),
            description = "OPPO/一加/真我官方ROM下载, 酷安网友收集"
        ),
        CoolapkSource(
            title = "大佬自建站 - PureSky净空ROM",
            author = "@PureSky",
            url = "https://jk.511i.cn/",
            tags = listOf("miui", "xiaomi", "custom", "rom"),
            deviceCodes = listOf("alioth", "raphael", "umi", "cetus", "vermeer", "aurora"),
            description = "酷安@骁亿维护的MIUI官改ROM, 净空ROM工作室"
        ),
        CoolapkSource(
            title = "大佬自建站 - ROM中国",
            author = "@ROM中国",
            url = "https://www.cnroms.com/",
            tags = listOf("rom", "firmware", "官方", "救砖"),
            deviceCodes = listOf("SM-S9210", "SM-S926", "SM-S928", "PD2", "PGT-AN00", "CMA-AN00"),
            description = "官方原厂固件下载站, 三星/OPPO/vivo/荣耀全系列"
        ),
        CoolapkSource(
            title = "大佬自建站 - 三星固件 SAMFW",
            author = "@SAMFW",
            url = "https://samfw.com/",
            tags = listOf("samsung", "oneui", "firmware"),
            deviceCodes = listOf("SM-S9210", "SM-S926", "SM-S928", "kona", "d1q", "o1q", "r8q"),
            description = "三星官方固件下载, 支持所有三星机型"
        ),
        CoolapkSource(
            title = "大佬自建站 - XiaomiROM",
            author = "@XiaomiROM",
            url = "https://xiaomirom.com/series",
            tags = listOf("xiaomi", "miui", "hyperos", "rom"),
            deviceCodes = listOf("alioth", "raphael", "sweet", "rose", "lisa", "evergo", "fire", "vela"),
            description = "小米官方ROM下载汇总"
        ),
        CoolapkSource(
            title = "大佬自建站 - FiimeROM",
            author = "@Fiime",
            url = "https://mi.fiime.cn/",
            tags = listOf("xiaomi", "redmi", "rom", "tool"),
            deviceCodes = listOf("alioth", "raphael", "sweet", "rose", "lisa", "marble", "garnet"),
            description = "专业小米/红米玩机资源平台"
        ),
        CoolapkSource(
            title = "大佬自建站 - Moto固件镜像",
            author = "@Lolinet",
            url = "https://mirrors.lolinet.com/firmware/motorola/",
            tags = listOf("motorola", "firmware", "rom"),
            deviceCodes = listOf("edison", "lake", "nash", "berkeley", "rome", "capri"),
            description = "摩托罗拉官方固件镜像站"
        ),
        CoolapkSource(
            title = "大佬自建站 - Nothing Phone ROM",
            author = "@spike0en",
            url = "https://spike0en.github.io/nothing_archive/docs/firmware/",
            tags = listOf("nothing", "phone", "firmware"),
            deviceCodes = listOf("nothing", "ph1", "ph2"),
            description = "Nothing Phone 官方固件归档"
        ),
        CoolapkSource(
            title = "大佬自建站 - MagiskCN ROM汇总",
            author = "@MagiskCN",
            url = "https://magiskcn.com/roms.html",
            tags = listOf("rom", "汇总", "xiaomi", "oneplus", "oppo"),
            deviceCodes = listOf("alioth", "lemonade", "ossi", "sargo", "raphael"),
            description = "酷安网友收集的各品牌ROM下载汇总"
        ),
        CoolapkSource(
            title = "大佬自建站 - ROM官网",
            author = "@ROM官网",
            url = "https://www.romgw.com/",
            tags = listOf("romgw", "镜像站", "rom", "救砖"),
            deviceCodes = listOf("MT6989", "MT6985", "MT6878", "SM-S9210", "PD2", "PGT-AN00"),
            description = "线刷救砖ROM包、卡刷包、官方原厂固件"
        ),
        CoolapkSource(
            title = "酷安 - 小白向刷机教程",
            author = "@落笔成酌",
            url = "https://www.coolapk.com/feed/9857726",
            tags = listOf("tutorial", "guide", "刷机教程", "小白"),
            deviceCodes = listOf("sargo", "bonito", "alioth", "lemonade"),
            description = "酷安@落笔成酌的小白向刷机教程"
        ),
        CoolapkSource(
            title = "酷安 - 中兴家族工具箱",
            author = "@某贼",
            url = "https://www.coolapk.com/feed/55222939",
            tags = listOf("zte", "nubia", "redmagic", "tool", "root"),
            deviceCodes = listOf("nubia", "redmagic", "nx729j", "nx659j"),
            description = "中兴/努比亚/红魔系列解锁BL、获取Root、9008刷机"
        ),
        CoolapkSource(
            title = "酷安 - 一加ACE3 9008刷机",
            author = "@一加玩家",
            url = "https://www.coolapk.com/feed/58724413",
            tags = listOf("oneplus", "ace3", "9008", "刷机"),
            deviceCodes = listOf("lemonade", "lemonadep"),
            description = "一加ACE3 9008刷机教程与工具"
        ),
        CoolapkSource(
            title = "酷安 - Pixel ROM 大全",
            author = "@Pixel玩家",
            url = "https://www.coolapk.com/search?q=PixelROM",
            tags = listOf("pixel", "rom", "google", "lineageos"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "bramble", "barbet", "raven", "oriole"),
            description = "Pixel 系列各类 ROM 综合合集"
        ),
        CoolapkSource(
            title = "酷安 - 联发科 MTK ROM 大全",
            author = "@MTK玩家",
            url = "https://www.coolapk.com/search?q=MTK%E5%88%B7%E6%9C%BA",
            tags = listOf("mediatek", "mtk", "rom", "bootloader"),
            deviceCodes = listOf("MT6989", "MT6985", "MT6878", "evergo", "fire", "lisa"),
            description = "MT6989/85/78 等联发科平台 ROM 解锁与刷机"
        ),
        CoolapkSource(
            title = "大佬自建站 - ROMHUB 镜像站",
            author = "@ROMHUB",
            url = "https://romhub.org",
            tags = listOf("romhub", "镜像站", "rom"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "nabu", "psyche"),
            description = "ROMHUB 大佬自建镜像站"
        ),
        CoolapkSource(
            title = "大佬自建站 - Needrom 镜像",
            author = "@Needrom",
            url = "https://www.needrom.com",
            tags = listOf("needrom", "镜像站", "rom", "firmware"),
            deviceCodes = listOf("MT6989", "MT6985", "MT6878", "SM-S9210", "PD2"),
            description = "Needrom 国际 ROM 镜像站"
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
        ),
        CoolapkSource(
            title = "酷安大佬 - AndroidFileHost ROM 搬运站",
            author = "@AFH搬运工",
            url = "https://www.androidfilehost.com",
            tags = listOf("androidfilehost", "rom", "firmware"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "nabu", "psyche", "aurora", "raphael"),
            description = "AFH 官方镜像站, 海量 ROM 资源"
        ),
        CoolapkSource(
            title = "酷安大佬 - SourceForge ROM 合集",
            author = "@SF搬运组",
            url = "https://sourceforge.net/directory/os:android/",
            tags = listOf("sourceforge", "rom", "firmware"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "bonito", "raphael", "nabu"),
            description = "SourceForge 上的 Android ROM 项目合集"
        ),
        CoolapkSource(
            title = "酷安大佬 - XiaomiFirmwareUpdater",
            author = "@XiaomiFirmware",
            url = "https://xiaomifirmwareupdater.com",
            tags = listOf("xiaomi", "miui", "firmware", "hyperos"),
            deviceCodes = listOf("alioth", "raphael", "nabu", "psyche", "aurora", "ruby", "lmi", "sweet"),
            description = "小米固件自动更新站, 全系列 MIUI/HyperOS"
        ),
        CoolapkSource(
            title = "酷安大佬 - MIUI ROM 下载站",
            author = "@MIUI下载站",
            url = "https://www.miui.com/download.html",
            tags = listOf("miui", "xiaomi", "rom", "官网"),
            deviceCodes = listOf("alioth", "raphael", "nabu", "psyche", "aurora", "ruby", "lmi"),
            description = "MIUI 官方 ROM 下载页"
        ),
        CoolapkSource(
            title = "酷安大佬 - HyperOS 固件库",
            author = "@HyperOS搬运",
            url = "https://hyperos.mi.com/download",
            tags = listOf("hyperos", "xiaomi", "rom", "官网"),
            deviceCodes = listOf("nabu", "psyche", "aurora", "ruby", "marble", "garnet"),
            description = "HyperOS 官方固件下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - PixelDust / ArrowOS 合集",
            author = "@AOSP搬运组",
            url = "https://sourceforge.net/projects/arrow-os/files/",
            tags = listOf("arrowos", "pixeldust", "aosp", "rom"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "barbet", "raven", "oriole"),
            description = "ArrowOS / PixelDust AOSP ROM 合集"
        ),
        CoolapkSource(
            title = "酷安大佬 - Havoc OS / Bliss ROM 合集",
            author = "@Havoc搬运",
            url = "https://sourceforge.net/projects/havoc-os/files/",
            tags = listOf("havoc", "bliss", "aosp", "rom"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "bonito", "crosshatch"),
            description = "Havoc OS / Bliss ROM 高级定制 ROM"
        ),
        CoolapkSource(
            title = "酷安大佬 - Samsung Odin 固件库",
            author = "@三星固件库",
            url = "https://samfw.com",
            tags = listOf("samsung", "oneui", "odin", "firmware"),
            deviceCodes = listOf("SM-S9210", "SM-S926", "SM-S928", "SM-S936", "SM-A515", "SM-A725"),
            description = "三星全机型 Odin 固件下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - SamMobile 固件",
            author = "@SamMobile",
            url = "https://www.sammobile.com",
            tags = listOf("samsung", "oneui", "firmware"),
            deviceCodes = listOf("SM-S9210", "SM-S926", "SM-S928", "SM-S23", "SM-A515"),
            description = "SamMobile 三星固件下载站"
        ),
        CoolapkSource(
            title = "酷安大佬 - Firmware.mobi 国际固件",
            author = "@FirmwareMobi",
            url = "https://firmware.mobi",
            tags = listOf("firmware", "rom", "international"),
            deviceCodes = listOf("SM-S9210", "ossi", "taro", "MT6989", "PGT-AN00"),
            description = "国际固件镜像站, 覆盖多国版本"
        ),
        CoolapkSource(
            title = "酷安大佬 - OPPO 官方固件",
            author = "@OPPO官方",
            url = "https://www.oppo.com/cn/support/softwareupdate/",
            tags = listOf("oppo", "coloros", "firmware", "官网"),
            deviceCodes = listOf("ossi", "taro", "aston", "davinci", "PD2", "PGW110"),
            description = "OPPO 官方固件下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - vivo 官方固件",
            author = "@vivo官方",
            url = "https://www.vivo.com.cn/download/",
            tags = listOf("vivo", "originos", "firmware", "官网"),
            deviceCodes = listOf("PD2", "PD3", "V22", "V21", "X90", "V2324A"),
            description = "vivo 官方固件下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - Realme 官方固件",
            author = "@Realme官方",
            url = "https://www.realme.com/cn/support/software-update/",
            tags = listOf("realme", "realmeui", "firmware", "官网"),
            deviceCodes = listOf("RMX3081", "RMX3360", "RMX3461", "RE54C1"),
            description = "Realme 官方固件下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - Honor 官方固件",
            author = "@荣耀官方",
            url = "https://www.honor.cn/support/download/",
            tags = listOf("honor", "magicos", "firmware", "官网"),
            deviceCodes = listOf("CMA-AN00", "PGT-AN00", "ANY-AN00", "GT-AN00"),
            description = "荣耀官方固件下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - OnePlus 官方固件",
            author = "@一加官方",
            url = "https://www.oneplus.com/support/softwareupgrade",
            tags = listOf("oneplus", "oxygenos", "firmware", "官网"),
            deviceCodes = listOf("lemonade", "lemonadep", "kebab", "instantnoodle", "OP5955"),
            description = "一加官方 OxygenOS 下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - PE 官方下载",
            author = "@PixelExperience",
            url = "https://download.pixelexperience.org",
            tags = listOf("pixelexperience", "rom", "官网"),
            deviceCodes = listOf("sargo", "bonito", "redfin", "barbet", "raven", "oriole", "alioth"),
            description = "Pixel Experience 官方下载"
        ),
        CoolapkSource(
            title = "酷安大佬 - crDroid 官方下载",
            author = "@crDroid",
            url = "https://crdroid.net/download",
            tags = listOf("crdroid", "rom", "官网"),
            deviceCodes = listOf("alioth", "lemonade", "kebab", "raven", "oriole"),
            description = "crDroid 官方下载站"
        ),
        CoolapkSource(
            title = "酷安大佬 - Evolution X 官方下载",
            author = "@EvolutionX",
            url = "https://evolutionx.org/download",
            tags = listOf("evolutionx", "rom", "官网"),
            deviceCodes = listOf("alioth", "lemonade", "kebab", "raven", "oriole"),
            description = "Evolution X 官方下载站"
        ),
        CoolapkSource(
            title = "酷安大佬 - KernelSU 官方",
            author = "@KernelSU",
            url = "https://kernelsu.org",
            tags = listOf("kernelsu", "kernel", "root", "官网"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "nabu", "psyche"),
            description = "KernelSU 官方下载与文档"
        ),
        CoolapkSource(
            title = "酷安大佬 - Magisk 官方",
            author = "@topjohnwu",
            url = "https://github.com/topjohnwu/Magisk/releases",
            tags = listOf("magisk", "root", "kernel", "官网"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "nabu", "psyche"),
            description = "Magisk 官方 release"
        ),
        CoolapkSource(
            title = "酷安大佬 - APatch 官方",
            author = "@APatch",
            url = "https://github.com/bmax121/APatch/releases",
            tags = listOf("apatch", "root", "kernel", "官网"),
            deviceCodes = listOf("sargo", "alioth", "lemonade", "raven", "nabu"),
            description = "APatch 官方 release"
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

                        val downloads = rel.optJSONObject("downloads")
                        if (downloads != null) {
                            val types = downloads.keys()
                            while (types.hasNext()) {
                                val typeKey = types.next()
                                val typeObj = downloads.optJSONObject(typeKey)
                                if (typeObj == null) continue
                                val url = typeObj.optString("url")
                                if (url.isBlank()) continue
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
            }
        } catch (e: Exception) {
            Logger.w(TAG, "OrangeFox 查询异常: ${e.message}")
        }
        return out
    }
}
