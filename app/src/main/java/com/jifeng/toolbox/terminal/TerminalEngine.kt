package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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
                        Thread.sleep(20)
                    }
                    try { while (r.ready()) { val n=r.read(buf); if(n>0){synchronized(bufferLock){shellBuffer.append(buf,0,n);bufferLock.notifyAll()}} } } catch (_: Exception) {}
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Shell startup failed: ${e.message}")
            false
        }
    }

    private fun runPersistent(cmd: String): String {
        if (!ensureShell()) return "Shell startup failed"
        val writer = shellWriter ?: return "Shell unavailable"
        synchronized(bufferLock) { shellBuffer.setLength(0) }
        val fullCmd = "cd \"" + cwd.replace("\"", "\\\"") + "\" 2>/dev/null; " + cmd + "\necho \"$END_MARK$?\""
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
                        val trimmed = cmd.trim()
                        if (trimmed == "cd") cwd = System.getProperty("user.home") ?: cwd
                        else if (trimmed.startsWith("cd ")) {
                            val target = trimmed.removePrefix("cd ").trim().trim('"', '\'')
                            cwd = if (target.startsWith("/")) target else "$cwd/$target"
                        }
                        return if (ec != "0") "$result\n[exit=$ec]" else result.ifBlank { "(no output)" }
                    }
                }
                Thread.sleep(40)
            }
            "(timeout)"
        } catch (e: Exception) {
            "Shell I/O error: ${e.message}"
        }
    }

    fun currentDir(): String = cwd

    fun closeShell() {
        try { shellWriter?.write("exit\n".toByteArray()); shellWriter?.flush() } catch (_: Exception) {}
        try { shellProc?.destroy() } catch (_: Exception) {}
        shellProc = null; shellWriter = null
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
                else -> "Unknown env: $lang"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
        ExecResult(output, durationMs = System.currentTimeMillis() - start)
    }

    private fun execInterpreter(primary: String, fallback: String?, code: String, ext: String): String {
        val bin = which(primary) ?: (fallback?.let { which(it) })
            ?: return "Not installed: $primary. Hint: pkg install $primary"
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
            ?: return "No compiler. Hint: pkg install clang"
        val tmp = File(System.getProperty("java.io.tmpdir", "/data/local/tmp"))
        val src = File(tmp, "jf_" + System.currentTimeMillis() + ".c")
        val bin = File(tmp, "jf_" + System.currentTimeMillis() + ".bin")
        return try {
            src.writeText(code)
            val compileOut = runPersistent("$cc '${src.absolutePath}' -o '${bin.absolutePath}' 2>&1")
            if (!bin.exists()) return "Compile failed:\n$compileOut"
            runPersistent("'${bin.absolutePath}' 2>&1").ifBlank { "(no output)" }
        } finally { src.delete(); bin.delete() }
    }

    private suspend fun execLlm(prompt: String): String {
        if (llmEnv == null || llmEnv?.backend == LocalLlmRunner.LlmBackend.NONE) {
            llmEnv = LocalLlmRunner.detect("local")
        }
        val env = llmEnv ?: return "LLM env detection failed"
        return LocalLlmRunner.infer("local", env, prompt)
    }

    private fun which(bin: String): String? = try {
        val p = ProcessBuilder("sh", "-c", "command -v $bin 2>/dev/null").start()
        p.waitFor()
        p.inputStream.bufferedReader().readText().trim().ifBlank { null }
    } catch (_: Exception) { null }

    fun resetLlmEnv() { llmEnv = null }
}
