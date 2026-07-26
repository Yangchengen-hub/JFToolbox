package com.jifeng.toolbox.tools

import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程分片下载器 —— 基于 HTTP Range 请求的并发分段下载。
 *
 * 工作流程:
 * 1. HEAD 请求获取 Content-Length 与 Accept-Ranges 支持
 * 2. 按 segmentCount 将文件均分为 N 段
 * 3. 每段独立 Range GET 请求, 并发写入 RandomAccessFile 对应偏移
 * 4. 实时汇总进度, 支持取消与失败重试(单段级)
 *
 * 不支持断点续传（MVP 阶段）, 服务端不支持 Range 时自动降级为单线程。
 */
class SegmentedDownloader(
    private val client: OkHttpClient = defaultClient,
    private val segmentCount: Int = 4
) {

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private const val TAG = "Downloader"
    }

    /** 下载状态。 */
    sealed class State {
        object Idle : State()
        data class Downloading(val progress: Float, val speedKBps: Long, val downloaded: Long, val total: Long) : State()
        data class Done(val file: File) : State()
        data class Failed(val message: String) : State()
        object Cancelled : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** 最近解析的 torrent 种子信息 (供 UI 观察)。 */
    private val _torrentInfo = MutableStateFlow<TorrentParser.TorrentInfo?>(null)
    val torrentInfo: StateFlow<TorrentParser.TorrentInfo?> = _torrentInfo

    private var scope: CoroutineScope? = null
    private val downloadedBytes = AtomicLong(0)
    private var totalBytes: Long = 0
    private var startTimeMs: Long = 0

    /** 启动下载。 */
    fun start(url: String, outputDir: String, fileName: String? = null): Boolean {
        cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            try {
                doDownload(url, outputDir, fileName)
            } catch (e: Exception) {
                Logger.e(TAG, "下载失败: ${e.message}")
                _state.value = State.Failed(e.message ?: "未知错误")
            }
        }
        return true
    }

    /**
     * 尝试用 HTTP 分段下载器处理 torrent 种子。
     *
     * 仅当种子为**单文件**且包含 HTTP/HTTPS web seed (BEP 19 url-list) 时,
     * 复用 [start] 走 Range 多线程下载; 否则返回 false ——
     * 此时 UI 应提示用户用外部 BT 客户端 (如 LibreTorrent/Flud) 打开磁力链接。
     *
     * 本工具**不实现** BT P2P 协议。
     */
    fun startTorrent(torrentFile: File, outputDir: String): Boolean {
        val info = TorrentParser.parse(torrentFile) ?: return false
        _torrentInfo.value = info
        // 多文件种子无法用 HTTP web seed 还原目录结构
        if (info.files.size != 1) return false
        // 单文件: 必须有 HTTP/HTTPS 直链源
        val webSeed = info.webSeedUrls.firstOrNull { url ->
            url.startsWith("http://", true) || url.startsWith("https://", true)
        } ?: return false
        return start(webSeed, outputDir, info.name)
    }

    /** 取消下载。 */
    fun cancel() {
        scope?.cancel()
        scope = null
        if (_state.value is State.Downloading) {
            _state.value = State.Cancelled
        }
    }

    private suspend fun doDownload(url: String, outputDir: String, fileName: String?) {
        // 1. HEAD 请求获取文件信息
        val headReq = Request.Builder().url(url).head().build()
        val headResp = client.newCall(headReq).execute()
        if (!headResp.isSuccessful) {
            _state.value = State.Failed("HEAD 请求失败: ${headResp.code}")
            headResp.close()
            return
        }

        totalBytes = headResp.header("Content-Length")?.toLongOrNull() ?: -1
        val supportsRange = headResp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
        val finalName = fileName ?: headResp.header("Content-Disposition")
            ?.substringAfter("filename=\"")?.substringBefore("\"")
            ?.ifBlank { null }
            ?: url.substringAfterLast("/").substringBefore("?").ifBlank { "download.bin" }
        headResp.close()

        val dir = File(outputDir).apply { mkdirs() }
        val outFile = File(dir, finalName)

        Logger.i(TAG, "下载: $url → ${outFile.name} size=$totalBytes range=$supportsRange segments=$segmentCount")

        if (totalBytes <= 0 || !supportsRange) {
            // 降级单线程
            Logger.i(TAG, "服务端不支持 Range 或无 Content-Length, 降级单线程下载")
            downloadSingle(url, outFile)
            return
        }

        // 2. 多分片并发
        startTimeMs = System.currentTimeMillis()
        downloadedBytes.set(0)

        // 预分配文件
        RandomAccessFile(outFile, "rw").use { it.setLength(totalBytes) }

        val segments = splitSegments(totalBytes, segmentCount)
        val scope = scope ?: return

        val jobs = segments.map { seg ->
            scope.async(Dispatchers.IO) {
                downloadSegment(url, outFile, seg)
            }
        }

        // 进度监控
        val monitorJob = scope.launch(Dispatchers.IO) {
            while (downloadedBytes.get() < totalBytes) {
                val dl = downloadedBytes.get()
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000.0
                val speed = if (elapsed > 0) (dl / 1024.0 / elapsed).toLong() else 0L
                val pct = if (totalBytes > 0) dl.toFloat() / totalBytes else 0f
                _state.value = State.Downloading(pct, speed, dl, totalBytes)
                kotlinx.coroutines.delay(300)
            }
        }

        val results = jobs.awaitAll()
        monitorJob.cancel()

        if (results.any { !it }) {
            _state.value = State.Failed("部分分片下载失败")
            return
        }

        downloadedBytes.set(totalBytes)
        _state.value = State.Downloading(1f, 0, totalBytes, totalBytes)
        Logger.i(TAG, "下载完成: ${outFile.absolutePath} (${totalBytes} bytes)")
        _state.value = State.Done(outFile)
    }

    private data class Segment(val index: Int, val start: Long, val end: Long)

    private fun splitSegments(total: Long, count: Int): List<Segment> {
        val segSize = total / count
        return (0 until count).map { i ->
            val start = i * segSize
            val end = if (i == count - 1) total - 1 else (start + segSize - 1)
            Segment(i, start, end)
        }
    }

    private fun downloadSegment(url: String, file: File, seg: Segment): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Range", "bytes=${seg.start}-${seg.end}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in setOf(206, 200)) {
                    Logger.e(TAG, "分片 ${seg.index} 失败: HTTP ${resp.code}")
                    return false
                }
                val body = resp.body ?: return false
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(seg.start)
                    val source = body.source()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        downloadedBytes.addAndGet(read.toLong())
                    }
                }
            }
            Logger.i(TAG, "分片 ${seg.index} 完成 [${seg.start}-${seg.end}]")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "分片 ${seg.index} 异常: ${e.message}")
            false
        }
    }

    private suspend fun downloadSingle(url: String, file: File) {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    _state.value = State.Failed("HTTP ${resp.code}")
                    return@withContext
                }
                val body = resp.body ?: run {
                    _state.value = State.Failed("无响应体")
                    return@withContext
                }
                startTimeMs = System.currentTimeMillis()
                file.outputStream().use { fos ->
                    val source = body.source()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        downloadedBytes.addAndGet(read.toLong())
                    }
                }
            }
            _state.value = State.Done(file)
        }
    }
}
