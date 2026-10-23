package com.kpiblocker.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.kpiblocker.app.R
import com.kpiblocker.app.data.Schedule

class ScheduleAdapter(
    private val onToggle: (Schedule, Boolean) -> Unit,
    private val onEdit: (Schedule) -> Unit,
    private val onDelete: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.Holder>() {

    private val items = mutableListOf<Schedule>()

    fun submit(newItems: List<Schedule>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.scheduleName)
        private val details: TextView = view.findViewById(R.id.scheduleDetails)
        private val toggle: MaterialSwitch = view.findViewById(R.id.scheduleToggle)
        private val edit: ImageButton = view.findViewById(R.id.scheduleEdit)
        private val delete: ImageButton = view.findViewById(R.id.scheduleDelete)

        fun bind(schedule: Schedule) {
            name.text = schedule.name
            details.text = "${schedule.daysLabel()} · ${schedule.timeLabel()}"

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = schedule.enabled
            toggle.setOnCheckedChangeListener { _, checked -> onToggle(schedule, checked) }

            edit.setOnClickListener { onEdit(schedule) }
            delete.setOnClickListener { onDelete(schedule) }
        }
    }
}
