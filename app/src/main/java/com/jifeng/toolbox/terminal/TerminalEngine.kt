package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 超级终端执行引擎 v6 — Termux 风格持久 Shell。
 *
 * 核心升级:
 *  - 长期持有一个 sh 进程, 所有命令通过同一个 shell 执行 (cd / export / 别名全部持久)
 *  - 工作目录可切换, 环境变量可保留
 *  - 命令用结束标记分隔, 能区分命令边界
 *  - 兼容多语言执行 (python/node/lua 等仍走一次性进程)
 */
object TerminalEngine {

    private const val TAG = "TerminalEngine"
    private const val END_MARK = "__JF_END_${'$'}_"
    private const val ERR_MARK = "__JF_ERR_${'$'}_"

    val LANGUAGES = listOf("shell", "python", "javascript", "lua", "c/c++", "ai-llm")

    data class ExecResult(
        val output: String,
        val isError: Boolean = false,
        val durationMs: Long = 0
    )

    // 持久 shell
    @Volatile private var shellProc: Process? = null
    @Volatile private var shellWriter: java.io.OutputStream? = null
    @Volatile private var shellReader: Thread? = null
    private val shellBuffer = StringBuilder()
    @Volatile private var bufferLock = Object()
    @Volatile private var cwd: String = System.getProperty("user.home") ?: "/"

    private var llmEnv: LocalLlmRunner.LlmEnvironment? = null

