# 极风工具箱 | JFToolbox
<p align="center">
  <strong>🦊 免 Root · OTG 直连 · 全安卓刷机/调试/玩机全能工具箱</strong>
</p>
<p align="center">
  <strong>🦊 No-Root · OTG Direct · All-in-One Android Flashing/Debug/Power-User Toolkit</strong>
</p>

---

## 📱 简介 | Introduction

**中文：**
极风工具箱是一款面向安卓发烧友、刷机爱好者和开发者的全能工具箱应用。通过 USB-OTG 线直连手机，无需 Root 即可实现 ADB 调试、Fastboot 刷机、9008 救砖等高级操作。采用澎湃OS 4 柔光玻璃 UI 设计风格，搭载「生命感动效」动画引擎，提供 25+ 功能模块。

**English:**
JFToolbox is an all-in-one toolkit designed for Android enthusiasts, flashaholics, and developers. By connecting your phone directly via USB-OTG cable, you can perform advanced operations like ADB debugging, Fastboot flashing, and 9008 EDL rescue — all without root. Featuring a HyperOS 4 Frosted Glass UI design and "Life in Motion" animation engine, it provides 25+ functional modules.

---

## ✨ 核心特性 | Key Features

| 中文 | English |
|------|---------|
| 免 Root OTG 直连安卓设备 | No-Root OTG direct connection to Android devices |
| 完整 ADB 协议栈 (CNXN/AUTH/OPEN/WRTE) | Full ADB protocol stack (CNXN/AUTH/OPEN/WRTE) |
| Fastboot 刷机 (ZIP/IMG, 分区校验) | Fastboot flashing (ZIP/IMG, partition validation) |
| 9008 EDL 救砖模式 | 9008 EDL brick rescue mode |
| RSA 密钥管理与设备授权 | RSA key management & device authorization |
| 澎湃OS 4 柔光玻璃 UI + 生命感动效 | HyperOS 4 Frosted Glass UI + Life in Motion animations |
| 多线程分片下载器 | Multi-threaded segmented downloader |
| 内置 WebView 浏览器 | Built-in WebView browser |
| 多语言终端 (Shell/Python/JS/Lua/C++/AI) | Multi-language terminal (Shell/Python/JS/Lua/C++/AI) |
| SSH 远程客户端 | SSH remote client |
| APK 批量安装器 | APK batch installer |
| 智能组件冻结/解冻 | Smart component freeze/unfreeze |
| 屏幕镜像投屏 | Screen mirroring |
| 加密解密器 | Encryption/Decryption tool |
| 固件搜索与下载 | Firmware search & download |
| 通知栏刷机进度 | Notification bar flashing progress |
| 深色/浅色主题跟随系统 | Dark/Light theme following system |

---

## 📦 下载安装 | Download & Install

### 🚀 最新版本 | Latest Version

<p align="center">
  <a href="https://github.com/Yangchengen-hub/JFToolbox/releases/latest/download/JFToolbox-v3.0.0.apk" target="_blank">
    <img src="https://img.shields.io/badge/下载%20APK%20v3.0.0-%232979FF.svg?style=for-the-badge&logo=android&logoColor=white" alt="下载 APK" />
  </a>
  <a href="https://github.com/Yangchengen-hub/JFToolbox/releases" target="_blank">
    <img src="https://img.shields.io/github/v/release/Yangchengen-hub/JFToolbox?include_prereleases&style=for-the-badge" alt="Release" />
  </a>
  <a href="https://github.com/Yangchengen-hub/JFToolbox/actions" target="_blank">
    <img src="https://img.shields.io/github/actions/workflow/status/Yangchengen-hub/JFToolbox/build-apk.yml?branch=main&style=for-the-badge" alt="Build Status" />
  </a>
</p>

### 方式一：直接下载 APK | Option 1: Direct APK Download

