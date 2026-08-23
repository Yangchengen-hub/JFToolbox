package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 超级终端引擎 v7 — 开箱即用, 类 Termux 体验。
 *
 * 改进:
 *  1. 持久 sh 进程, cd/export/alias/变量全部跨命令保持
 *  2. 启动时自动注入 Windows→Linux 命令别名 (dir/type/copy/move/del/ipconfig 等),
 *     用户可以直接敲 Windows 或 Linux 命令
 *  3. 内置 toybox/busybox 工具路径探测, 自动补全 PATH
 *  4. 命令结束标记 + exit code 回传, 30s 超时
 *  5. 支持多语言解释器: python/node/lua/c(c++), 均通过 which 检测
 */
object TerminalEngine {
    private const val TAG = "TerminalEngine"
    private const val END_MARK = "__JF_END_MARK__"

    val LANGUAGES = listOf("shell", "python", "javascript", "lua", "c/c++", "ai-llm")

    data class ExecResult(val output: String, val isError: Boolean = false, val durationMs: Long = 0)

    @Volatile private var shellProc: Process? = null
    @Volatile private var shellWriter: java.io.OutputStream? = null
    private val shellBuffer = StringBuilder()
    private val bufferLock = Object()
    @Volatile private var cwd: String = System.getProperty("user.home") ?: "/"
    private var llmEnv: LocalLlmRunner.LlmEnvironment? = null
    @Volatile private var initialized = false

    /** 启动时注入 Windows→Linux 别名 + PATH 补全。 */
    private fun bootScript(): String {
        val pathDirs = listOf(
            "/system/bin", "/system/xbin",
            "/sbin", "/vendor/bin",
            "/data/local/tmp",
            System.getProperty("java.io.tmpdir", "/data/local/tmp")
        ).filter { File(it).exists() }.joinToString(":")

        // Windows→Linux 常用命令别名, 在 sh 启动后立即 alias 注入
        val aliases = listOf(
            "dir" to "ls -lah --color=never",
            "type" to "cat",
            "copy" to "cp -i",
            "xcopy" to "cp -ri",
            "move" to "mv -i",
            "ren" to "mv",
            "del" to "rm -i",
            "erase" to "rm -i",
            "md" to "mkdir -p",
            "mkdir" to "mkdir -p",
            "rd" to "rmdir",
            "cls" to "printf '\\033c'",
            "clear" to "printf '\\033c'",
            "ver" to "uname -a",
            "ipconfig" to "ip addr 2>/dev/null || ifconfig",
            "tracert" to "traceroute",
            "ping" to "ping",
            "tasklist" to "ps -ef",
            "taskkill" to "kill",
            "findstr" to "grep",
            "where" to "which",
            "echo" to "echo",
            "set" to "env",
            "cd" to "cd",
            "exit" to "echo '__JF_EXIT__' && exit",
            "help" to "cat <<'JFHELP'\nJF Terminal — Windows/Linux 双语命令:\n  dir=ls  type=cat  copy=cp  move=mv  del=rm\n  ipconfig=ip addr  tasklist=ps  findstr=grep\n  前缀 :py/:js/:lua 临时切语言\nJFHELP",
            "more" to "cat",
            "attrib" to "ls -la",
            "chkdsk" to "df -h",
            "systeminfo" to "uname -a && echo && cat /proc/cpuinfo | head -30",
            "whoami" to "id"
        ).joinToString("\n") { (w, n) -> "alias $w='$n' 2>/dev/null" }

        return """
            export PATH="$pathDirs:${'$'}PATH"
            export PS1='jif:\w> '
            export TERM=xterm-256color
            umask 022
            $aliases
            true
        """.trimIndent()
    }

