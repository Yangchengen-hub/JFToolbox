package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 超级终端执行引擎 v5 —— 单一本地模式。
 *
 * 支持的运行环境 (本机直接执行):
 * - shell: 本机 shell (sh / bash)
 * - python: 本机 Python3 解释器
 * - javascript: 本机 Node.js
 * - lua: 本机 Lua 解释器
 * - c/c++: 本地编译运行 (gcc/clang)
 * - ai-llm: 本地 LLM 推理
 *
 * 所有命令通过 Runtime.getRuntime().exec() / ProcessBuilder 在本机执行。
 * 预装概念: 提示用户可通过内置包管理器安装工具。
 */
object TerminalEngine {

    private const val TAG = "TerminalEngine"

    /** 运行环境标识。 */
    val LANGUAGES = listOf("shell", "python", "javascript", "lua", "c/c++", "ai-llm")

    /** 执行结果。 */
    data class ExecResult(
        val output: String,
        val isError: Boolean = false,
        val durationMs: Long = 0
    )

    /** LLM 环境缓存。 */
    private var llmEnv: LocalLlmRunner.LlmEnvironment? = null

    /**
     * 执行命令 (本地模式)。
     * @param lang 运行环境
     * @param cmd 命令/代码/prompt
     */
    suspend fun execute(lang: String, cmd: String): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val output = try {
            when (lang) {
                "shell" -> executeShell(cmd)
                "python" -> executePython(cmd)
                "javascript" -> executeJavaScript(cmd)
                "lua" -> executeLua(cmd)
                "c/c++" -> executeCpp(cmd)
                "ai-llm" -> executeLlm(cmd)
                else -> "未知运行环境: $lang"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "执行异常 [$lang]: ${e.message}")
            "错误: ${e.message}"
        }
        ExecResult(output, durationMs = System.currentTimeMillis() - start)
    }

    // ---------- 本地 Shell ----------

    private fun executeShell(cmd: String): String {
        return runCommand(listOf("sh", "-c", cmd))
    }

    // ---------- Python ----------

    private fun executePython(code: String): String {
        val pyCheck = runCommand(listOf("sh", "-c", "command -v python3 2>/dev/null || command -v python 2>/dev/null")).trim()
        val pyBin = when {
            pyCheck.isNotBlank() -> pyCheck.substringBefore("\n")
            else -> ""
        }
        if (pyBin.isBlank()) {
            return "❌ 本机未安装 Python3。\n" +
                "提示: 可通过内置包管理器安装 \n" +
                "      pkg install python  (Termux)\n" +
                "      或从 Python 官网下载 APK 安装"
        }
        // 多行代码: 写入临时文件执行
        if (code.contains("\n") || code.length > 200) {
            return executeScriptFile(code, ".py", pyBin)
        }
        // 单行: -c 模式
        val escaped = code.replace("'", "'\\''")
        return runCommand(listOf("sh", "-c", "$pyBin -c '$escaped' 2>&1"))
    }

    // ---------- JavaScript ----------

    private fun executeJavaScript(code: String): String {
        val nodeCheck = runCommand(listOf("sh", "-c", "command -v node 2>/dev/null")).trim()
        if (nodeCheck.isBlank()) {
            return "❌ 本机未安装 Node.js。\n" +
                "提示: 可通过内置包管理器安装 \n" +
                "      pkg install nodejs  (Termux)"
        }
        if (code.contains("\n") || code.length > 200) {
            return executeScriptFile(code, ".js", "node")
        }
        val escaped = code.replace("'", "'\\''")
        return runCommand(listOf("sh", "-c", "node -e '$escaped' 2>&1"))
    }

    // ---------- Lua ----------

    private fun executeLua(code: String): String {
        val luaCheck = runCommand(listOf("sh", "-c", "command -v lua 2>/dev/null || command -v luajit 2>/dev/null")).trim()
        val luaBin = when {
            luaCheck.isNotBlank() -> luaCheck.substringBefore("\n")
            else -> ""
        }
        if (luaBin.isBlank()) {
            return "❌ 本机未安装 Lua。\n" +
                "提示: 可通过内置包管理器安装 \n" +
                "      pkg install lua  (Termux)"
        }
        if (code.contains("\n")) {
            return executeScriptFile(code, ".lua", luaBin)
        }
        val escaped = code.replace("'", "'\\''")
        return runCommand(listOf("sh", "-c", "$luaBin -e '$escaped' 2>&1"))
    }

    // ---------- C/C++ ----------

    private fun executeCpp(code: String): String {
        // 检测编译器
        val gccCheck = runCommand(listOf("sh", "-c", "command -v gcc 2>/dev/null")).trim()
        val clangCheck = runCommand(listOf("sh", "-c", "command -v clang 2>/dev/null")).trim()
        val compiler = when {
            clangCheck.isNotBlank() -> "clang"
            gccCheck.isNotBlank() -> "gcc"
            else -> ""
        }
        if (compiler.isBlank()) {
            return "❌ 本机未安装 C/C++ 编译器 (gcc/clang)。\n" +
                "提示: 可通过内置包管理器安装 \n" +
                "      pkg install clang  (Termux)"
        }

        val tmpDir = System.getProperty("java.io.tmpdir", "/data/local/tmp")
        val srcFile = File(tmpDir, "jf_${System.currentTimeMillis()}.c")
        val binFile = File(tmpDir, "jf_${System.currentTimeMillis()}")
        try {
            srcFile.writeText(code)
            // 编译
            val compileOut = runCommand(listOf("sh", "-c",
                "$compiler ${srcFile.absolutePath} -o ${binFile.absolutePath} 2>&1"))
            if (compileOut.contains("error", ignoreCase = true) && !binFile.exists()) {
                return "编译失败:\n$compileOut"
            }
            // 运行
            val runOut = runCommand(listOf("sh", "-c", "${binFile.absolutePath} 2>&1"))
            return runOut.ifBlank { "(运行成功, 无输出)" }
        } finally {
            srcFile.delete()
            binFile.delete()
        }
    }

    // ---------- LLM ----------

    private suspend fun executeLlm(prompt: String): String {
        if (llmEnv == null || llmEnv?.backend == LocalLlmRunner.LlmBackend.NONE) {
            llmEnv = LocalLlmRunner.detect("local")
        }
        val env = llmEnv ?: return "❌ LLM 环境检测失败"
        return LocalLlmRunner.infer("local", env, prompt)
    }

    // ---------- 工具: 通用命令执行 ----------

    /**
     * 使用 ProcessBuilder 在本机执行命令, 返回 stdout+stderr 合并输出。
     */
    private fun runCommand(cmd: List<String>): String {
        return try {
            val pb = ProcessBuilder(cmd)
                .redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            output.ifBlank { "(无输出)" }
        } catch (e: Exception) {
            "执行失败: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * 将多行代码写入临时文件, 在本地执行。
     */
    private fun executeScriptFile(code: String, ext: String, interpreter: String): String {
        val tmpDir = System.getProperty("java.io.tmpdir", "/data/local/tmp")
        val scriptFile = File(tmpDir, "jf_${System.currentTimeMillis()}$ext")
        try {
            scriptFile.writeText(code)
            return runCommand(listOf("sh", "-c", "$interpreter ${scriptFile.absolutePath} 2>&1"))
        } finally {
            scriptFile.delete()
        }
    }

    /** 重置 LLM 环境缓存 (强制重新检测)。 */
    fun resetLlmEnv() {
        llmEnv = null
    }
}
