package com.kpiblocker.app.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.kpiblocker.app.R

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

class AppListAdapter(
    private val onToggle: (AppEntry, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.Holder>() {

    private val items = mutableListOf<AppEntry>()
    private val blocked: MutableSet<String> = mutableSetOf()

    fun submit(newItems: List<AppEntry>, blockedPackages: Set<String>) {
        items.clear()
        items.addAll(newItems)
        blocked.clear()
        blocked.addAll(blockedPackages)
        notifyDataSetChanged()
    }

    fun setBlocked(packageName: String, isBlocked: Boolean) {
        if (isBlocked) blocked.add(packageName) else blocked.remove(packageName)
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.appIcon)
        private val name: TextView = view.findViewById(R.id.appName)
        private val pkg: TextView = view.findViewById(R.id.appPackage)
        private val toggle: MaterialSwitch = view.findViewById(R.id.appToggle)

        fun bind(entry: AppEntry) {
            icon.setImageDrawable(entry.icon)
            name.text = entry.label
            pkg.text = entry.packageName

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = blocked.contains(entry.packageName)
            toggle.setOnCheckedChangeListener { _, checked ->
                onToggle(entry, checked)
            }

            itemView.setOnClickListener {
                toggle.toggle()
            }
        }
    }
}
