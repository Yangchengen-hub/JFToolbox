package com.jifeng.toolbox.tools

import android.util.Base64
import com.jifeng.toolbox.JFToolboxApp
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Shell 权限中枢 (类 Shizuku, 但更深度)。
 *
 * 架构:
 * 1. 在被控端 /data/local/tmp/jf_daemon.sh 放置 daemon 脚本 (uid 2000 shell 权限)
 * 2. daemon 监听 Unix socket /data/local/tmp/jf_daemon.sock (供设备上的其他 APP 使用)
 * 3. 控制端 (本 APP) 由于 AdbManager 未暴露 `adb forward`, 改为在本地 8848 端口
 *    起一个 ServerSocket 中继: 客户端 → 8848 → adb shell oneshot → daemon
 * 4. 其他本机 APP 连接 127.0.0.1:8848 即可获取 shell 权限 (作为中枢给其他应用授权)
 *
 * 即使 daemon 的 socket 监听未启动 (nc -U 不可用), ShellHub.exec 仍能通过
 * adb shell 直接调用 daemon 的 oneshot 模式工作, 只是其他设备端 APP 无法使用。
 */
object ShellHub {

    private const val TAG = "ShellHub"
    private const val REMOTE_SCRIPT = "/data/local/tmp/jf_daemon.sh"
    private const val ASSET_NAME = "jf_daemon.sh"
    private const val LOCAL_RELAY_PORT = 8848
    private const val REMOTE_SOCK = "/data/local/tmp/jf_daemon.sock"

    sealed class State {
        object Stopped : State()
        object Starting : State()
        data class Running(val pid: Int, val uptime: Long) : State()
        data class Failed(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: MutableStateFlow<State> = _state

    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile private var relayJob: Job? = null
    @Volatile private var relayServer: ServerSocket? = null

    // ========== deploy ==========

    /**
     * 部署 daemon 到被控端: assets 拷贝到本地 cache → adb push → chmod 755。
     * serial 参数仅为 API 兼容 (AdbManager 单连接, 实际忽略)。
     */
    suspend fun deploy(serial: String): Boolean = withContext(Dispatchers.IO) {
        if (!AdbManager.isConnected) {
            _state.value = State.Failed("ADB 未连接")
            return@withContext false
        }
        try {
            val ctx = JFToolboxApp.instance.appContext()
            // 把 assets/jf_daemon.sh 拷贝到本地 cache (push 需要文件路径)
            val cacheFile = File(ctx.cacheDir, ASSET_NAME)
            ctx.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            // push 到设备
            if (!AdbManager.push(serial, cacheFile.absolutePath, REMOTE_SCRIPT)) {
                _state.value = State.Failed("push daemon 脚本失败")
                return@withContext false
            }
            // chmod 755
            AdbManager.shell(serial, "chmod 755 $REMOTE_SCRIPT")
            Logger.i(TAG, "daemon 脚本已部署到 $REMOTE_SCRIPT")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "deploy 失败: ${e.message}")
            _state.value = State.Failed("deploy: ${e.message}")
            false
        }
    }

    // ========== start ==========

    /**
     * 启动 daemon:
     * 1. 在设备端后台运行 `jf_daemon.sh daemon` (nohup + 重定向 + &)
     * 2. 在本地 8848 端口启动中继 ServerSocket (供其他本机 APP 使用)
     * 3. 轮询 status 直到 Running 或超时 (3 秒)
     */
    suspend fun start(serial: String): Boolean = withContext(Dispatchers.IO) {
        if (!AdbManager.isConnected) {
            _state.value = State.Failed("ADB 未连接")
            return@withContext false
        }
        _state.value = State.Starting
        try {
            // 启动 daemon (后台 nohup, stdio 全部重定向, 避免阻塞 adb shell 流)
            // 用 sh -c 包一层, 确保 & 在子 shell 中生效
            val launch = "sh -c 'nohup $REMOTE_SCRIPT daemon </dev/null >/data/local/tmp/jf_daemon.out 2>&1 &'"
            AdbManager.shell(serial, launch)
            // 启动本地中继 (供其他 APP 通过 127.0.0.1:8848 拿到 shell 权限)
            startLocalRelay(serial)
            // 轮询 status
            var ok = false
            for (i in 1..10) {
                delay(300)
                val s = queryStatus(serial)
                if (s is State.Running) {
                    _state.value = s
                    ok = true
                    break
                }
            }
            if (!ok) {
                _state.value = State.Failed("daemon 启动超时 (可能 nc -U 不可用; oneshot 仍可用)")
                // 即便 daemon 监听没起来, 本地中继已启动, oneshot 模式仍可工作
            }
            ok
        } catch (e: Exception) {
            Logger.e(TAG, "start 异常: ${e.message}")
            _state.value = State.Failed("start: ${e.message}")
            false
        }
    }

    // ========== stop ==========

    /** 停止 daemon + 关闭本地中继。 */
    suspend fun stop(serial: String): Boolean = withContext(Dispatchers.IO) {
        try {
            AdbManager.shell(serial, "$REMOTE_SCRIPT stop")
        } catch (e: Exception) {
            Logger.w(TAG, "stop 远程失败: ${e.message}")
        }
        stopLocalRelay()
        _state.value = State.Stopped
        Logger.i(TAG, "daemon 已停止")
        true
    }

    // ========== exec ==========

