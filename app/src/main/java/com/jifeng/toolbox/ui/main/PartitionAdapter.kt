package com.jifeng.toolbox.ui.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jifeng.toolbox.R
import com.jifeng.toolbox.core.Partition

class PartitionAdapter(initial: List<Partition>) :
    ListAdapter<Partition, PartitionAdapter.VH>(Diff()) {

    init { submitList(initial) }

    fun update(list: List<Partition>) = submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_partition, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        holder.name.text = p.name
        val mb = if (p.size > 0) "${p.size / 1048576} MB" else "—"
        holder.size.text = mb
        if (p.isProtected) {
            holder.tag.text = "🔒 受保护"
            holder.tag.setTextColor(Color.parseColor("#E53935"))
        } else {
            holder.tag.text = "可读写"
            holder.tag.setTextColor(Color.parseColor("#43A047"))
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txtPartName)
        val size: TextView = v.findViewById(R.id.txtPartSize)
        val tag: TextView = v.findViewById(R.id.txtPartTag)
    }

    class Diff : DiffUtil.ItemCallback<Partition>() {
        override fun areItemsTheSame(o: Partition, n: Partition) = o.name == n.name
        override fun areContentsTheSame(o: Partition, n: Partition) = o == n
    }
}
