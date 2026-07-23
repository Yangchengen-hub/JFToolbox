package com.jifeng.toolbox.ui.freeze

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.databinding.ActivityFreezeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 智能冻结检索系统 MVP：预置常见可冻结组件，一键禁用。
 */
class FreezeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFreezeBinding

    // 预置冻结清单（MVP 内置；后续改为云端拉取）
    private val presets = listOf(
        FreezeItem("厂商云控服务", "com.xiaomi.miui.analytics", "com.miui.cloud"),
        FreezeItem("系统更新组件", "com.android.updater", "com.sec.android.systemupdate"),
        FreezeItem("出厂广告组件", "com.miui.analytics", "com.android.adservices"),
        FreezeItem("数据上报/追踪", "com.xiaomi.miui.miuicontrolcenter", "com.android.settings.intelligence"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityFreezeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = FreezeListAdapter(presets) { item ->
            val serial = AdbManager.instance.listDevices().firstOrNull() ?: return@FreezeListAdapter toast("无设备")
            CoroutineScope(Dispatchers.IO).launch {
                item.pkgs.forEach { pkg ->
                    AdbManager.instance.shell(serial, "pm disable-user --user 0 $pkg")
                    AdbManager.instance.shell(serial, "pm hide --user 0 $pkg")
                }
                runOnUiThread { toast("已冻结: ${item.category}") }
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

data class FreezeItem(val category: String, val pkgs: List<String>)
class FreezeListAdapter(private val items: List<FreezeItem>, private val onExec: (FreezeItem) -> Unit) :
    androidx.recyclerview.widget.RecyclerView.Adapter<FreezeListAdapter.VH>() {
    override fun onCreateViewHolder(p: android.view.ViewGroup, v: Int): VH =
        VH(android.view.LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_2, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, i: Int) {
        val it = items[i]
        h.title.text = it.category
        h.sub.text = it.pkgs.joinToString()
        h.itemView.setOnClickListener { _ -> onExec(it) }
    }
    class VH(v: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
        val title: android.widget.TextView = v.findViewById(android.R.id.text1)
        val sub: android.widget.TextView = v.findViewById(android.R.id.text2)
    }
}
