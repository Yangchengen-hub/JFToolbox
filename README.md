# 极风工具箱 (JFToolbox)

安卓刷机/调试/玩机全能工具箱 MVP 版本

## 功能模块

- ✅ 免责声明启动页
- ✅ 液态玻璃 UI（深浅色跟随系统）
- ✅ 首页设备信息 + 分区列表 + 热插拔识别
- ✅ Fastboot 刷 ZIP/IMG + 校验 + 日志终端
- ✅ 超级终端（ADB shell）
- ✅ 全线程下载器（多线程分片）
- ✅ 内置浏览器（WebView）
- ✅ 无线调试（TCP/IP 5555）
- ✅ 智能冻结组件（pm disable / pm uninstall）
- ✅ 关于页 + 联系方式胶囊按钮

## 技术栈

- Kotlin + Material Design 3
- Gradle 8.7 + Android Gradle Plugin 8.5.2
- minSdk 24 (Android 7.0) | targetSdk 34
- 基于 ADB over USB-OTG 协议

## 构建

```bash
# 需要 JDK 17 + Android SDK (API 34)
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 作者

极风工作室 · 诺言

## 版本

0.1.0-mvp

## 免责声明

刷机有风险，操作需谨慎。本软件仅供技术研究。
