package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 超级终端执行引擎 —— 统一调度多语言运行环境。
 *
 * 支持的运行模式:
 * - shell: ADB shell 直接执行 (Android 原生 shell)
 * - python: 被控端 Python3 解释器
 * - javascript: 被控端 Node.js
 * - lua: 被控端 Lua 解释器
 * - c/c++: 推送源码 → 设备端 gcc/clang 编译 → 运行
 * - ai-llm: 本地 LLM 推理 (ollama / llama.cpp / transformers)
 * - ssh: 远程 SSH 主机执行
 */
object TerminalEngine {

    private const val TAG = "TerminalEngine"

    /** 运行环境标识。 */
    val LANGUAGES = listOf("shell", "python", "javascript", "lua", "c/c++", "ai-llm", "ssh")

    /** 执行结果。 */
    data class ExecResult(
        val output: String,
        val isError: Boolean = false,
        val durationMs: Long = 0
    )

    /** SSH 会话状态 (维持跨命令连接)。 */
    private var sshClient: SshClient? = null
    private var sshConfig: SshClient.SshConfig? = null

    /** LLM 环境缓存。 */
    private var llmEnv: LocalLlmRunner.LlmEnvironment? = null

    /**
     * 配置 SSH 连接。
     */
    fun configureSsh(config: SshClient.SshConfig) {
        sshConfig = config
    }

    /**
     * 执行命令。
     * @param lang 运行环境
     * @param cmd 命令/代码/prompt
     */
    suspend fun execute(lang: String, cmd: String): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val serial = AdbManager.currentSerial ?: ""

        if (serial.isBlank() && lang != "ssh") {
            return@withContext ExecResult("错误: 无设备连接 (ADB 未连接)", isError = true,
                durationMs = System.currentTimeMillis() - start)
        }

        val output = try {
            when (lang) {
                "shell" -> executeShell(serial, cmd)
                "python" -> executePython(serial, cmd)
                "javascript" -> executeJavaScript(serial, cmd)
                "lua" -> executeLua(serial, cmd)
                "c/c++" -> executeCpp(serial, cmd)
                "ai-llm" -> executeLlm(serial, cmd)
                "ssh" -> executeSsh(cmd)
                else -> "未知运行环境: $lang"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "执行异常 [$lang]: ${e.message}")
            "错误: ${e.message}"
        }

