package com.jifeng.toolbox.ui.disclaimer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.jifeng.toolbox.databinding.ActivityDisclaimerBinding
import com.jifeng.toolbox.ui.main.MainActivity

class DisclaimerActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "jf_disclaimer"
        private const val KEY_ACCEPTED = "accepted"
        fun hasAccepted(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)
    }

    private lateinit var binding: ActivityDisclaimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (hasAccepted(this)) { goMain(); return }

        binding.btnAccept.setOnClickListener {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACCEPTED, true).apply()
            goMain()
        }
        binding.btnExit.setOnClickListener { finishAffinity() }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
