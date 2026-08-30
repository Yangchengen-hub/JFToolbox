package com.jifeng.toolbox.terminal

import android.content.Context
import android.os.Environment
import com.jifeng.toolbox.JFToolboxApp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 超级终端引擎 v8 — 开箱即用, 类 Termux 体验。
 *
 * v8:
 *  1. 打开直接落在内部存储根目录 (/sdcard), 可直接管理本地文件
 *  2. 智能识别语言: shebang / :py:js:lua:c 前缀 / .py .js .lua 脚本文件, 默认 shell
 *  3. 内置 push/pull(=send/recv): 通过已连接的 ADB 设备直接收发文件
 *  4. 持久 sh 进程, cwd 由 shell 的 $PWD 权威回传, cd .. 等相对路径完全正确
 *  5. Windows→Linux 别名 (无 -i 交互参数, 避免非交互 shell 卡死)
 *  6. 串行锁保证多协程并发输入不串输出
 */
object TerminalEngine {
    private const val TAG = "TerminalEngine"
    private const val END_MARK = "__JF_END_MARK__"

    @Volatile private var initialized = false

    data class ExecResult(val output: String, val isError: Boolean = false, val durationMs: Long = 0,
                         val clearScreen: Boolean = false)

    @Volatile private var shellProc: Process? = null
    @Volatile private var shellWriter: java.io.OutputStream? = null
    private val shellBuffer = StringBuilder()
    private val bufferLock = Object()
    private val runLock = Object()

    /** 当前工作目录, 启动即内部存储根目录。 */
    @Volatile private var cwd: String = "/sdcard"
    private var llmEnv: LocalLlmRunner.LlmEnvironment? = null

    /** 预装工具目录 (固定在内部存储, 终端首次启动部署 assets/jf_tools.sh)。 */
    @Volatile private var toolsDir: File = File("/sdcard/JFToolbox/bin")
    @Volatile private var toolsDeployed = false

    @Volatile private var appCtx: Context? = null

