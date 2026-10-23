package com.kpiblocker.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.kpiblocker.app.R
import com.kpiblocker.app.data.Schedule
import java.util.UUID

object ScheduleEditDialog {

    private val DAY_CHIP_IDS = mapOf(
        R.id.chipLun to 1,
        R.id.chipMar to 2,
        R.id.chipMie to 3,
        R.id.chipJue to 4,
        R.id.chipVie to 5,
        R.id.chipSab to 6,
        R.id.chipDom to 7
    )

    fun show(
        context: Context,
        fragmentManager: FragmentManager,
        existing: Schedule?,
        onSaved: (Schedule) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_schedule, null, false)
        val nameInput = view.findViewById<TextInputEditText>(R.id.scheduleNameInput)
        view.findViewById<ChipGroup>(R.id.chipGroupDays)
        val btnStart = view.findViewById<Button>(R.id.btnStartTime)
        val btnEnd = view.findViewById<Button>(R.id.btnEndTime)

        var startMin = existing?.startMin ?: (8 * 60)
        var endMin = existing?.endMin ?: (22 * 60)

        nameInput.setText(existing?.name ?: "")

        DAY_CHIP_IDS.forEach { (chipId, day) ->
            view.findViewById<Chip>(chipId).isChecked = existing?.days?.contains(day) == true
        }

        fun updateTimeLabels() {
            btnStart.text = context.getString(R.string.schedule_start, Schedule.formatMinutes(startMin))
            btnEnd.text = context.getString(R.string.schedule_end, Schedule.formatMinutes(endMin))
        }
        updateTimeLabels()

        fun pickTime(title: Int, current: Int, onPicked: (Int) -> Unit) {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(current / 60)
                .setMinute(current % 60)
                .setTitleText(context.getString(title))
                .build()
            picker.addOnPositiveButtonClickListener {
                onPicked(picker.hour * 60 + picker.minute)
                updateTimeLabels()
            }
            picker.show(fragmentManager, "time_picker")
        }

        btnStart.setOnClickListener {
            pickTime(R.string.schedule_start_title, startMin) { startMin = it }
        }
        btnEnd.setOnClickListener {
            pickTime(R.string.schedule_end_title, endMin) { endMin = it }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.schedule_add_title else R.string.schedule_edit_title)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedDays = mutableSetOf<Int>()
                for ((chipId, day) in DAY_CHIP_IDS) {
                    if (view.findViewById<Chip>(chipId).isChecked) selectedDays.add(day)
                }
                if (selectedDays.isEmpty()) {
                    Toast.makeText(context, R.string.schedule_days_error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val name = nameInput.text?.toString()?.trim().orEmpty()
                    .ifEmpty { context.getString(R.string.schedule_default_name) }

                onSaved(
                    Schedule(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        days = selectedDays,
                        startMin = startMin,
                        endMin = endMin,
                        enabled = existing?.enabled ?: true
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
