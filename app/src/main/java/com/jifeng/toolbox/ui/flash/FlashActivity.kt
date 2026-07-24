package com.jifeng.toolbox.ui.flash

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import com.jifeng.toolbox.databinding.ActivityFlashBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FlashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityFlashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickZip.setOnClickListener { pickFile("application/zip", REQ_ZIP) }
        binding.btnPickImg.setOnClickListener { pickFile("application/octet-stream", REQ_IMG) }
        binding.btnFlashZip.setOnClickListener { doFlashZip() }
        binding.btnFlashImg.setOnClickListener { doFlashImg() }
        binding.btnValidateZip.setOnClickListener { validateZip() }

        Logger.subscribe { line ->
            runOnUiThread { binding.txtLog.append("$line\n") }
        }
    }

    private fun pickFile(mime: String, req: Int) {
        val i = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = mime; addCategory(android.content.Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(android.content.Intent.createChooser(i, "选择文件"), req)
    }

    private var pickedZip: String? = null
    private var pickedImg: String? = null
    private var pickedPartition: String = "boot"

    override fun onActivityResult(req: Int, res: Int, data: android.content.Intent?) {
        super.onActivityResult(req, res, data)
        val path = data?.data?.let { uriToFile(it) } ?: return
        when (req) {
            REQ_ZIP -> { pickedZip = path; binding.txtPickedZip.text = path }
            REQ_IMG -> { pickedImg = path; binding.txtPickedImg.text = path }
        }
    }

    private fun uriToFile(uri: android.net.Uri): String? {
        val cr = contentResolver
        val name = "jf_upload_${System.currentTimeMillis()}"
        val tmp = File(cacheDir, name)
        cr.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return tmp.absolutePath
    }

    private fun validateZip() {
        val zip = pickedZip ?: return toast("请先选择 ZIP")
        binding.txtLog.append("正在校验 ZIP...\n")
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) {
                // MVP: 检查是否为 fastboot 卡刷包（含 META-INF/com/android/metadata 或 partition xml）
                val f = File(zip)
                if (!f.exists()) return@withContext false
                // 用 commons-compress 快速嗅探
                try {
                    org.apache.commons.compress.archivers.zip.ZipFile(f).use { zf ->
                        val entries = zf.entries.toList().map { it.name }
                        val isFastboot = entries.any { it.endsWith("partition.xml") || it.contains("META-INF/com/android") }
                        Logger.i("Validate", "ZIP entries=${entries.size}, fastboot=$isFastboot")
                        isFastboot
                    }
                } catch (e: Exception) { Logger.e("Validate", e.message ?: ""); false }
            }
            binding.txtLog.append(if (ok) "✅ 校验通过：合法 fastboot 卡刷包\n" else "❌ 校验失败：不是有效的 fastboot 卡刷包\n")
        }
    }

    private fun doFlashZip() {
        val zip = pickedZip ?: return toast("请先选择 ZIP")
        binding.txtLog.append("开始刷写 ZIP（MVP 演示，推送到设备端）...\n")
        CoroutineScope(Dispatchers.Main).launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                val serial = intent.getStringExtra("serial")
                    ?: AdbManager.listDevices().firstOrNull()
                    ?: return@withContext Pair(false, "无设备")
                AdbManager.parseAndFlashZip(serial, zip)
            }
            binding.txtLog.append(if (ok) "✅ $msg\n" else "❌ 失败: $msg\n")
        }
    }

    private fun doFlashImg() {
        val img = pickedImg ?: return toast("请先选择 IMG")
        val part = binding.edtPartition.text.toString().trim().ifBlank { "boot" }
        pickedPartition = part
        binding.txtLog.append("刷写 $part ← $img ...\n")
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) {
                val serial = intent.getStringExtra("serial")
                    ?: AdbManager.listDevices().firstOrNull()
                    ?: return@withContext false
                AdbManager.fastbootFlash(serial, part, img)
            }
            binding.txtLog.append(if (ok) "✅ 刷写完成\n" else "❌ 刷写失败\n")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object { const val REQ_ZIP = 1001; const val REQ_IMG = 1002 }
}
