package com.jifeng.toolbox.ui.downloader

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.jifeng.toolbox.databinding.ActivityDownloaderBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * 全线程下载器 MVP：HTTP/HTTPS 多线程分片下载。
 */
class DownloaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloaderBinding
    private val client = OkHttpClient()
    private val THREADS = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityDownloaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            val url = binding.edtUrl.text.toString().trim()
            if (url.isBlank()) { toast("请输入下载链接"); return@setOnClickListener }
            startDownload(url)
        }
    }

    private fun startDownload(url: String) {
        binding.txtLog.append("开始下载: $url\n")
        CoroutineScope(Dispatchers.Main).launch {
            val length = withContext(Dispatchers.IO) { headContentLength(url) }
            if (length <= 0) { binding.txtLog.append("❌ 无法获取文件大小\n"); return@launch }
            binding.txtLog.append("文件大小: ${length / 1048576} MB，分 $THREADS 线程\n")
            val out = File(cacheDir, "dl_${System.currentTimeMillis()}.bin")
            val partSize = length / THREADS
            val jobs = (0 until THREADS).map { i ->
                val start = i * partSize
                val end = if (i == THREADS - 1) length - 1 else (start + partSize - 1)
                downloadRange(url, out, start, end, i)
            }
            // 简单串行 MVP，后续改并行 + 进度回调
            jobs.forEachIndexed { idx, job ->
                withContext(Dispatchers.IO) { job() }
                binding.txtLog.append("  线程 ${idx + 1}/$THREADS 完成\n")
            }
            binding.txtLog.append("✅ 下载完成: ${out.absolutePath}\n")
        }
    }

    private fun headContentLength(url: String): Long = try {
        val req = Request.Builder().url(url).head().build()
        client.newCall(req).execute().use { it.header("Content-Length")?.toLongOrNull() ?: -1 }
    } catch (_: Exception) { -1 }

    private fun downloadRange(url: String, out: File, start: Long, end: Long, partIdx: Int): () -> Unit = {
        val req = Request.Builder().url(url)
            .header("Range", "bytes=$start-$end").build()
        client.newCall(req).execute().use { resp ->
            val buf = resp.body?.bytes() ?: return@use
            RandomAccessFile(out, "rw").use { raf ->
                raf.seek(start)
                raf.write(buf)
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