        ExecResult(output, durationMs = System.currentTimeMillis() - start)
    }

    // ---------- Shell ----------

    private fun executeShell(serial: String, cmd: String): String {
        return AdbManager.shell(serial, cmd) ?: "(无输出或执行失败)"
    }

    // ---------- Python ----------

    private fun executePython(serial: String, code: String): String {
        val adb = AdbManager.instance
        // 检测 python3
        val pyCheck = adb.shell(serial, "command -v python3 2>/dev/null").orEmpty().trim()
        if (pyCheck.isBlank()) {
            return "❌ 被控设备未安装 Python3。\n提示: 在 Termux 中执行 pkg install python"
        }
        // 多行代码: 写入临时文件执行
        if (code.contains("\n") || code.length > 200) {
            return executeScriptFile(serial, code, ".py", "python3")
        }
        // 单行: -c 模式
        val escaped = code.replace("'", "'\\''")
        return adb.shell(serial, "python3 -c '$escaped' 2>&1") ?: "(执行失败)"
    }

    // ---------- JavaScript ----------

    private fun executeJavaScript(serial: String, code: String): String {
        val adb = AdbManager.instance
        val nodeCheck = adb.shell(serial, "command -v node 2>/dev/null").orEmpty().trim()
        if (nodeCheck.isBlank()) {
            return "❌ 被控设备未安装 Node.js。\n提示: 在 Termux 中执行 pkg install nodejs"
        }
        if (code.contains("\n") || code.length > 200) {
            return executeScriptFile(serial, code, ".js", "node")
        }
        val escaped = code.replace("'", "'\\''")
        return adb.shell(serial, "node -e '$escaped' 2>&1") ?: "(执行失败)"
    }

    // ---------- Lua ----------

    private fun executeLua(serial: String, code: String): String {
        val adb = AdbManager.instance
        val luaCheck = adb.shell(serial, "command -v lua 2>/dev/null").orEmpty().trim()
        if (luaCheck.isBlank()) {
            return "❌ 被控设备未安装 Lua。\n提示: 在 Termux 中执行 pkg install lua"
        }
        if (code.contains("\n")) {
            return executeScriptFile(serial, code, ".lua", "lua")
        }
        val escaped = code.replace("'", "'\\''")
        return adb.shell(serial, "lua -e '$escaped' 2>&1") ?: "(执行失败)"
    }

    // ---------- C/C++ ----------

    private fun executeCpp(serial: String, code: String): String {
        val adb = AdbManager.instance
        // 检测编译器
        val gccCheck = adb.shell(serial, "command -v gcc 2>/dev/null").orEmpty().trim()
        val clangCheck = adb.shell(serial, "command -v clang 2>/dev/null").orEmpty().trim()
        val compiler = when {
            gccCheck.isNotBlank() -> "gcc"
            clangCheck.isNotBlank() -> "clang"
            else -> ""
        }
        if (compiler.isBlank()) {
            return "❌ 被控设备未安装 C/C++ 编译器 (gcc/clang)。\n" +
                "提示: 在 Termux 中执行 pkg install clang"
        }

        // 推送源码到设备
        val remoteSrc = "/data/local/tmp/jf_${System.currentTimeMillis()}.c"
        val localTmp = File.createTempFile("jf_src", ".c")
        try {
            localTmp.writeText(code)
            if (!adb.push(serial, localTmp.absolutePath, remoteSrc)) {
                return "❌ 推送源码失败"
            }
        } finally {
            localTmp.delete()
        }

        // 编译
        val remoteBin = remoteSrc.removeSuffix(".c")
        val compileCmd = "$compiler $remoteSrc -o $remoteBin 2>&1"
        val compileOut = adb.shell(serial, compileCmd).orEmpty()
        if (compileOut.contains("error", ignoreCase = true)) {
            adb.shell(serial, "rm -f $remoteSrc 2>/dev/null")
            return "编译失败:\n$compileOut"
        }

        // 运行
        val runOut = adb.shell(serial, "$remoteBin 2>&1") ?: "(运行失败)"

        // 清理
        adb.shell(serial, "rm -f $remoteSrc $remoteBin 2>/dev/null")

        return runOut
    }

    // ---------- LLM ----------

    private suspend fun executeLlm(serial: String, prompt: String): String {
        // 首次使用时检测环境
        if (llmEnv == null || llmEnv?.backend == LocalLlmRunner.LlmBackend.NONE) {
            llmEnv = LocalLlmRunner.detect(serial)
        }
        val env = llmEnv ?: return "❌ LLM 环境检测失败"
        return LocalLlmRunner.infer(serial, env, prompt)
    }

    // ---------- SSH ----------

    private suspend fun executeSsh(cmd: String): String {
        val config = sshConfig ?: return "❌ SSH 未配置。\n请先在设置中填写 SSH 主机信息 (host/port/user/password 或密钥路径)"

        // 维持连接
        if (sshClient == null || !sshClient!!.isConnected) {
            sshClient = SshClient()
            val ok = sshClient!!.connect(config)
            if (!ok) return "❌ SSH 连接失败: ${config.username}@${config.host}:${config.port}"
        }

        return sshClient!!.execute(cmd)
    }

    // ---------- 工具 ----------

    /**
     * 将多行代码写入临时文件, push 到设备后执行。
     */
    private fun executeScriptFile(serial: String, code: String, ext: String, interpreter: String): String {
        val adb = AdbManager.instance
        val remoteScript = "/data/local/tmp/jf_${System.currentTimeMillis()}$ext"
        val localTmp = File.createTempFile("jf_script", ext)
        try {
            localTmp.writeText(code)
            if (!adb.push(serial, localTmp.absolutePath, remoteScript)) {
                return "❌ 推送脚本失败"
            }
        } finally {
            localTmp.delete()
        }
        val out = adb.shell(serial, "$interpreter $remoteScript 2>&1") ?: "(执行失败)"
        adb.shell(serial, "rm -f $remoteScript 2>/dev/null")
        return out
    }

    /** 断开 SSH 连接。 */
    fun disconnectSsh() {
        sshClient?.disconnect()
        sshClient = null
    }

    /** 重置 LLM 环境缓存 (强制重新检测)。 */
    fun resetLlmEnv() {
        llmEnv = null
    }
}
