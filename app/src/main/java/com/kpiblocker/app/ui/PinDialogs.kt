package com.kpiblocker.app.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kpiblocker.app.R
import com.kpiblocker.app.data.Prefs

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 6

fun showEnterPin(context: Context, title: String, onSuccess: () -> Unit) {
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = context.getString(R.string.pin_hint)
        maxLines = 1
        setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 8))
    }
    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(input)
        .setPositiveButton(R.string.ok) { _, _ ->
            val pin = input.text.toString()
            if (Prefs.checkPin(pin)) {
                onSuccess()
            } else {
                Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

fun showCreatePin(context: Context, changeExisting: Boolean, onDone: () -> Unit) {
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0)
    }
    val newPin = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = context.getString(R.string.pin_new)
        maxLines = 1
    }
    val confirmPin = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = context.getString(R.string.pin_confirm)
        maxLines = 1
    }
    layout.addView(newPin)
    layout.addView(confirmPin)

    val builder = MaterialAlertDialogBuilder(context)
        .setTitle(if (changeExisting) R.string.pin_change_title else R.string.pin_create_title)
        .setView(layout)
        .setPositiveButton(R.string.save, null)
        .setNegativeButton(R.string.cancel, null)

    if (changeExisting) {
        builder.setNeutralButton(R.string.pin_remove) { _, _ ->
            showEnterPin(context, context.getString(R.string.pin_remove_title)) {
                Prefs.pinHash = null
                Toast.makeText(context, R.string.pin_removed, Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
    }

    val dialog = builder.create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = newPin.text.toString()
            val b = confirmPin.text.toString()
            if (a.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
                Toast.makeText(context, R.string.pin_length_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (a != b) {
                Toast.makeText(context, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.pinHash = Prefs.hashPin(a)
            Toast.makeText(context, R.string.pin_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onDone()
        }
    }
    dialog.show()
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
