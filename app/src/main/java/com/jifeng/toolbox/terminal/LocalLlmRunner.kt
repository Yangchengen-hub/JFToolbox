package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地 LLM 推理引擎 —— 在被控设备上检测并调用本地 AI 模型。
 *
 * 实现策略 (诚实, 不虚标能力):
 * 1. 优先检测被控设备上的 Termux 环境 + llama.cpp / ollama
 * 2. 检测 /data/local/tmp/ 下的 llama CLI 工具
 * 3. 如找到可用工具, 推送 prompt 并执行推理
 * 4. 如未找到, 诚实返回"未检测到本地 LLM 运行环境"
 *
 * 不内置模型文件 (APK 体积考量), 由用户自行部署量化模型到设备。
 *
 * 支持的推理后端:
 * - llama.cpp (main / llama-cli)
 * - ollama (ollama run)
 * - Termux 中的 python + transformers (回退)
 */
object LocalLlmRunner {

    private const val TAG = "LocalLLM"

    /** 检测到的 LLM 后端类型。 */
    enum class LlmBackend(val displayName: String, val command: String) {
        OLLAMA("Ollama", "ollama"),
        LLAMA_CPP("llama.cpp", "llama-cli"),
        LLAMA_CPP_LEGACY("llama.cpp (旧版)", "main"),
        TERMUX_PYTHON("Termux + Python", "python3"),
        NONE("未检测到", "");

        val isAvailable: Boolean get() = this != NONE
    }

    data class LlmEnvironment(
        val backend: LlmBackend,
        val binaryPath: String,
        val modelPath: String = "",
        val versionInfo: String = ""
    )

    /**
     * 在被控设备上探测可用的 LLM 后端。
     */
    suspend fun detect(serial: String): LlmEnvironment = withContext(Dispatchers.IO) {
        val adb = AdbManager.instance
        if (!adb.isConnected) return@withContext LlmEnvironment(LlmBackend.NONE, "")

        // 1. 检测 ollama
        val ollamaPath = adb.shell(serial, "command -v ollama 2>/dev/null || which ollama 2>/dev/null").orEmpty().trim()
        if (ollamaPath.isNotBlank() && ollamaPath != "ollama") {
            val ver = adb.shell(serial, "ollama --version 2>/dev/null").orEmpty().trim()
            // 查找已安装的模型
            val models = adb.shell(serial, "ollama list 2>/dev/null").orEmpty()
            val firstModel = models.lines().drop(1).firstOrNull()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
            Logger.i(TAG, "检测到 Ollama v$ver, 模型: $firstModel")
            return@withContext LlmEnvironment(LlmBackend.OLLAMA, ollamaPath, firstModel, ver)
        }

        // 2. 检测 llama-cli (新版 llama.cpp)
        val llamaCliPath = adb.shell(serial, "command -v llama-cli 2>/dev/null").orEmpty().trim()
        if (llamaCliPath.isNotBlank()) {
            // 查找模型文件
            val model = findModelFile(serial, adb)
            Logger.i(TAG, "检测到 llama.cpp (llama-cli), 模型: $model")
            return@withContext LlmEnvironment(LlmBackend.LLAMA_CPP, llamaCliPath, model)
        }

        // 3. 检测 main (旧版 llama.cpp)
        val mainPath = adb.shell(serial, "ls /data/local/tmp/main 2>/dev/null").orEmpty().trim()
        if (mainPath.isNotBlank()) {
            val model = findModelFile(serial, adb)
            Logger.i(TAG, "检测到 llama.cpp (main), 模型: $model")
            return@withContext LlmEnvironment(LlmBackend.LLAMA_CPP_LEGACY, "/data/local/tmp/main", model)
        }

        // 4. 检测 Termux Python
        val pyPath = adb.shell(serial, "command -v python3 2>/dev/null || which python3 2>/dev/null").orEmpty().trim()
        if (pyPath.isNotBlank()) {
            val tfCheck = adb.shell(serial, "python3 -c 'import transformers; print(transformers.__version__)' 2>/dev/null").orEmpty().trim()
            if (tfCheck.isNotBlank()) {
                Logger.i(TAG, "检测到 Termux Python + transformers v$tfCheck")
                return@withContext LlmEnvironment(LlmBackend.TERMUX_PYTHON, pyPath, "", tfCheck)
            }
        }

        Logger.i(TAG, "未检测到本地 LLM 环境")
        LlmEnvironment(LlmBackend.NONE, "")
    }

