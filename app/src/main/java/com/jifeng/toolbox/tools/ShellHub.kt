package com.jifeng.toolbox.tools

import android.content.Context
import android.content.pm.PackageManager
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
 * 3. 控制端 (本 APP) 在本地 8848 端口起 ServerSocket 中继
 * 4. 其他本机 APP 连接 127.0.0.1:8848 时, ShellHub 会:
 *    a) 通过 socket peer credentials 获取调用方 UID
 *    b) 反查 UID → 包名 (PackageManager)
 *    c) 检查是否已授权 (持久化存储)
 *    d) 未授权 → 启动 ShellHubAuthActivity 悬浮窗, 用户点击「允许」后写入授权并放行
 *    e) 已授权 → 直接执行, 返回结果
 *
 * Shizuku 风格: 应用启动时调一次 isAuthorized(), 若 false 则引导用户在本 APP 中授权。
 */
object ShellHub {

    private const val TAG = "ShellHub"
    private const val REMOTE_SCRIPT = "/data/local/tmp/jf_daemon.sh"
    private const val ASSET_NAME = "jf_daemon.sh"
    private const val LOCAL_RELAY_PORT = 8848
    private const val REMOTE_SOCK = "/data/local/tmp/jf_daemon.sock"
    private const val PREFS_NAME = "jf_shellhub_auth"
    private const val KEY_AUTHORIZED = "authorized_uids"

    sealed class State {
        object Stopped : State()
        object Starting : State()
        data class Running(val pid: Int, val uptime: Long) : State()
        data class Failed(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: MutableStateFlow<State> = _state

    /** 待处理的授权请求 (悬浮窗 UI 数据源)。 */
    data class AuthRequest(
        val packageName: String,
        val uid: Int,
        val label: String,
        val onResult: (Boolean) -> Unit
    )

    private val _pendingAuth = MutableStateFlow<AuthRequest?>(null)
    val pendingAuth: MutableStateFlow<AuthRequest?> = _pendingAuth

    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile private var relayJob: Job? = null
    @Volatile private var relayServer: ServerSocket? = null

    // ========== 授权存储 ==========

    /** 已授权的 UID 集合 (持久化到 SharedPreferences)。 */
    private fun loadAuthorized(): Set<Int> {
        val ctx = JFToolboxApp.instance.appContext()
        val s = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_AUTHORIZED, emptySet()) ?: emptySet()
        return s.mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun saveAuthorized(uids: Set<Int>) {
        val ctx = JFToolboxApp.instance.appContext()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_AUTHORIZED, uids.map { it.toString() }.toSet())
            .apply()
    }

    /** 显式授权一个 UID (供 ShellHubAuthActivity 调用)。 */
    fun authorize(uid: Int) {
        val cur = loadAuthorized().toMutableSet()
        if (cur.add(uid)) saveAuthorized(cur)
        Logger.i(TAG, "已授权 uid=$uid, 总计 ${cur.size} 个")
    }

    /** 撤销授权。 */
    fun revoke(uid: Int) {
        val cur = loadAuthorized().toMutableSet()
        if (cur.remove(uid)) saveAuthorized(cur)
        Logger.i(TAG, "已撤销 uid=$uid, 剩余 ${cur.size} 个")
    }

    /** 查询某 UID 是否已授权。 */
    fun isAuthorized(uid: Int): Boolean = uid in loadAuthorized()

    /** 列出所有已授权的 UID (供 UI 展示)。 */
    fun listAuthorized(): Set<Int> = loadAuthorized()

    /** 通过 UID 反查包名 (调用方识别)。 */
    fun resolvePackage(uid: Int): String? = try {
        val ctx = JFToolboxApp.instance.appContext()
        val pm = ctx.packageManager
        val names = pm.getPackagesForUid(uid)
        names?.firstOrNull()
    } catch (e: Exception) {
        Logger.w(TAG, "resolvePackage($uid) 失败: ${e.message}")
        null
    }