    /**
     * 通过 daemon 执行 shell 命令。
     * 优先走本地中继 127.0.0.1:8848 (其他 APP 也走同一通道);
     * 若中继未启动则直接用 adb shell oneshot 模式。
     * 返回 stdout 字符串, 失败返回 null。
     */
    suspend fun exec(cmd: String, timeoutMs: Long = 10_000): String? = withContext(Dispatchers.IO) {
        if (!AdbManager.isConnected) return@withContext null
        val req = JSONObject().apply {
            put("cmd", "exec")
            put("command", cmd)
            put("timeout", timeoutMs.toInt())
        }.toString()
        // 优先本地中继 (其他 APP 也能用同一通道)
        val resp = tryLocalRelay(req, timeoutMs + 3_000)
            ?: tryOneshotDirect(req)
        if (resp == null) {
            Logger.w(TAG, "exec 失败: 中继与 oneshot 均无响应")
            return@withContext null
        }
        try {
            val json = JSONObject(resp)
            val exit = json.optInt("exit", -1)
            val stdout = json.optString("stdout", "")
            val stderr = json.optString("stderr", "")
            if (exit != 0) {
                Logger.w(TAG, "exec 退出码 $exit: $stderr")
            }
            stdout
        } catch (e: Exception) {
            Logger.e(TAG, "解析响应失败: ${e.message}, resp=$resp")
            null
        }
    }

    /** 本地中继是否已启动 (即 8848 端口监听中)。 */
    fun isForwarded(): Boolean = relayServer?.isClosed == false

    /** 查询 daemon 当前状态。 */
    suspend fun status(): State = queryStatus(AdbManager.currentSerial ?: "")

    private suspend fun queryStatus(serial: String): State = withContext(Dispatchers.IO) {
        if (serial.isEmpty() || !AdbManager.isConnected) return@withContext State.Stopped
        val out = AdbManager.shell(serial, "$REMOTE_SCRIPT status") ?: return@withContext State.Stopped
        try {
            val json = JSONObject(out.trim())
            val pid = json.optInt("pid", 0)
            val alive = json.optBoolean("alive", false)
            val uptime = json.optLong("uptime", 0L)
            if (pid > 0 && alive) State.Running(pid, uptime)
            else State.Stopped
        } catch (e: Exception) {
            Logger.w(TAG, "status 解析失败: ${e.message}, out=$out")
            State.Failed("status 解析失败")
        }
    }

    // ========== 本地中继 (8848 端口) ==========

    /**
     * 启动本地 ServerSocket 监听 8848, 把客户端的 JSON 请求通过 adb shell oneshot
     * 转发给设备端 daemon。
     *
     * 这样其他本机 APP 连接 127.0.0.1:8848 即可拿到 shell 权限 (作为中枢授权)。
     */
    private fun startLocalRelay(serial: String) {
        if (relayJob?.isActive == true) return
        relayJob = scope.launch {
            try {
                val server = ServerSocket(LOCAL_RELAY_PORT)
                relayServer = server
                Logger.i(TAG, "本地中继监听 127.0.0.1:$LOCAL_RELAY_PORT")
                while (isActive) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (server.isClosed) break
                        Logger.w(TAG, "accept 失败: ${e.message}")
                        continue
                    }
                    launch { handleRelayClient(client, serial) }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "中继启动失败: ${e.message}")
            }
        }
    }

    private fun stopLocalRelay() {
        try { relayServer?.close() } catch (_: Exception) {}
        relayServer = null
        relayJob?.cancel()
        relayJob = null
    }

    /** 处理一个中继客户端: 读一行 JSON 请求 → adb shell oneshot → 写 JSON 响应。 */
    private suspend fun handleRelayClient(client: Socket, serial: String) {
        try {
            client.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = OutputStreamWriter(client.getOutputStream())
            val line = reader.readLine() ?: return
            // base64 编码后通过 adb shell 传给 daemon oneshot 模式 (避免 shell 元字符问题)
            val b64 = Base64.encodeToString(line.toByteArray(), Base64.NO_WRAP)
            val resp = withContext(Dispatchers.IO) {
                AdbManager.shell(serial, "$REMOTE_SCRIPT oneshot $b64")
            } ?: "{\"exit\":-1,\"stdout\":\"\",\"stderr\":\"adb shell failed\"}"
            writer.write(resp)
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            Logger.w(TAG, "中继客户端处理失败: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    /** 尝试连接本地中继 8848 并发送请求, 返回响应字符串。 */
    private suspend fun tryLocalRelay(req: String, timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        if (!isForwarded()) return@withContext null
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress("127.0.0.1", LOCAL_RELAY_PORT), 2_000)
                sock.soTimeout = timeoutMs.toInt().coerceAtLeast(3_000)
                val out = OutputStreamWriter(sock.getOutputStream())
                out.write(req); out.write("\n"); out.flush()
                BufferedReader(InputStreamReader(sock.getInputStream())).readLine()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "本地中继调用失败: ${e.message}")
            null
        }
    }

    /** 直接通过 adb shell oneshot 模式调用 daemon (不走中继)。 */
    private suspend fun tryOneshotDirect(req: String): String? = withContext(Dispatchers.IO) {
        val serial = AdbManager.currentSerial ?: return@withContext null
        val b64 = Base64.encodeToString(req.toByteArray(), Base64.NO_WRAP)
        AdbManager.shell(serial, "$REMOTE_SCRIPT oneshot $b64")
    }
}