    /** 应用启动时调用: 选定可写的工作目录 (内部存储优先)。 */
    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (initialized) return
        cwd = pickWorkingDir(ctx)
        // 工作目录若为应用专属外部目录, 预装工具也放其下
        toolsDir = File(cwd, "bin")
        Logger.i(TAG, "终端工作目录: $cwd")
        initialized = true
    }

    /** 首次 shell 就绪后把 assets/jf_tools.sh 部署到工具目录 (预装, 开箱即用)。 */
    private fun deployTools() {
        if (toolsDeployed) return
        toolsDeployed = true
        try {
            val ctx = appCtx ?: return
            // 工具目录固定放内部存储公共位置; 无全文件权限时退回工作目录/bin
            val target = if (Environment.isExternalStorageManager()) File("/sdcard/JFToolbox/bin")
                         else File(cwd, "bin")
            toolsDir = target
            runCatching { target.mkdirs() }
            if (!target.exists() || !target.canWrite()) return
            val script = File(target, "jf_tools.sh")
            ctx.assets.open("jf_tools.sh").use { input ->
                script.outputStream().use { input.copyTo(it) }
            }
            // 执行部署脚本 (内部通过 sh 运行, 生成各命令)
            val shPath = File("/system/bin/sh").absolutePath
            runCatching {
                val p = ProcessBuilder(shPath, script.absolutePath)
                    .redirectErrorStream(true)
                p.environment()["JF_TOOLS_DIR"] = target.absolutePath
                p.directory(File("/"))
                val proc = p.start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                Logger.i(TAG, "预装工具部署: ${out.trim()}")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "预装工具部署失败(不影响使用): ${e.message}")
        }
    }

    /**
     * 选择工作目录:
     *  1. 有「所有文件访问」权限 → /sdcard (内部存储根目录)
     *  2. 否则用应用专属外部目录 (无需权限可读写, 同样在内部存储里)
     *  3. 最后兜底应用私有目录
     */
    private fun pickWorkingDir(ctx: Context): String {
        fun writable(dir: File?): Boolean =
            dir != null && dir.exists() && dir.canWrite()

        runCatching {
            if (Environment.isExternalStorageManager()) {
                val ext = Environment.getExternalStorageDirectory()
                if (ext.exists() && ext.isDirectory) return ext.absolutePath
            }
        }
        ctx.getExternalFilesDir(null)?.let { if (it.exists() && it.isDirectory) return it.absolutePath }
        runCatching {
            @Suppress("DEPRECATION")
            val ext = Environment.getExternalStorageDirectory()
            if (writable(ext)) return ext.absolutePath
        }
        return ctx.filesDir?.absolutePath ?: "/sdcard"
    }

    /** 启动时注入 PATH 补全 + Windows→Linux 别名 (全部非交互, 不会卡住)。 */
    private fun bootScript(): String {
        val pathDirs = listOf(
            "/system/bin", "/system/xbin",
            "/sbin", "/vendor/bin",
            "/data/local/tmp",
            toolsDir.absolutePath,
            System.getProperty("java.io.tmpdir", "/data/local/tmp")
        ).filter { File(it).exists() }.distinct().joinToString(":")

        val aliases = listOf(
            "dir" to "ls -lah",
            "type" to "cat",
            "copy" to "cp",
            "xcopy" to "cp -r",
            "move" to "mv",
            "ren" to "mv",
            "del" to "rm",
            "erase" to "rm",
            "md" to "mkdir -p",
            "rd" to "rmdir",
            "cls" to "printf '\\033c'",
            "clear" to "printf '\\033c'",
            "ver" to "uname -a",
            "ipconfig" to "ip addr 2>/dev/null || ifconfig",
            "tracert" to "traceroute",
            "tasklist" to "ps -ef",
            "taskkill" to "kill",
            "findstr" to "grep",
            "where" to "which",
            "set" to "env",
            "attrib" to "ls -la",
            "chkdsk" to "df -h",
            "systeminfo" to "uname -a && cat /proc/cpuinfo | head -30",
            "whoami" to "id",
            "more" to "cat",
            "nano" to "cat",
            "vim" to "cat",
            "vi" to "cat"
        ).joinToString("\n") { (w, n) -> "alias $w='$n' 2>/dev/null" }

        return """
            export PATH="$pathDirs:${'$'}PATH"
            export PS1='jif:\w> '
            export TERM=xterm-256color
            umask 022
            cd "$cwd" 2>/dev/null
            $aliases
            true
        """.trimIndent()
    }

    private fun ensureShell(): Boolean {
        if (shellProc?.isAlive == true) return true
        return try {
            val workDir = File(cwd).takeIf { it.exists() && it.isDirectory } ?: File("/")
            val pb = ProcessBuilder("sh").directory(workDir).redirectErrorStream(true)
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

            // 后台部署预装工具 (不阻塞终端)
            Thread { deployTools() }.apply { isDaemon = true; start() }

            val w = shellWriter!!
            w.write((bootScript() + "\necho \"$END_MARK\"0\n").toByteArray())
            w.flush()
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 5000L) {
                synchronized(bufferLock) {
                    val idx = shellBuffer.indexOf(END_MARK)
                    if (idx >= 0) {
                        shellBuffer.delete(0, idx + END_MARK.length + 1)
                        return true
                    }
                }
                Thread.sleep(30)
            }
            true // 超时但 shell 存活, 继续用
        } catch (e: Exception) {
            Logger.e(TAG, "Shell 启动失败: ${e.message}")
            false
        }
    }

    /** 在持久 shell 中执行, 返回 (输出, exitCode, 最新PWD)。 */
    private fun runPersistent(cmd: String): Triple<String, Int, String> {
        synchronized(runLock) {
            if (!ensureShell()) return Triple("Shell 启动失败", 1, cwd)
            val writer = shellWriter ?: return Triple("Shell 不可用", 1, cwd)
            synchronized(bufferLock) { shellBuffer.setLength(0) }
            val normalized = cmd.replace("\\", "/").trim()
            // 标记格式: __JF_END_MARK__<exit>|<PWD>
            val fullCmd = "$normalized\necho \"$END_MARK${'$'}?|${'$'}PWD\""
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
                            // 解析 "0|/sdcard/xxx"
                            val tail = StringBuilder()
                            while (shellBuffer.isNotEmpty() && shellBuffer[0] != '\n') {
                                tail.append(shellBuffer[0]); shellBuffer.deleteCharAt(0)
                            }
                            val parts = tail.toString().split("|", limit = 2)
                            val ec = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: -1
                            val newPwd = parts.getOrNull(1)?.trim()?.takeIf { it.startsWith("/") }
                            if (!newPwd.isNullOrBlank()) cwd = newPwd
                            val text = out.toString().trimEnd('\n')
                            return Triple(text, ec, cwd)
                        }
                    }
                    Thread.sleep(35)
                }
                Triple("(命令超时, 30秒; 长时间运行的命令请加参数限制, 如 top -n 1)", 124, cwd)
            } catch (e: Exception) {
                Triple("Shell I/O 错误: ${e.message}", 1, cwd)
            }
        }
    }

    fun currentDir(): String = cwd

    /** 极简欢迎语 — 直接进入工作目录, 不啰嗦。 */
    fun banner(): String = buildString {
        appendLine("JF Terminal — 工作目录: $cwd")
        append("常用: ls/cat/mkdir/cp/mv/rm · push 本地→目标 · pull 目标→本地 · help")
    }

    fun closeShell() {
        try { shellWriter?.write("exit\n".toByteArray()); shellWriter?.flush() } catch (_: Exception) {}
        try { shellProc?.destroy() } catch (_: Exception) {}
        shellProc = null; shellWriter = null
    }

    /** 智能执行入口: 自动识别语言 / 文件传输 / shell。 */
    suspend fun run(raw: String): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val cmd0 = raw.trim()
        if (cmd0.isBlank()) return@withContext ExecResult("", durationMs = 0)

        // 清屏
        if (cmd0 == "clear" || cmd0 == "cls") {
            return@withContext ExecResult("", clearScreen = true, durationMs = 0)
        }
        if (cmd0 == "help" || cmd0 == "?") {
            return@withContext ExecResult(helpText(), durationMs = System.currentTimeMillis() - start)
        }

        // 语言前缀
        val (lang, code) = when {
            cmd0.startsWith(":py ") -> "python" to cmd0.removePrefix(":py ").trim()
            cmd0.startsWith(":py\n") -> "python" to cmd0.removePrefix(":py").trim()
            cmd0.startsWith(":js ") -> "javascript" to cmd0.removePrefix(":js ").trim()
            cmd0.startsWith(":js\n") -> "javascript" to cmd0.removePrefix(":js").trim()
            cmd0.startsWith(":lua ") -> "lua" to cmd0.removePrefix(":lua ").trim()
            cmd0.startsWith(":c ") -> "c/c++" to cmd0.removePrefix(":c ").trim()
            cmd0.startsWith(":llm ") -> "ai-llm" to cmd0.removePrefix(":llm ").trim()
            else -> {
                // shebang 智能识别
                val shebang = Regex("^#!\\s*(?:/\\S+)*(?:env\\s+)?(\\S+)").find(cmd0)?.groupValues?.get(1)
                when (shebang) {
                    "python", "python3", "py" -> "python" to cmd0.substringAfter('\n').ifBlank { cmd0 }
                    "node", "nodejs" -> "javascript" to cmd0.substringAfter('\n').ifBlank { cmd0 }
                    "lua", "luajit" -> "lua" to cmd0.substringAfter('\n').ifBlank { cmd0 }
                    "sh", "bash" -> "shell" to cmd0.substringAfter('\n').ifBlank { cmd0 }
                    else -> {
                        // 脚本文件智能识别: x.py / x.js / x.lua
                        val firstToken = cmd0.substringBefore(' ').substringBefore('\n')
                        val ext = firstToken.substringAfterLast('.', "").lowercase()
                        when (ext) {
                            "py" -> "python" to cmd0
                            "js" -> "javascript" to cmd0
                            "lua" -> "lua" to cmd0
                            else -> "shell" to cmd0
                        }
                    }
                }
            }
        }

        val output = try {
            when (lang) {
                "shell" -> runShellSmart(code)
                "python" -> execInterpreter("python3", "python", code, ".py")
                "javascript" -> execInterpreter("node", null, code, ".js")
                "lua" -> execInterpreter("lua", "luajit", code, ".lua")
                "c/c++" -> execCpp(code)
                "ai-llm" -> execLlm(code)
                else -> runShellSmart(code)
            }
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
        val isErr = output.contains("[exit=") || output.startsWith("错误") ||
            output.startsWith("Shell") || output.contains("失败")
        ExecResult(output, isError = isErr, durationMs = System.currentTimeMillis() - start)
    }

    /** shell 层智能路由: push/pull/adb 内建命令, 其余走持久 shell。 */
    private fun runShellSmart(cmd: String): String {
        val tokens = cmd.split(Regex("\\s+")).filter { it.isNotBlank() }
        val head = tokens.firstOrNull()?.lowercase().orEmpty()

        // 文件传输: push/send 本地→目标, pull/recv 目标→本地
        when (head) {
            "push", "send" -> {
                if (tokens.size < 2) return "用法: push <本地路径> [目标路径]\n示例: push test.zip /sdcard/Download/"
                return transferPush(tokens[1], tokens.getOrNull(2))
            }
            "pull", "recv" -> {
                if (tokens.size < 2) return "用法: pull <目标路径> [本地路径]\n示例: pull /sdcard/DCIM/a.jpg ./a.jpg"
                return transferPull(tokens[1], tokens.getOrNull(2))
            }
            "devices", "adb" -> {
                if (tokens.size >= 2 && tokens[1].lowercase() == "devices") return adbDevices()
                if (tokens.size >= 2 && tokens[1].lowercase() == "shell") {
                    val remote = tokens.drop(2).joinToString(" ")
                    if (remote.isBlank()) return "用法: adb shell <命令>  (在被控设备上执行)"
                    return adbShell(remote)
                }
                if (head == "devices") return adbDevices()
            }
            "ashell" -> {
                val remote = tokens.drop(1).joinToString(" ")
                if (remote.isBlank()) return "用法: ashell <命令>  (在被控设备上执行)"
                return adbShell(remote)
            }
        }
        val (out, ec, _) = runPersistent(cmd)
        return if (ec != 0 && out.isNotBlank()) "$out\n[exit=$ec]"
        else if (ec != 0) "[exit=$ec]"
        else out
    }

    private fun adbDevices(): String {
        val list = runCatching { AdbManager.listDevices() }.getOrDefault(emptyList())
        if (list.isEmpty()) return "当前无已连接的 ADB 设备。\n提示: OTG 连接被控端并在主页完成授权, 或使用无线调试。"
        return buildString {
            appendLine("已连接 ${list.size} 台设备:")
            list.forEachIndexed { i, s -> appendLine("  ${i + 1}. $s") }
        }.trimEnd()
    }

    private fun adbShell(cmd: String): String {
        val serial = runCatching { AdbManager.listDevices().firstOrNull() }.getOrNull()
            ?: return "无已连接设备, 无法执行远程命令"
        return runCatching { AdbManager.shell(serial, cmd) }
            .getOrNull()?.trimEnd().orEmpty().ifBlank { "(无输出)" }
    }

    private fun transferPush(local: String, remote: String?): String {
        val serial = runCatching { AdbManager.listDevices().firstOrNull() }.getOrNull()
            ?: return "无已连接设备。请先在主页连接被控端 (OTG 或无线调试)。"
        val localFile = resolveLocal(local)
        if (localFile == null || !localFile.exists()) return "本地文件不存在: $local (当前目录 $cwd)"
        val target = remote ?: "/sdcard/${localFile.name}"
        val ok = runCatching { AdbManager.push(serial, localFile.absolutePath, target) }.getOrDefault(false)
        return if (ok) "✓ 已发送: ${localFile.absolutePath} → 目标:$target"
        else "✗ 发送失败 (目标设备拒绝或路径不可写): $target"
    }

    private fun transferPull(remote: String, local: String?): String {
        val serial = runCatching { AdbManager.listDevices().firstOrNull() }.getOrNull()
            ?: return "无已连接设备。请先在主页连接被控端 (OTG 或无线调试)。"
        val name = remote.trimEnd('/').substringAfterLast('/').ifBlank { "pulled_file" }
        val target = resolveLocal(local ?: name) ?: File(cwd, name)
        target.parentFile?.mkdirs()
        val ok = runCatching { AdbManager.pull(serial, remote, target.absolutePath) }.getOrDefault(false)
        return if (ok) "✓ 已接收: 目标:$remote → ${target.absolutePath}"
        else "✗ 接收失败 (目标文件不存在或无权限): $remote"
    }

    private fun resolveLocal(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (f.isAbsolute) return f
        return File(cwd, path)
    }

    private fun helpText() = """
        文件管理 (本地, 工作目录 $cwd):
          ls / cd / pwd / mkdir / touch / cp / mv / rm / cat / chmod
          echo 内容 > 文件    新建/写入文件
          例: mkdir test && cd test && echo hello > a.txt
        发送文件到被控设备:
          push <本地路径> [目标路径]   (别名 send, 缺省传到 /sdcard/)
          例: push update.zip
        从被控设备接收文件:
          pull <目标路径> [本地路径]   (别名 recv)
          例: pull /sdcard/DCIM/a.jpg
        被控设备:
          devices           查看已连接设备
          ashell <命令>     在被控设备上执行 shell (别名 adb shell)
        脚本 (智能识别, 无需选语言):
          :py <代码> / :js <代码> / :lua <代码> / :c <代码>
          ./test.py 或 python test.py 直接运行脚本文件
        Windows 命令别名: dir type copy move del ipconfig tasklist findstr
    """.trimIndent()

    // ---------- 兼容旧接口 ----------
    suspend fun execute(lang: String, cmd: String): ExecResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val output = try {
            when (lang) {
                "shell" -> runShellSmart(cmd)
                "python" -> execInterpreter("python3", "python", cmd, ".py")
                "javascript" -> execInterpreter("node", null, cmd, ".js")
                "lua" -> execInterpreter("lua", "luajit", cmd, ".lua")
                "c/c++" -> execCpp(cmd)
                "ai-llm" -> execLlm(cmd)
                else -> runShellSmart(cmd)
            }
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
        ExecResult(output, durationMs = System.currentTimeMillis() - start)
    }

    private fun execInterpreter(primary: String, fallback: String?, code: String, ext: String): String {
        val bin = which(primary) ?: (fallback?.let { which(it) })
            ?: return "$primary 未安装 (系统自带环境无此解释器; 文件管理可用 shell 命令)"
        // 单行且是已存在的脚本文件路径 (test.py / ./test.py / /sdcard/test.py)
        if (!code.contains('\n')) {
            val pathToken = code.trim().substringBefore(' ')
            if (pathToken.endsWith(ext)) {
                val scriptFile = resolveLocal(pathToken.removePrefix("./"))
                if (scriptFile != null && scriptFile.exists()) {
                    return runPersistent("$bin '${scriptFile.absolutePath}' 2>&1").first
                }
            }
        }
        return if (code.contains("\n") || code.length > 200) {
            val tmp = File(cwd.takeIf { File(it).canWrite() }
                ?: System.getProperty("java.io.tmpdir", "/data/local/tmp"),
                "jf_" + System.currentTimeMillis() + ext)
            try { tmp.writeText(code); runPersistent("$bin '${tmp.absolutePath}' 2>&1").first }
            finally { tmp.delete() }
        } else {
            runPersistent("$bin -c '${code.replace("'", "'\\''")}' 2>&1").first
        }
    }

    private fun execCpp(code: String): String {
        val cc = which("clang") ?: which("gcc")
            ?: return "未找到 C/C++ 编译器 (系统环境通常不自带 clang/gcc)"
        val tmpDir = File(cwd.takeIf { File(it).canWrite() }
            ?: System.getProperty("java.io.tmpdir", "/data/local/tmp"))
        val src = File(tmpDir, "jf_" + System.currentTimeMillis() + ".c")
        val bin = File(tmpDir, "jf_" + System.currentTimeMillis() + ".bin")
        return try {
            src.writeText(code)
            val compileOut = runPersistent("$cc '${src.absolutePath}' -o '${bin.absolutePath}' 2>&1").first
            if (!bin.exists()) return "编译失败:\n$compileOut"
            runPersistent("'${bin.absolutePath}' 2>&1").first.ifBlank { "(无输出)" }
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

    fun resetLlmEnv() { llmEnv = null }
}
