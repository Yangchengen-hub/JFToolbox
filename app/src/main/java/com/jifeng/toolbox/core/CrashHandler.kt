package com.jifeng.toolbox.core

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器 —— 稳定性保障核心。
 *
 * - 安装后接管所有线程（含子线程）的未捕获异常。
 * - 将崩溃堆栈写入持久化日志文件（filesDir/crash/）。
 * - 同步推入 [Logger] 供可视化终端查看。
 * - 优雅退出当前进程，避免静默崩溃。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_DIR = "crash"
    private const val MAX_CRASH_FILES = 10

    private lateinit var app: Application
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(application: Application) {
        app = application
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        Logger.i(TAG, "全局崩溃处理器已安装")
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stackTrace = formatStackTrace(e)
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val threadInfo = "Thread: ${t.name} (id=${t.id})"

        val report = buildString {
            appendLine("===== 极风工具箱 崩溃报告 =====")
            appendLine("时间: $time")
            appendLine(threadInfo)
            appendLine("进程: ${Process.myPid()}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("-------------------------------")
            appendLine(stackTrace)
            appendLine("===============================")
        }

        // 1. 推入可视化日志
        Logger.e(TAG, "未捕获异常 @${t.name}: ${e.javaClass.simpleName}: ${e.message}")
        Logger.e(TAG, report)

        // 2. 持久化到文件
        try {
            writeCrashFile(time, report)
        } catch (_: Exception) { /* 防止写文件本身再次抛出 */ }

        // 3. 交给系统默认处理器（会弹出“应用已停止”对话框并终止进程）
        previousHandler?.uncaughtException(t, e)
    }

    private fun formatStackTrace(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun writeCrashFile(time: String, report: String) {
        val dir = File(app.filesDir, CRASH_DIR)
        if (!dir.exists()) dir.mkdirs()

        // 清理过期崩溃日志
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_CRASH_FILES)
            ?.forEach { it.delete() }

        val file = File(dir, "crash_$time.txt")
        file.writeText(report)
    }

    /** 返回所有崩溃报告文件（新→旧）。 */
    fun listCrashFiles(): List<File> =
        File(app.filesDir, CRASH_DIR)
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** 读取指定崩溃报告内容。 */
    fun readCrashFile(file: File): String =
        if (file.exists()) file.readText() else ""

    /** 清除所有崩溃报告。 */
    fun clearCrashFiles() {
        File(app.filesDir, CRASH_DIR).listFiles()?.forEach { it.delete() }
    }

    /** 获取最近一次崩溃的简要摘要（供首页健康检查展示）。 */
    fun lastCrashSummary(): String? {
        val file = listCrashFiles().firstOrNull() ?: return null
        val text = readCrashFile(file)
        val timeLine = text.lineSequence().firstOrNull { it.startsWith("时间:") }
        val exLine = text.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }
        return "${timeLine ?: file.name} | ${exLine ?: "未知异常"}"
    }
}
