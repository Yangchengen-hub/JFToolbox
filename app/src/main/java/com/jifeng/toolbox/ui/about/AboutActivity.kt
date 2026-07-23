package com.jifeng.toolbox.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.jifeng.toolbox.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtStudio.text = "极风工作室"
        binding.txtAuthor.text = "诺言"

        binding.btnContactQQ.setOnClickListener { openContact("mqq://im/chat?chat_type=wpa") }
        binding.btnContactTg.setOnClickListener { openContact("https://t.me/TELGRAMES") }
        binding.btnContactCoolapk.setOnClickListener { openContact("coolmarket://u/123456") }
    }

    private fun openContact(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (_: Exception) {
            // 未安装 → 在内置浏览器打开
            startActivity(Intent(this, com.jifeng.toolbox.ui.browser.BrowserActivity::class.java).putExtra("url", uri))
        }
    }
}