    /**
     * 在被控设备上搜索 .gguf 模型文件。
     */
    private fun findModelFile(serial: String, adb: AdbManager): String {
        val candidates = listOf(
            "/sdcard/llm/", "/data/local/tmp/", "/sdcard/Download/models/",
            "/storage/emulated/0/llm/"
        )
        for (dir in candidates) {
            val out = adb.shell(serial, "find '$dir' -name '*.gguf' -type f 2>/dev/null | head -1").orEmpty().trim()
            if (out.isNotBlank() && !out.contains("No such")) return out
        }
        return ""
    }

    /**
     * 执行 LLM 推理。
     * @param prompt 用户输入的提示词
     * @return 推理结果文本
     */
    suspend fun infer(serial: String, env: LlmEnvironment, prompt: String): String = withContext(Dispatchers.IO) {
        val adb = AdbManager.instance
        if (!env.backend.isAvailable) {
            return@withContext "❌ 未检测到本地 LLM 运行环境。\n\n" +
                "请在被控设备上安装以下任一方案:\n" +
                "1. Termux + ollama (推荐): pkg install ollama && ollama pull qwen2:1.5b\n" +
                "2. llama.cpp: 编译后 push 到 /data/local/tmp/, 模型放 /sdcard/llm/\n" +
                "3. Termux + Python transformers: pip install transformers torch\n\n" +
                "极风工具箱不内置模型文件 (APK 体积考量), 需用户自行部署量化模型。"
        }

        Logger.i(TAG, "推理 [${env.backend.displayName}]: ${prompt.take(80)}...")

        return@withContext when (env.backend) {
            LlmBackend.OLLAMA -> {
                if (env.modelPath.isBlank()) {
                    "❌ Ollama 已安装但无可用模型。\n请先拉取模型: ollama pull qwen2:1.5b"
                } else {
                    // ollama run <model> "<prompt>" (非交互模式)
                    val escaped = prompt.replace("'", "'\\''").replace("\"", "\\\"")
                    val cmd = "ollama run ${env.modelPath} \"$escaped\" 2>&1"
                    adb.shell(serial, cmd) ?: "(推理失败)"
                }
            }
            LlmBackend.LLAMA_CPP, LlmBackend.LLAMA_CPP_LEGACY -> {
                if (env.modelPath.isBlank()) {
                    "❌ llama.cpp 已安装但未找到 .gguf 模型文件。\n" +
                        "请将量化模型 (.gguf) 放到 /sdcard/llm/ 或 /data/local/tmp/"
                } else {
                    val escaped = prompt.replace("'", "'\\''")
                    val cmd = "${env.binaryPath} -m ${env.modelPath} -p '$escaped' -n 512 --no-display-prompt 2>&1"
                    adb.shell(serial, cmd) ?: "(推理失败)"
                }
            }
            LlmBackend.TERMUX_PYTHON -> {
                // 使用 transformers pipeline
                val pyScript = """
                    from transformers import pipeline
                    import sys
                    pipe = pipeline("text-generation", model="Qwen/Qwen2-0.5B", device_map="auto")
                    result = pipe(sys.argv[1], max_new_tokens=256, do_sample=True, temperature=0.7)
                    print(result[0]["generated_text"])
                """.trimIndent()
                val escaped = prompt.replace("'", "'\\''")
                val cmd = "python3 -c '$pyScript' '$escaped' 2>&1"
                adb.shell(serial, cmd) ?: "(推理失败)"
            }
            LlmBackend.NONE -> "未检测到本地 LLM 环境"
        }
    }
}