**最新版下载（推荐）：**
- [📥 JFToolbox-v3.0.0.apk](https://github.com/Yangchengen-hub/JFToolbox/releases/latest/download/JFToolbox-v3.0.0.apk)

**所有历史版本：**
- [📋 Releases 页面](https://github.com/Yangchengen-hub/JFToolbox/releases)

### 方式二：自行编译 | Option 2: Build from Source

```bash
# 克隆仓库 | Clone the repository
git clone https://github.com/Yangchengen-hub/JFToolbox.git
cd JFToolbox

# 需要 JDK 17 + Android SDK (API 34)
# Requires JDK 17 + Android SDK (API 34)
./gradlew assembleDebug

# APK 输出路径 | APK output path
# app/build/outputs/apk/debug/app-debug.apk
```

### 安装说明 | Installation Notes

**中文：**
1. 下载 APK 文件到手机
2. 允许「来自未知来源」的应用安装
3. 安装后打开，授予所需权限（存储、通知、USB）
4. 使用 USB-OTG 线连接手机即可开始

**English:**
1. Download the APK file to your phone
2. Enable "Install from unknown sources"
3. Open the app and grant required permissions (Storage, Notifications, USB)
4. Connect your phone using a USB-OTG cable to get started

### 📊 版本信息 | Version Info

| 项目 | 详情 | Item | Details |
|------|------|------|---------|
| 当前版本 | v3.0.0 | Current Version | v3.0.0 |
| 版本号 | 7 | Version Code | 7 |
| 支持架构 | arm64-v8a / armeabi-v7a / x86_64 | Architectures | arm64-v8a / armeabi-v7a / x86_64 |
| 最小系统 | Android 7.0 (API 24) | Min SDK | Android 7.0 (API 24) |
| 目标系统 | Android 14 (API 34) | Target SDK | Android 14 (API 34) |

---

## 🎨 设计语言 | Design Language

**澎湃OS 4 柔光玻璃 (HyperOS 4 Frosted Glass)**

v3.0.0 全面采用小米澎湃OS 4 的「柔光玻璃」设计语言：
- **小米蓝渐变品牌色** (#2979FF → #448AFF)
- **柔光玻璃材质**: 半透明 + 高斯模糊 + 边缘高光线条
- **「生命感动效」曲线**: emphasizedDecelerate / emphasizedAccelerate
- **弹簧系统**: softBounce / crispBounce / floatSpring
- **AI 感色辅助色板**: 卡片根据内容动态调色
- **2.5D 图标效果**: 多层阴影 + 微视差
- **渐变网格背景**: 模拟壁纸色彩渗透感

---

## 🛠️ 功能模块 | Feature Modules

### 🔧 刷机与调试 | Flashing & Debug

| 模块 | 说明 | Module | Description |
|------|------|--------|-------------|
| Fastboot 刷机 | 支持 ZIP 整包/IMG 单分区刷入，自动校验 | Fastboot Flash | Support ZIP full-package/IMG single-partition flashing with auto-validation |
| 9008 救砖 | EDL 模式 Firehose 协议，GPT 分区表检测 | 9008 Rescue | EDL mode Firehose protocol, GPT partition table detection |
| ADB 超级终端 | 完整 ADB Shell，支持多设备切换 | ADB Terminal | Full ADB Shell with multi-device switching |
| 无线调试 | TCP/IP 5555 端口无线连接 | Wireless Debug | TCP/IP 5555 port wireless connection |

### 📲 设备管理 | Device Management

| 模块 | 说明 | Module | Description |
|------|------|--------|-------------|
| 设备信息 | CPU/GPU/内存/存储/电池/屏幕全信息 | Device Info | CPU/GPU/RAM/Storage/Battery/Screen full info |
| APK 安装器 | 本地/远程批量安装 APK | APK Installer | Local/Remote batch APK installation |
| 组件冻结 | pm disable/uninstall 智能管理 | Component Freeze | pm disable/uninstall smart management |
| 屏幕镜像 | 实时投屏与远程控制 | Screen Mirror | Real-time mirroring & remote control |

### 🔨 实用工具 | Utility Tools

| 模块 | 说明 | Module | Description |
|------|------|--------|-------------|
| 多语言终端 | Shell/Python/JS/Lua/C++/AI-LLM | Multi-Language Terminal | Shell/Python/JS/Lua/C++/AI-LLM |
| SSH 客户端 | JSch 远程 SSH 连接 | SSH Client | JSch remote SSH connection |
| 内置浏览器 | WebView 全功能浏览器 | Built-in Browser | WebView full-featured browser |
| 多线程下载器 | OkHttp Range 分片下载 | Multi-Thread Downloader | OkHttp Range segmented download |
| 固件搜索 | GitHub 开源 ROM 搜索 | Firmware Search | GitHub open-source ROM search |
| 加密解密器 | AES/RSA/Base64 编解码 | Crypto Tool | AES/RSA/Base64 encode/decode |

---

## 🔒 安全保障 | Safety Guarantees

**中文：**
- ✅ 分区白名单机制，防止误刷关键分区
- ✅ 镜像文件 Magic Number 校验
- ✅ GPT 分区表砖机检测（分区数=0 时警告）
- ✅ 路径遍历攻击防护
- ✅ 完整免责声明与用户确认
- ✅ 刷机进度通知栏实时显示

**English:**
- ✅ Partition whitelist mechanism to prevent flashing critical partitions
- ✅ Image file magic number validation
- ✅ GPT partition table brick detection (warning when partition count = 0)
- ✅ Path traversal attack protection
- ✅ Comprehensive disclaimer with user confirmation
- ✅ Real-time flashing progress in notification bar

---

## 📋 技术规格 | Technical Specs

| 项目 | 规格 | Item | Spec |
|------|------|------|------|
| 语言 | Kotlin | Language | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 | UI Framework | Jetpack Compose + Material 3 |
| 设计语言 | 澎湃OS 4 柔光玻璃 | Design Language | HyperOS 4 Frosted Glass |
| 最低版本 | Android 7.0 (API 24) | Min SDK | Android 7.0 (API 24) |
| 目标版本 | Android 14 (API 34) | Target SDK | Android 14 (API 34) |
| 构建工具 | Gradle 8.14 | Build Tool | Gradle 8.14 |
| 协议 | ADB-over-USB / Fastboot / EDL | Protocol | ADB-over-USB / Fastboot / EDL |

---

## 📸 截图预览 | Screenshots

> 截图将在后续版本中添加 | Screenshots will be added in future versions

---

## ⚠️ 免责声明 | Disclaimer

**中文：**
本软件（极风工具箱 / JFToolbox）仅供技术学习与研究目的使用。使用者需遵守以下条款：
1. **风险自担：** 刷机、解锁 Bootloader、修改系统分区等操作可能导致设备变砖、数据丢失、失去保修等后果，所有风险由使用者自行承担。
2. **版权尊重：** 使用本工具下载或刷入的任何固件、ROM、镜像文件，其版权归原版权所有者所有。使用者应确保拥有合法使用权。
3. **合法使用：** 使用者应确保遵守所在地区的法律法规，不得将本工具用于任何非法用途。
4. **数据备份：** 使用本工具前，请务必备份所有重要数据。本工具不对任何数据损失负责。
5. **免责声明接受：** 首次启动应用时，使用者需明确同意本免责声明方可使用。

本软件开发者（极风工作室）不对因使用本软件而导致的任何直接或间接损失承担责任。

**English:**
This software (JFToolbox) is provided for technical learning and research purposes only. Users must comply with the following terms:
1. **Risk Assumption:** Flashing, unlocking bootloader, modifying system partitions, and other operations may result in device bricking, data loss, loss of warranty, etc. All risks are borne by the user.
2. **Copyright Respect:** Any firmware, ROM, or image files downloaded or flashed using this tool are owned by their respective copyright holders. Users must ensure they have legal usage rights.
3. **Legal Use:** Users must comply with local laws and regulations and must not use this tool for any illegal purposes.
4. **Data Backup:** Please back up all important data before using this tool. This tool is not responsible for any data loss.
5. **Disclaimer Acceptance:** Upon first launch, users must explicitly agree to this disclaimer before using the application.

The developer of this software (JFToolbox Studio) shall not be liable for any direct or indirect losses caused by the use of this software.

---

## 🙏 鸣谢 | Acknowledgments

**中文：** 本项目借鉴了以下开源项目的技术与思路：
**English:** This project draws on the technology and ideas of the following open-source projects:

- [ADB Protocol Specification](https://source.android.com/docs/setup/start/adb) — Android Debug Bridge Protocol
- [Fastboot Protocol](https://android.googlesource.com/platform/system/core/+/master/fastboot/) — Android Fastboot Protocol
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI Toolkit
- [Material Design 3](https://m3.material.io/) — Google's Material Design System
- [OkHttp](https://github.com/square/okhttp) — HTTP+HTTP/2 client for Android
- [JSch](http://www.jcraft.com/jsch/) — Java Secure Channel implementation

---

## 📞 联系方式 | Contact

| 平台 | 链接 | Platform | Link |
|------|------|----------|------|
| QQ 群 | [点击加入](https://qm.qq.com/q/xxxxxx) | QQ Group | [Join](https://qm.qq.com/q/xxxxxx) |
| Telegram | [@jifengtoolbox](https://t.me/jifengtoolbox) | Telegram | [@jifengtoolbox](https://t.me/jifengtoolbox) |
| GitHub | [Yangchengen-hub/JFToolbox](https://github.com/Yangchengen-hub/JFToolbox) | GitHub | [Yangchengen-hub/JFToolbox](https://github.com/Yangchengen-hub/JFToolbox) |

---

## 📄 开源协议 | License

本项目采用 MIT 协议 | This project is licensed under the MIT License.

```
MIT License

Copyright (c) 2026 极风工作室 (JFToolbox Studio)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files...
```

---

## 📌 版本信息 | Version Info

**当前版本 | Current Version:** `v3.0.0`

**更新日志 | Changelog:**
- v3.0.0 — 澎湃OS 4 柔光玻璃大改版 | HyperOS 4 Frosted Glass Redesign
  - 🎨 品牌色升级为小米蓝渐变 (#2979FF → #448AFF) | Brand color upgraded to Xiaomi Blue gradient
  - 🪟 全面柔光玻璃材质 (半透明 + 高斯模糊 + 边缘高光) | Full frosted glass material
  - ✨ 「生命感动效」曲线引擎 | "Life in Motion" animation curve engine
  - 🎯 AI 感色辅助色板 | AI sense color palette
  - 📐 2.5D 图标效果 + 微视差 | 2.5D icon effect with parallax
  - 🌊 渐变网格壁纸背景 | Gradient mesh wallpaper background
  - 📱 功能栅格改为 2 列大卡片 | Feature grid changed to 2-column large cards
  - 💊 重启按钮改为玻璃胶囊样式 | Reboot buttons changed to glass capsule style
- v2.3.0 — 正式发布 | Official Release
  - ✅ 25+ 功能模块全部就绪 | All 25+ feature modules ready
  - ✅ 液态玻璃 UI + HyperOS 动画 | Liquid Glass UI + HyperOS animations
  - ✅ 免 Root OTG 直连 | No-Root OTG direct connection
  - ✅ ADB/Fastboot/9008 全协议支持 | Full ADB/Fastboot/9008 protocol support

---

<p align="center">
  <strong>极风工作室 © 2026</strong><br>
  <strong>JFToolbox Studio © 2026</strong>
</p>
