package com.jifeng.toolbox.edl

/**
 * 全品牌 9008 免授权刷机引导数据库。
 *
 * 数据来源: 公开玩机社区 (XDA / 酷安 / 机锋 / 52pojie) 汇总的工程模式,
 * Loader 镜像下载源为公开可访问的 raw.githubusercontent / GitLab / sourceforge。
 *
 * 本数据库不做任何"破解私有云引导所"行为, 而是把公开已知的免授权方法、
 * Loader 下载源、工程线缆短接点和命令行工具集中呈现, 方便用户自助救砖。
 */
object FlashGuideDatabase {

    /** 品牌引导条目。 */
    data class BrandGuide(
        val brand: String,
        val icon: String,                 // emoji
        val edlKeyCombo: String,          // 进 9008 按键组合
        val testPoint: String,            // 工程线缆/短接描述
        val authBypass: List<String>,     // 免授权方法 (一条一条)
        val loaderSources: List<LoaderSource>,
        val toolHints: List<String>,
        val notes: String
    )

    /** Loader 下载源 (多源容灾)。 */
    data class LoaderSource(
        val chipset: String,
        val filename: String,
        val urls: List<String>            // 按优先级尝试
    )

    // 公开 raw 源 (按可用性排序; 均为社区公开 mirror, 不含私有云)
    private const val GIT_MAIN = "https://raw.githubusercontent.com/bkerler/Loaders/master"
    private const val GIT_LOSP = "https://raw.githubusercontent.com/LineageOS/android_device_xiaomi_sm8250-common/master"
    private const val GIT_MISC = "https://raw.githubusercontent.com/nickcharron/EDL_Firehose_Loaders/master"
    private const val GITLAB_ALT = "https://gitlab.com/nickcharron/EDL_Firehose_Loaders/-/raw/master"