    private fun ensureShell(): Boolean {
        if (shellProc?.isAlive == true) return true
        return try {
            val pb = ProcessBuilder("sh").directory(File(cwd)).redirectErrorStream(true)
            shellProc = pb.start()
            shellWriter = shellProc!!.outputStream
            val r = BufferedReader(InputStreamReader(shellProc!!.inputStream))
            Thread {
                try {
                    val buf = CharArray(4096)
                    while (shellProc?.isAlive == true) {
                        if (r.ready()) {
                            val n = r.read(buf)
                            if (n > 0) synchronized(bufferLock) {
                                shellBuffer.append(buf, 0, n)
                                bufferLock.notifyAll()
                            }
                        }
                        Thread.sleep(15)
                    }
                    try {
                        while (r.ready()) {
                            val n = r.read(buf)
                            if (n > 0) synchronized(bufferLock) {
                                shellBuffer.append(buf, 0, n)
                                bufferLock.notifyAll()
                            }
                        }
                    } catch (_: Exception) {}
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            // inject aliases & PATH, wait for ready
            if (!initialized) {
                val w = shellWriter!!
                w.write((bootScript() + "\necho \"$END_MARK\"0\n").toByteArray())
                w.flush()
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 5000L) {
                    synchronized(bufferLock) {
                        val idx = shellBuffer.indexOf(END_MARK)
                        if (idx >= 0) {
                            shellBuffer.delete(0, idx + END_MARK.length + 1)
                            initialized = true
                            return true
                        }
                    }
                    Thread.sleep(30)
                }
                // timeout but shell alive — still mark initialized
                initialized = true
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Shell startup failed: ${e.message}")
            false
        }
    }

    private fun runPersistent(cmd: String): String {
        if (!ensureShell()) return "Shell 启动失败"
        val writer = shellWriter ?: return "Shell 不可用"
        synchronized(bufferLock) { shellBuffer.setLength(0) }
        // cd into cwd, then execute. Windows 风格的反斜杠转成正斜杠
        val normalized = cmd.replace("\\", "/").trim()
        val fullCmd = "cd \"" + cwd.replace("\"", "\\\"") + "\" 2>/dev/null; " +
            normalized + "\necho \"$END_MARK$?\""
        return try {
            writer.write((fullCmd + "\n").toByteArray())
            writer.flush()
            val start = System.currentTimeMillis()
            val out = StringBuilder()
            while (System.currentTimeMillis() - start < 30000L) {
                synchronized(bufferLock) {
                    val idx = shellBuffer.indexOf(END_MARK)
                    if (idx >= 0) {
                        out.append(shellBuffer.substring(0, idx))
                        shellBuffer.delete(0, idx + END_MARK.length)
                        var ec = ""
                        var i = 0
                        while (i < shellBuffer.length && shellBuffer[i].isDigit()) {
                            ec += shellBuffer[i]; i++
                        }
                        if (i > 0) shellBuffer.delete(0, i)
                        val result = out.toString().trimEnd('\n')
                        // 解析 cd (alias 已注入, 但真实路径变化需更新)
                        val t = normalized
                        if (t == "cd" || t == "cd ~") cwd = System.getProperty("user.home") ?: cwd
                        else if (t.startsWith("cd ")) {
                            val target = t.removePrefix("cd ").trim().trim('"', '\'')
                            cwd = if (target.startsWith("/")) target
                            else if (target == "..") {
                                val p = cwd.trimEnd('/')
                                if (p.contains('/')) p.substringBeforeLast('/').ifBlank { "/" } else cwd
                            }
                            else "$cwd/$target"
                        }
                        return if (ec != "0" && ec.isNotEmpty()) {
                            result.ifBlank { "(exit=$ec)" } + if (result.isNotBlank()) "\n[exit=$ec]" else ""
                        } else result.ifBlank { "" }
                    }
                }
                Thread.sleep(35)
            }
            "(命令超时, 30秒)"
        } catch (e: Exception) {
            "Shell I/O 错误: ${e.message}"
        }
    }

    fun currentDir(): String = cwd

    /** 终端欢迎信息 (打开即显示)。 */
    fun banner(): String = buildString {
        appendLine("JF Terminal v7 — 开箱即用")
        appendLine("─────────────────────────")
        appendLine("• Linux 命令直接用 (ls/cat/grep/find/ps/top...)")
        appendLine("• Windows 命令也支持: dir/type/copy/del/ipconfig/tasklist/findstr")
        appendLine("• 前缀 :py/:js/:lua 临时切换解释器")
        appendLine("• 工作目录持久保持, cd/export/alias 跨命令生效")
        appendLine("• 输入 help 查看全部别名")
        appendLine()
        val os = try { runPersistent("uname -a") } catch (_: Exception) { "Android shell" }
        if (os.isNotBlank() && !os.startsWith("(")) appendLine(os)
        append("jif:${cwd}> ")
    }

    fun closeShell() {
        try { shellWriter?.write("exit\n".toByteArray()); shellWriter?.flush() } catch (_: Exception) {}
        try { shellProc?.destroy() } catch (_: Exception) {}
        shellProc = null; shellWriter = null; initialized = false
    }

    suspend fun execute(lang: String, cmd: String): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val output = try {
            when (lang) {
                "shell" -> runPersistent(cmd)
                "python" -> execInterpreter("python3", "python", cmd, ".py")
                "javascript" -> execInterpreter("node", null, cmd, ".js")
                "lua" -> execInterpreter("lua", "luajit", cmd, ".lua")
                "c/c++" -> execCpp(cmd)
                "ai-llm" -> execLlm(cmd)
                else -> runPersistent(cmd)
            }
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
        ExecResult(output, durationMs = System.currentTimeMillis() - start)
    }

    private fun execInterpreter(primary: String, fallback: String?, code: String, ext: String): String {
        val bin = which(primary) ?: (fallback?.let { which(it) })
            ?: return "$primary 未安装。提示: 在支持的环境中可安装 Termux 后 pkg install $primary"
        return if (code.contains("\n") || code.length > 200) {
            val tmp = File(System.getProperty("java.io.tmpdir", "/data/local/tmp"),
                "jf_" + System.currentTimeMillis() + ext)
            try { tmp.writeText(code); runPersistent("$bin '${tmp.absolutePath}' 2>&1") }
            finally { tmp.delete() }
        } else {
            runPersistent("$bin -c '${code.replace("'", "'\\''")}' 2>&1")
        }
    }

    private fun execCpp(code: String): String {
        val cc = which("clang") ?: which("gcc")
            ?: return "未找到 C/C++ 编译器 (clang/gcc 未安装)"
        val tmp = File(System.getProperty("java.io.tmpdir", "/data/local/tmp"))
        val src = File(tmp, "jf_" + System.currentTimeMillis() + ".c")
        val bin = File(tmp, "jf_" + System.currentTimeMillis() + ".bin")
        return try {
            src.writeText(code)
            val compileOut = runPersistent("$cc '${src.absolutePath}' -o '${bin.absolutePath}' 2>&1")
            if (!bin.exists()) return "编译失败:\n$compileOut"
            runPersistent("'${bin.absolutePath}' 2>&1").ifBlank { "(无输出)" }
        } finally { src.delete(); bin.delete() }
    }

    private suspend fun execLlm(prompt: String): String {
        if (llmEnv == null || llmEnv?.backend == LocalLlmRunner.LlmBackend.NONE) {
            llmEnv = LocalLlmRunner.detect("local")
        }
        val env = llmEnv ?: return "LLM 环境检测失败"
        return LocalLlmRunner.infer("local", env, prompt)
    }

    private fun which(bin: String): String? = try {
        val p = ProcessBuilder("sh", "-c", "command -v $bin 2>/dev/null").start()
        p.waitFor()
        p.inputStream.bufferedReader().readText().trim().ifBlank { null }
    } catch (_: Exception) { null }

    fun resetLlmEnv() { llmEnv = null