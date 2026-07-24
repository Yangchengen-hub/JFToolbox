package com.jifeng.toolbox.terminal

import com.jifeng.toolbox.core.Logger
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * SSH 客户端 —— 通过 JSch 连接远程主机执行命令。
 *
 * 支持:
 * - 密码认证 / 密钥认证
 * - 命令执行 (同步返回 stdout + stderr)
 * - 交互式会话 (维持连接, 多次执行)
 *
 * 用途: 超级终端 SSH 模式, 允许从手机 SSH 到远程服务器执行命令。
 */
class SshClient {

    private var session: Session? = null

    /** 连接配置。 */
    data class SshConfig(
        val host: String,
        val port: Int = 22,
        val username: String,
        val password: String? = null,
        val privateKeyPath: String? = null,
        val passphrase: String? = null,
        val timeoutMs: Int = 15_000
    )

    /** 是否已连接。 */
    val isConnected: Boolean get() = session?.isConnected == true

    /**
     * 建立 SSH 连接。
     */
    suspend fun connect(config: SshConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val jsch = JSch()

            // 密钥认证
            if (!config.privateKeyPath.isNullOrBlank()) {
                val keyFile = File(config.privateKeyPath)
                if (keyFile.exists()) {
                    if (!config.passphrase.isNullOrBlank()) {
                        jsch.addIdentity(config.privateKeyPath, config.passphrase)
                    } else {
                        jsch.addIdentity(config.privateKeyPath)
                    }
                    Logger.i("SshClient", "使用密钥认证: ${config.privateKeyPath}")
                }
            }

            val sess = jsch.getSession(config.username, config.host, config.port)

            // 密码认证
            if (!config.password.isNullOrBlank()) {
                sess.setPassword(config.password)
            }

            // 严格主机密钥检查关闭 (MVP 阶段)
            sess.setConfig("StrictHostKeyChecking", "no")
            sess.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            sess.timeout = config.timeoutMs

            Logger.i("SshClient", "连接 ${config.username}@${config.host}:${config.port} ...")
            sess.connect(config.timeoutMs)
            session = sess
            Logger.i("SshClient", "SSH 连接成功")
            true
        } catch (e: Exception) {
            Logger.e("SshClient", "SSH 连接失败: ${e.message}")
            false
        }
    }

    /**
     * 执行单条命令并返回输出。
     */
    suspend fun execute(command: String, timeoutMs: Int = 30_000): String = withContext(Dispatchers.IO) {
        val sess = session ?: return@withContext "错误: SSH 未连接"
        if (!sess.isConnected) return@withContext "错误: SSH 会话已断开"

        try {
            val channel = sess.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setErrStream(java.io.ByteArrayOutputStream())

            val input = channel.inputStream
            val errStream = ByteArrayOutputStream()
            channel.setErrStream(errStream)

            channel.connect(timeoutMs)

            val output = StringBuilder()
            val buf = ByteArray(4096)
            val startTime = System.currentTimeMillis()

            while (true) {
                while (input.available() > 0) {
                    val n = input.read(buf)
                    if (n > 0) output.append(String(buf, 0, n))
                }
                if (channel.isClosed) {
                    if (input.available() > 0) continue
                    break
                }
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    output.append("\n(超时 ${timeoutMs}ms)")
                    break
                }
                Thread.sleep(50)
            }

            val stderr = errStream.toString()
            val exitCode = channel.exitStatus
            channel.disconnect()

            val result = StringBuilder()
            if (output.isNotBlank()) result.append(output.toString())
            if (stderr.isNotBlank()) {
                if (result.isNotBlank()) result.append("\n")
                result.append("[stderr]\n").append(stderr)
            }
            result.append("\n[exit: $exitCode]")
            result.toString()
        } catch (e: Exception) {
            Logger.e("SshClient", "执行失败: ${e.message}")
            "错误: ${e.message}"
        }
    }

    /** 断开连接。 */
    fun disconnect() {
        session?.let {
            try { it.disconnect() } catch (_: Exception) {}
        }
        session = null
    }
}
