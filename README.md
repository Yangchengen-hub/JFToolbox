# 极风工具箱 (JFToolbox) — MVP

## 快速构建 APK（需要本地有 Android SDK）

### 前置条件
- JDK 17+
- Android SDK Platform 34 已安装
- 设置 `ANDROID_HOME` 环境变量

### 一键构建
```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

产物位置：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到手机
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 项目结构
```
JFToolbox/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/wrapper/        ← Gradle 8.7 wrapper
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/jifeng/toolbox/
        │   ├── JFToolboxApp.kt
        │   ├── adb/AdbManager.kt + AdbDaemonService.kt
        │   ├── core/DeviceDetector.kt + DeviceInfo.kt + Logger.kt + ThemeManager.kt
        │   ├── usb/UsbPermissionReceiver.kt
        │   └── ui/
        │       ├── disclaimer/DisclaimerActivity.kt
        │       ├── main/MainActivity.kt + PartitionAdapter.kt
        │       ├── flash/FlashActivity.kt
        │       ├── terminal/TerminalActivity.kt
        │       ├── downloader/DownloaderActivity.kt
        │       ├── browser/BrowserActivity.kt
        │       ├── about/AboutActivity.kt
        │       ├── wireless/WirelessDebugActivity.kt
        │       └── freeze/FreezeActivity.kt
        └── res/
            ├── layout/  (11 个 XML)
            ├── drawable/ (图标 + 玻璃背景)
            ├── values/ + values-night/ (主题)
            └── mipmap-anydpi-v26/ (自适应图标)
```

## MVP 已实现
- ✅ 免责声明启动页（必须同意才能进入）
- ✅ 液态玻璃 UI（浅色/深色自动跟随系统）
- ✅ 首页设备信息展示 + 分区列表
- ✅ 极速热插拔识别（USB 设备插拔监听）
- ✅ 快捷重启（系统/Recovery/Bootloader/9008）
- ✅ Fastboot 刷 ZIP / 单分区 IMG（校验 + 刷写 + 日志终端）
- ✅ 超级终端（执行 ADB shell 命令）
- ✅ 全线程下载器（HTTP 多线程分片）
- ✅ 内置浏览器（WebView）
- ✅ 无线调试（TCP/IP 5555 开启 + 连接）
- ✅ 智能冻结（预置组件清单一键禁用）
- ✅ 关于页（工作室 + 联系方式胶囊按钮）
- ✅ ADB 后台守护服务
- ✅ 日志收集器（全局可订阅）

## 需要你提供 / 后续补齐
- ⚠️ 9008 loader 资源（各品牌芯片引导文件）—— 这是刷写 EDL 模式的核心
- ⚠️ 加密解密器需要明确算法支持范围
- ⚠️ 超级终端跑 LLM 需要选定模型 + 量化方案

## 工作室
极风工作室 · 作者：诺言

---
生成时间：2026-07-23
版本：0.1.0-mvp