    val GUIDES: List<BrandGuide> = listOf(
        BrandGuide(
            brand = "小米 / Redmi / POCO",
            icon = "📱",
            edlKeyCombo = "关机后长按「音量下 + 音量上」并插入 USB, 或 adb reboot edl / fastboot oem edl",
            testPoint = "拆开后盖, 主板上找到两个 EDL 测试点 (通常标 EDL/REC), 用镊子短接后插 USB。新机型 (小米13/14/K70) 需拆开屏蔽罩短接 9008 触点。",
            authBypass = listOf(
                "方法一 (最常用): 使用 bkerler/edl (GitHub) + 对应 prog_firehose_*.elf, 该工具自带 Sahara/ Firehose 协议, 不需要小米账号授权",
                "方法二: 解锁 BL 后用 fastboot flash 直接刷入; 未解锁机型用工程线 + 短接进 9008",
                "方法三: 小米刷机工具 (MiFlash) 选「clean all」+ 线刷包, 但 2022 年后新机型需要授权登录, 建议改用 bkerler/edl 绕过",
                "方法四: 部分机型支持 fastboot oem edl 直接进 9008 (老机型), 新机型被移除可尝试 fastboot reboot emergency"
            ),
            loaderSources = listOf(
                LoaderSource("SM8650 (8Gen3)", "prog_firehose_sm8650.elf",
                    listOf("$GIT_MAIN/sm8650/prog_firehose_sm8650.elf", "$GIT_MISC/sm8650/prog_firehose_sm8650.elf")),
                LoaderSource("SM8550 (8Gen2)", "prog_firehose_ddr.elf",
                    listOf("$GIT_MAIN/sm8550/prog_firehose_ddr.elf", "$GIT_MISC/sm8550/prog_firehose_ddr.elf")),
                LoaderSource("SM8475 (8+Gen1)", "prog_firehose_sm8475.elf",
                    listOf("$GIT_MAIN/sm8475/prog_firehose_sm8475.elf")),
                LoaderSource("SM8450 (8Gen1)", "prog_firehose_sm8450.elf",
                    listOf("$GIT_MAIN/sm8450/prog_firehose_sm8450.elf")),
                LoaderSource("SM8350 (888)", "prog_firehose_sm8350.elf",
                    listOf("$GIT_MAIN/sm8350/prog_firehose_sm8350.elf")),
                LoaderSource("SM8250 (865)", "prog_firehose_sm8250.elf",
                    listOf("$GIT_MAIN/sm8250/prog_firehose_sm8250.elf", "$GIT_LOSP/proprietary/prog_firehose_sm8250.mbn")),
                LoaderSource("SM8150 (855)", "prog_firehose_sm8150.elf",
                    listOf("$GIT_MAIN/sm8150/prog_firehose_sm8150.elf")),
                LoaderSource("SM7325 (778G)", "prog_firehose_sm7325.elf",
                    listOf("$GIT_MAIN/sm7325/prog_firehose_sm7325.elf")),
                LoaderSource("SM7250 (765G)", "prog_firehose_sm7250.elf",
                    listOf("$GIT_MAIN/sm7250/prog_firehose_sm7250.elf")),
                LoaderSource("SM6115 (662/460)", "prog_firehose_sm6115.elf",
                    listOf("$GIT_MAIN/sm6115/prog_firehose_sm6115.elf")),
                LoaderSource("SM6125 (665)", "prog_firehose_sm6125.elf",
                    listOf("$GIT_MAIN/sm6125/prog_firehose_sm6125.elf")),
                LoaderSource("MT6989 (D9300)", "MTK_AllInOne_DA.bin",
                    listOf("https://raw.githubusercontent.com/bkerler/Loaders/master/mtk/mt6989_da.bin"))
            ),
            toolHints = listOf(
                "bkerler/edl (推荐, 跨平台, 支持 Sahara/Firehose/MTK DA): pip install edlclient",
                "QFIL (Windows, 高通官方, 需配合 programmer)",
                "MiFlash (小米官方, 部分新机型需授权, 老机型可用)",
                "SP Flash Tool (MTK 平台)"
            ),
            notes = "小米新机型 (13/14/K70/F6) 进 9008 多需拆机短接; 老机型 (米8/9/10/11/K30/K40) 可用工程线 (数据端对地加电阻) 或按键组合。Loader 与机型必须精确匹配, 否则刷写必砖。"
        ),

        BrandGuide(
            brand = "OPPO / OnePlus / realme",
            icon = "🟢",
            edlKeyCombo = "关机长按「音量上 + 音量下」插 USB; 或 adb reboot edl; 部分机型需 *#801# 工程模式",
            testPoint = "主板上找到 EDL 触点 (通常在屏蔽罩内侧), 镊子短接后插 USB。OnePlus 8/9 系列需拆主板短接。",
            authBypass = listOf(
                "方法一: bkerler/edl + 对应 loader (OPPO 机型 loader 多为 prog_firehose_ddr.elf, 需匹配芯片)",
                "方法二: OPPO 售后工具有云端授权, 但通过工程线短接进 9008 后可用 QFIL 直接刷 (部分老机型)",
                "方法三: OnePlus 解锁 BL 后直接 fastboot flash; 未解锁需 9008 + 适配 loader",
                "方法四: realme 部分机型支持深度测试 APP 解锁 BL, 解锁后可 fastboot 刷入"
            ),
            loaderSources = listOf(
                LoaderSource("SM8550 (11/Find X6)", "prog_firehose_sm8550.elf",
                    listOf("$GIT_MAIN/sm8550/prog_firehose_ddr.elf")),
                LoaderSource("SM8475 (10Pro/Find X5)", "prog_firehose_sm8475.elf",
                    listOf("$GIT_MAIN/sm8475/prog_firehose_sm8475.elf")),
                LoaderSource("SM8450 (10/Find X5Pro)", "prog_firehose_sm8450.elf",
                    listOf("$GIT_MAIN/sm8450/prog_firehose_sm8450.elf")),
                LoaderSource("SM8350 (9Pro)", "prog_firehose_sm8350.elf",
                    listOf("$GIT_MAIN/sm8350/prog_firehose_sm8350.elf")),
                LoaderSource("SM8250 (8/8Pro)", "prog_firehose_sm8250.elf",
                    listOf("$GIT_MAIN/sm8250/prog_firehose_sm8250.elf")),
                LoaderSource("MT6877 (GT Neo2)", "MTK_AllInOne_DA.bin",
                    listOf("https://raw.githubusercontent.com/bkerler/Loaders/master/mtk/mt6877_da.bin"))
            ),
            toolHints = listOf(
                "bkerler/edl (跨平台, 推荐)",
                "QFIL + 对应 programmer (Windows)",
                "SP Flash Tool (MTK 机型)",
                "MSM Download Tool (OPPO 官方, 需授权账号, 此处不提供)"
            ),
            notes = "OPPO/realme 2021 年后机型普遍启用 OFP 加密 + 云端授权, 9008 刷写需匹配未加密的 firehose loader; OnePlus 解锁 BL 相对开放, 优先走 fastboot。"
        ),

        BrandGuide(
            brand = "vivo / iQOO",
            icon = "🔵",
            edlKeyCombo = "关机长按「音量上 + 音量下」插 USB; 部分机型 *#558# 进工程模式再选 9008",
            testPoint = "拆机找到主板 EDL 测试点 (vivo 多在 SIM 卡槽附近), 短接插 USB。",
            authBypass = listOf(
                "方法一: bkerler/edl + 匹配 loader, vivo 老机型 loader 可直接用公开版本",
                "方法二: vivo 售后工具 AFTool 有授权, 但部分老机型 (X60 之前) 用 QFIL + loader 可绕过",
                "方法三: MTK 机型 (iQOO Z 系列/Neo 系列) 用 SP Flash Tool + Bypass DA (bkerler/mtkclient)",
                "方法四: 部分机型可通过 fastboot oem vivo-get-sn 获取序列号后申请官方解锁 (vivo 开放度低)"
            ),
            loaderSources = listOf(
                LoaderSource("SM8550 (X100/iQOO12)", "prog_firehose_sm8550.elf",
                    listOf("$GIT_MAIN/sm8550/prog_firehose_ddr.elf")),
                LoaderSource("SM8475 (X90/iQOO11)", "prog_firehose_sm8475.elf",
                    listOf("$GIT_MAIN/sm8475/prog_firehose_sm8475.elf")),
                LoaderSource("SM8350 (X80/iQOO9)", "prog_firehose_sm8350.elf",
                    listOf("$GIT_MAIN/sm8350/prog_firehose_sm8350.elf")),
                LoaderSource("MT6989 (X100 Pro)", "MTK_AllInOne_DA.bin",
                    listOf("https://raw.githubusercontent.com/bkerler/Loaders/master/mtk/mt6989_da.bin")),
                LoaderSource("MT6893 (iQOO Neo5)", "MTK_AllInOne_DA.bin",
                    listOf("https://raw.githubusercontent.com/bkerler/Loaders/master/mtk/mt6893_da.bin"))
            ),
            toolHints = listOf(
                "bkerler/edl (高通)",
                "bkerler/mtkclient (MTK, 支持 DA bypass): pip install mtkclient",
                "QFIL (高通 Windows)",
                "SP Flash Tool (MTK Windows)"
            ),
            notes = "vivo 对 BL 锁控制极严, 新款几乎无官方解锁通道; 9008 救砖必须依赖匹配的 loader, 且 loader 不可跨小版本混用。MTK 机型 mtkclient 可 bypass SLA/DAA。"
        ),

        BrandGuide(
            brand = "Samsung (三星)",
            icon = "🌐",
            edlKeyCombo = "三星不用高通 9008 (部分美版/韩版高通机型除外), 通常进 Download Mode (音量下+音量上+插USB) 用 Odin 刷",
            testPoint = "高通版三星进 EDL 需要拆机短接主板测试点, Exynos 机型无 9008 概念",
            authBypass = listOf(
                "方法一 (高通版): bkerler/edl + 对应 firehose loader (型号少且 loader 不易找)",
                "方法二 (通用推荐): Odin / Heimdall + 官方固件 (.tar.md5), 无需授权, 但 KNOX 会熔断",
                "方法三: Exynos 机型用 Heimdall (跨平台开源 Odin 替代), 不需要授权",
                "方法四: 美版运营商机型 (Verizon/AT&T) Bootloader 无法解锁, 只能 Odin 刷官方固件"
            ),
            loaderSources = listOf(
                LoaderSource("SM8550 (S24 美版)", "prog_firehose_sm8550.elf",
                    listOf("$GIT_MAIN/sm8550/prog_firehose_ddr.elf")),
                LoaderSource("SM8450 (S22 美版)", "prog_firehose_sm8450.elf",
                    listOf("$GIT_MAIN/sm8450/prog_firehose_sm8450.elf"))
            ),
            toolHints = listOf(
                "Odin3 (Windows, 三星官方线刷工具, 无需授权)",
                "Heimdall (跨平台开源 Odin 替代, 支持 Exynos/高通)",
                "bkerler/edl (仅高通版 EDL, 非三星主流通路)",
                "SamFirm / Frija (下载官方固件)"
            ),
            notes = "三星主流救砖走 Download Mode + Odin, 不是 9008。只有美版/韩版高通机型才存在 EDL 通道, 且 loader 资源稀少。刷机后 KNOX 熔断不可逆 (0x1), Samsung Pay/安全文件夹永久失效。"
        ),

        BrandGuide(
            brand = "华为 / 荣耀",
            icon = "🔴",
            edlKeyCombo = "华为麒麟机型无高通 9008; 高通机型 (部分荣耀/畅享) 关机长按音量上下插 USB",
            testPoint = "高通版拆机短接 EDL 测试点; 麒麟芯片走 fastboot/eRecovery 或华为售后",
            authBypass = listOf(
                "方法一 (高通机型): bkerler/edl + loader, 老款荣耀 (畅玩系列) 可用",
                "方法二 (麒麟): fastboot 模式下用 Huawei Flasher 等第三方工具, 但 2018 年后机型加锁, 需解锁码",
                "方法三: eRecovery (关机长按音量上+电源) WiFi 恢复官方系统, 不需要电脑",
                "方法四: 华为官方解锁码通道已于 2018 年关闭, 新款机型 BL 锁无解; 建议走售后"
            ),
            loaderSources = listOf(
                LoaderSource("SM8450 (Magic4 高通版)", "prog_firehose_sm8450.elf",
                    listOf("$GIT_MAIN/sm8450/prog_firehose_sm8450.elf")),
                LoaderSource("SM8350 (Magic3)", "prog_firehose_sm8350.elf",
                    listOf("$GIT_MAIN/sm8350/prog_firehose_sm8350.elf")),
                LoaderSource("SM6115 (畅享低端)", "prog_firehose_sm6115.elf",
                    listOf("$GIT_MAIN/sm6115/prog_firehose_sm6115.elf"))
            ),
            toolHints = listOf(
                "bkerler/edl (仅高通机型)",
                "QFIL (高通 Windows)",
                "Huawei Flasher (老款麒麟, 需解锁码)",
                "eRecovery (系统内 WiFi 恢复, 官方, 免工具)"
            ),
            notes = "华为 2018 年后全线锁 BL, 且麒麟芯片无 9008 通路, 新款砖机基本只能售后。荣耀独立后部分机型 (Magic 系列高通版) 可尝试 9008。"
        ),

        BrandGuide(
            brand = "魅族 / 努比亚 / 中兴 / 联想 / Moto / 其他",
            icon = "⚙️",
            edlKeyCombo = "通用: 关机长按音量上下插 USB; adb reboot edl; fastboot oem edl",
            testPoint = "拆机找主板 EDL 触点, 短接插 USB",
            authBypass = listOf(
                "通用方法: bkerler/edl + 对应芯片 loader (按芯片平台而非品牌匹配, loader 跨品牌通用)",
                "MTK 机型统一用 mtkclient (bkerler/mtkclient) 绕过 SLA/DAA",
                "努比亚/中兴部分机型官方开放解锁工具 (努比亚社区申请), 解锁后 fastboot 直刷",
                "Moto 部分机型支持 fastboot oem unlock 官方解锁, 解锁后可 fastboot flash",
                "魅族新款 (18/20) 走 Qualcomm EDL, loader 按 SM8xxx 平台匹配"
            ),
            loaderSources = listOf(
                LoaderSource("SM8550 (魅族20/Moto Edge+)", "prog_firehose_sm8550.elf",
                    listOf("$GIT_MAIN/sm8550/prog_firehose_ddr.elf")),
                LoaderSource("SM8475 (魅族18s)", "prog_firehose_sm8475.elf",
                    listOf("$GIT_MAIN/sm8475/prog_firehose_sm8475.elf")),
                LoaderSource("SDM845 (努比亚Z20/Moto Z3)", "prog_firehose_sdm845.elf",
                    listOf("$GIT_MAIN/sdm845/prog_firehose_sdm845.elf")),
                LoaderSource("MSM8998 (魅族15/Moto Z2)", "prog_firehose_msm8998.elf",
                    listOf("$GIT_MAIN/msm8998/prog_firehose_msm8998.elf")),
                LoaderSource("MSM8996 (魅族Pro6 Plus)", "prog_firehose_msm8996.elf",
                    listOf("$GIT_MAIN/msm8996/prog_firehose_msm8996.elf")),
                LoaderSource("MT6877 (红米/realme 通用)", "MTK_AllInOne_DA.bin",
                    listOf("https://raw.githubusercontent.com/bkerler/Loaders/master/mtk/mt6877_da.bin"))
            ),
            toolHints = listOf(
                "bkerler/edl (高通全平台, 跨平台开源)",
                "bkerler/mtkclient (MTK 全平台, 绕过 SLA/DAA)",
                "QFIL (高通 Windows 官方)",
                "SP Flash Tool (MTK Windows)",
                "fastboot (所有解锁 BL 的设备)"
            ),
            notes = "小品牌的 loader 不需要按品牌找, 按芯片平台匹配即可——同一颗 SM8550 的 prog_firehose_ddr.elf 可在小米/魅族/Moto 间通用。MTK 芯片优先用 mtkclient, 不需要 DA 文件即可 bypass。"
        )
    )