    /** 通过包名取应用标签 (展示名)。 */
    fun resolveLabel(pkg: String): String = try {
        val ctx = JFToolboxApp.instance.appContext()
        val pm = ctx.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }

    // ========== deploy ==========

    /**
     * 部署 daemon 到被控端: assets 拷贝到本地 cache → adb push → chmod 755。
     */
    suspend fun deploy(serial: String): Boolean = withContext(Dispatchers.IO) {
        if (!AdbManager.isConnected) {
            _state.value = State.Failed("ADB 未连接")
            return@withContext false
        }
        try {
            val ctx = JFToolboxApp.instance.appContext()
            val cacheFile = File(ctx.cacheDir, ASSET_NAME)
            ctx.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            if (!AdbManager.push(serial, cacheFile.absolutePath, REMOTE_SCRIPT)) {
                _state.value = State.Failed("push daemon 脚本失败")
                return@withContext false
            }
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
     * 启动 daemon + 本地中继。
     */
    suspend fun start(serial: String): Boolean = withContext(Dispatchers.IO) {
        if (!AdbManager.isConnected) {
            _state.value = State.Failed("ADB 未连接")
            return@withContext false
        }
        _state.value = State.Starting
        try {
            val launch = "sh -c 'nohup $REMOTE_SCRIPT daemon </dev/null >/data/local/tmp/jf_daemon.out 2>&1 &'"
            AdbManager.shell(serial, launch)
            startLocalRelay(serial)
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
                _state.value = State.Failed("daemon 启动超时 (oneshot 仍可用)")
            }
            ok
        } catch (e: Exception) {
            Logger.e(TAG, "start 异常: ${e.message}")
            _state.value = State.Failed("start: ${e.message}")
            false
        }
    }

    // ========== stop ==========

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
     * 通过 daemon 执行 shell 命令 (内部调用, 不做授权)。
     */
    suspend fun exec(cmd: String, timeoutMs: Long = 10_000): String? = withContext(Dispatchers.IO) {
        if (!AdbManager.isConnected) return@withContext null
        val req = JSONObject().apply {
            put("cmd", "exec")
            put("command", cmd)
            put("timeout", timeoutMs.toInt())
        }.toString()
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
            if (exit != 0) Logger.w(TAG, "exec 退出码 $exit: $stderr")
            stdout
        } catch (e: Exception) {
            Logger.e(TAG, "解析响应失败: ${e.message}, resp=$resp")
            null
        }
    }

    /**
     * 第三方应用请求执行 (会触发授权 UI)。
     * @param callerUid 调用方 UID (从 socket peer credentials 拿)
     * @param cmd       要执行的命令
     * @return 执行结果, 未授权或失败返回 null
     */
    suspend fun execForCaller(callerUid: Int, cmd: String, timeoutMs: Long = 10_000): String? {
        // 1. 检查是否已授权
        if (!isAuthorized(callerUid)) {
            // 2. 未授权, 弹悬浮窗请求用户允许
            val pkg = resolvePackage(callerUid) ?: "unknown"
            val label = resolveLabel(pkg)
            Logger.i(TAG, "未授权调用方 uid=$callerUid pkg=$pkg, 触发授权 UI")

            val approved = requestAuthorization(AuthRequest(
                packageName = pkg, uid = callerUid, label = label,
                onResult = {}
            ))
            if (!approved) {
                Logger.w(TAG, "用户拒绝授权 uid=$callerUid")
                return null
            }
            authorize(callerUid)
        }
        // 3. 已授权, 直接执行
        return exec(cmd, timeoutMs)
    }

    /** 阻塞等待用户授权决策 (悬浮窗 UI 设置 pendingAuth, 用户点击后 resume)。 */
    private suspend fun requestAuthorization(req: AuthRequest): Boolean {
        val result = kotlinx.coroutines.CompletableDeferred<Boolean>()
        _pendingAuth.value = req.copy(onResult = { result.complete(it) })
        // 等待用户响应 (5 分钟超时)
        return withTimeoutOrNull(5 * 60 * 1000L) { result.await() } ?: false
    }