    /** 初始化持久 shell (懒加载, 首次命令时启动)。 */
    private fun ensureShell(): Boolean {
        if (shellProc?.isAlive == true) return true
        return try {
            val pb = ProcessBuilder("sh")
                .directory(File(cwd))
                .redirectErrorStream(false)
            shellProc = pb.start()
            shellWriter = shellProc!!.outputStream
            // 读取 stdout
            shellReader = Thread {
                try {
                    val r = BufferedReader(InputStreamReader(shellProc!!.inputStream))
                    val errR = BufferedReader(InputStreamReader(shellProc!!.errorStream))
                    val buf = CharArray(4096)
                    while (shellProc?.isAlive == true) {
                        // 简单合并: 读 stdout
                        if (r.ready()) {
                            val n = r.read(buf)
                            if (n > 0) synchronized(bufferLock) {
                                shellBuffer.append(buf, 0, n)
                                bufferLock.notifyAll()
                            }
                        }
                        if (errR.ready()) {
                            val n = errR.read(buf)
                            if (n > 0) synchronized(bufferLock) {
                                shellBuffer.append(buf, 0, n)
                                bufferLock.notifyAll()
                            }
                        }
                        Thread.sleep(20)
                    }
                    // 收尾
                    try { while (r.ready()) { val n=r.read(buf); if(n>0){synchronized(bufferLock){shellBuffer.append(buf,0,n);bufferLock.notifyAll()}} } } catch (_: Exception) {}
                    try { while (errR.ready()) { val n=errR.read(buf); if(n>0){synchronized(bufferLock){shellBuffer.append(buf,0,n);bufferLock.notifyAll()}} } } catch (_: Exception) {}
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Shell 启动失败: ${e.message}")
            false
        }
    }

    /** 在持久 shell 中执行命令, 返回完整输出。 */
    private fun runPersistent(cmd: String): String {
        if (!ensureShell()) return "Shell 启动失败"
        val writer = shellWriter ?: return "Shell 不可用"
        synchronized(bufferLock) { shellBuffer.setLength(0) }
        val fullCmd = buildString {
            // 同步 cwd
            append("cd \"").append(cwd.replace("\"", "\\\"")).append("\" 2>/dev/null; ")
            append(cmd)
            append("; echo \"").append(END_MARK).append("\$?\"; ")
        }
        return try {
            writer.write(fullCmd.toByteArray())
            writer.write('\n'.code)
            writer.flush()
            val start = System.currentTimeMillis()
            val out = StringBuilder()
            while (System.currentTimeMillis() - start < 30_000) {
                synchronized(bufferLock) {
                    val idx = shellBuffer.indexOf(END_MARK)
                    if (idx >= 0) {
                        out.append(shellBuffer.substring(0, idx))
                        shellBuffer.delete(0, idx + END_MARK.length)
                        // 读 exit code
                        var ec = ""
                        var i = 0
                        while (i < shellBuffer.length && (shellBuffer[i].isDigit() || shellBuffer[i] == '-')) {
                            ec += shellBuffer[i]; i++
                        }
                        if (i > 0) shellBuffer.delete(0, i)
                        // 去掉换行
                        val result = out.toString().trimEnd('\n')
                        // 如果是 cd 命令, 更新 cwd
                        val trimmed = cmd.trim()
                        if (trimmed.startsWith("cd ") || trimmed == "cd") {
                            val newCwd = System.getProperty("user.home") ?: cwd
                            cwd = if (trimmed == "cd") newCwd else {
                                val target = trimmed.removePrefix("cd ").trim().trim('"', '\'')
                                if (target.startsWith("/")) target else "$cwd/$target"
                            }
                        }
                        return if (ec.isNotBlank() && ec != "0") "$result\n[exit=$ec]" else result.ifBlank { "(无输出)" }
                    }
                }
                Thread.sleep(40)
            }
            "(命令超时或仍在运行)"
        } catch (e: Exception) {
            "Shell I/O 错误: ${e.message}"
        }
    }

    /** 当前工作目录。 */
    fun currentDir(): String = cwd

    /** 关闭 shell。 */
    fun closeShell() {
        try { shellWriter?.write("exit\n".toByteArray()); shellWriter?.flush() } catch (_: Exception) {}
        try { shellProc?.destroy() } catch (_: Exception) {}
        shellProc = null; shellWriter = null; shellReader = null
    }

    suspend fun execute(lang: String, cmd: String): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val output = try {
            when (lang) {
                "shell" -> runPersistent(cmd)
                "python" -> executePython(cmd)
                "javascript" -> executeJavaScript(cmd)
                "lua" -> executeLua(cmd)
                "c/c++" -> executeCpp(cmd)
                "ai-llm" -> executeLlm(cmd)
                else -> "未知运行环境: $lang"
            }
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
        ExecResult(output, durationMs = System.currentTimeMillis() - start)
    }

    private fun executePython(code: String): String {
        val py = which("python3") ?: which("python")
        ?: return "❌ 本机未安装 Python3。\n提示: 在 shell 中执行 `pkg install python` 可安装"
        return if (code.contains("\n") || code.length > 200) scriptFile(code, ".py", py)
        else runPersistent("$py -c '${code.replace("'", "'\\''")}' 2>&1")
    }

    private fun executeJavaScript(code: String): String {
        val node = which("node") ?: return "❌ 未安装 Node.js。\n提示: `pkg install nodejs`"
        return if (code.contains("\n") || code.length > 200) scriptFile(code, ".js", "node")
        else runPersistent("node -e '${code.replace("'", "'\\''")}' 2>&1")
    }

    private fun executeLua(code: String): String {
        val lua = which("lua") ?: which("luajit")
            ?: return "❌ 未安装 Lua。\n提示: `pkg install lua`"
        return if (code.contains("\n")) scriptFile(code, ".lua", lua)
        else runPersistent("$lua -e '${code.replace("'", "'\\''")}' 2>&1")
    }

    private fun executeCpp(code: String): String {
        val cc = which("clang") ?: which("gcc")
            ?: return "❌ 未安装编译器。\n提示: `pkg install clang`"
        val tmp = File(System.getProperty("java.io.tmpdir", "/data/local/tmp"))
        val src = File(tmp, "jf_${System.currentTimeMillis()}.c")
        val bin = File(tmp, "jf_${System.currentTimeMillis()}.bin")
        return try {
            src.writeText(code)
            val compileOut = runPersistent("$cc '${src.absolutePath}' -o '${bin.absolutePath}' 2>&1")
            if (!bin.exists()) return "编译失败:\n$compileOut"
            runPersistent("'${bin.absolutePath}' 2>&1").ifBlank { "(无输出)" }
        } finally {
            src.delete(); bin.delete()
        }
    }

    private suspend fun executeLlm(prompt: String): String {
        if (llmEnv == null || llmEnv?.backend == LocalLlmRunner.LlmBackend.NONE) {
            llmEnv = LocalLlmRunner.detect("local")
        }
        val env = llmEnv ?: return "❌ LLM 环境检测失败"
        return LocalLlmRunner.infer("local", env, prompt)
    }

    private fun which(bin: String): String? = try {
        val p = ProcessBuilder("sh", "-c", "command -v $bin 2>/dev/null").start()
        p.waitFor()
        p.inputStream.bufferedReader().readText().trim().ifBlank { null }
    } catch (_: Exception) { null }

    private fun scriptFile(code: String, ext: String, interp: String): String {
        val tmp = File(System.getProperty("java.io.tmpdir", "/data/local/tmp"))
        val f = File(tmp, "jf_${System.currentTimeMillis()}$ext")
        return try {
            f.writeText(code)
            runPersistent("'$interp' '${f.absolutePath}' 2>&1")
        } finally { f.delete() }
    }

    fun resetLlmEnv() { llmEnv = null }
}