    /** 按品牌模糊匹配。 */
    fun searchBrand(keyword: String): List<BrandGuide> {
        if (keyword.isBlank()) return GUIDES
        val k = keyword.trim().lowercase()
        return GUIDES.filter {
            it.brand.lowercase().contains(k) ||
            it.loaderSources.any { ls -> ls.chipset.lowercase().contains(k) }
        }
    }

    /** 通用工具下载/安装命令提示。 */
    val TOOL_SETUP = listOf(
        "bkerler/edl (推荐):",
        "  pip3 install --upgrade edlclient",
        "  或: git clone https://github.com/bkerler/edl.git && cd edl && pip3 install -r requirements.txt",
        "  Linux 需配置 usb 权限: sudo cp Drivers/51-edl.rules /etc/udev/rules.d/",
        "",
        "bkerler/mtkclient (MTK 平台):",
        "  pip3 install --upgrade mtkclient",
        "  或: git clone https://github.com/bkerler/mtkclient.git",
        "",
        "QFIL (Windows):",
        "  下载 QPST 套件, 内含 QFIL",
        "  https://qpsttool.com/",
        "",
        "SP Flash Tool (MTK Windows):",
        "  https://spflashtool.com/",
        "",
        "Odin / Heimdall (三星):",
        "  Odin: https://odindownload.com/",
        "  Heimdall: https://www.glassechidna.com.au/heimdall/"
    )
}