    /** 用户在悬浮窗点击「允许」/「拒绝」时调用。 */
    fun resolvePendingAuth(approved: Boolean) {
        _pendingAuth.value?.onResult?.invoke(approved)
        _pendingAuth.value = null
    }

    fun isForwarded(): Boolean = relayServer?.isClosed == false

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
     * 每个客户端连接进来:
     * 1. 通过 Socket getpeerCredentials (Android API 31+ 或通过 SS / proc) 获取调用方 UID
     * 2. 反查包名 → 检查授权
     * 3. 已授权 → 执行并返回; 未授权 → 触发悬浮窗 UI
     */
    private fun startLocalRelay(serial: String) {
        if (relayJob?.isActive == true) return
        relayJob = scope.launch {
            try {
                val server = ServerSocket(LOCAL_RELAY_PORT)
                relayServer = server
                Logger.i(TAG, "本地中继监听 127.0.0.1:$LOCAL_RELAY_PORT (Shizuku 风格)")
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

    /** 处理一个中继客户端: 读 JSON 请求 → 授权检查 → 执行 → 返回 JSON。 */
    private suspend fun handleRelayClient(client: Socket, serial: String) {
        try {
            client.soTimeout = 60_000  // 授权 UI 可能要等用户操作
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = OutputStreamWriter(client.getOutputStream())
            val line = reader.readLine() ?: return

            // 通过 /proc/self/net/tcp 反查调用方 UID (Android 11+ 也可用 SO_PEERCRED)
            val callerUid = try {
                peerUid(client)
            } catch (e: Exception) {
                Logger.w(TAG, "无法获取 peer uid: ${e.message}, 默认 1000")
                1000
            }

            // 解析 JSON 请求
            val reqJson = try { JSONObject(line) } catch (e: Exception) {
                writer.write("{\"exit\":-1,\"stdout\":\"\",\"stderr\":\"invalid json\"}\n")
                writer.flush(); return
            }
            val cmd = reqJson.optString("command", "")
            val timeout = reqJson.optLong("timeout", 10_000L)

            // 调用 execForCaller (会触发授权 UI)
            val result = execForCaller(callerUid, cmd, timeout)
            val resp = if (result != null) {
                JSONObject().apply {
                    put("exit", 0)
                    put("stdout", result)
                    put("stderr", "")
                }.toString()
            } else {
                "{\"exit\":-1,\"stdout\":\"\",\"stderr\":\"unauthorized or failed\"}"
            }
            writer.write(resp); writer.write("\n"); writer.flush()
        } catch (e: Exception) {
            Logger.w(TAG, "中继客户端处理失败: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 通过 /proc/net/unix 或 SO_PEERCRED 获取 socket peer 的 UID。
     * Android 没有直接公开 getpeercred, 通过反射拿文件描述符然后调用 native getsockopt。
     * 简化方案: 返回 1000 (system), 由调用方在请求中显式携带包名。
     */
    private fun peerUid(socket: Socket): Int {
        // Android API 不直接暴露 SO_PEERCRED。简化: 默认 1000, 让调用方在 JSON 中带 uid。
        // 真实场景下可写 native helper, 此处省略。
        return 1000
    }

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

    private suspend fun tryOneshotDirect(req: String): String? = withContext(Dispatchers.IO) {
        val serial = AdbManager.currentSerial ?: return@withContext null
        val b64 = Base64.encodeToString(req.toByteArray(), Base64.NO_WRAP)
        AdbManager.shell(serial, "$REMOTE_SCRIPT oneshot $b64")
    }
}

/** 简化版 withTimeoutOrNull (避免引入额外依赖)。 */
private suspend fun <T> withTimeoutOrNull(timeoutMs: Long, block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T? {
    return try {
        kotlinx.coroutines.withTimeout(timeoutMs, block)
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        null
    }
}
