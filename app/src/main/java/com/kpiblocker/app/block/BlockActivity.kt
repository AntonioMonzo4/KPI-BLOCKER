package com.kpiblocker.app.block

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kpiblocker.app.R
import com.kpiblocker.app.data.Prefs
import com.kpiblocker.app.ui.showEnterPin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
        private const val PAUSE_MINUTES = 10L

        @Volatile
        var isShowing: Boolean = false
    }

    private var blockedPackage: String? = null

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        isShowing = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        applyIntent(intent)

        findViewById<Button>(R.id.btnExit).setOnClickListener { goHome() }
        findViewById<Button>(R.id.btnUnlock).setOnClickListener { askUnlock() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        blockedPackage = intent?.getStringExtra(EXTRA_PACKAGE)
        val title = findViewById<TextView>(R.id.blockTitle)
        val scheduleInfo = findViewById<TextView>(R.id.blockScheduleInfo)

        val pkg = blockedPackage
        val label = pkg?.let { loadLabel(it) }
        title.text = if (label != null) getString(R.string.blocked_title_named, label)
        else getString(R.string.blocked_title)

        scheduleInfo.text = buildScheduleInfo()
    }

    private fun buildScheduleInfo(): String {
        val now = System.currentTimeMillis()
        if (now < Prefs.pauseUntil) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            return getString(R.string.blocked_paused_until, fmt.format(Date(Prefs.pauseUntil)))
        }
        val next = BlockRules.nextUnlockMillis(now)
        return if (next != null) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            getString(R.string.blocked_unlock_at, fmt.format(Date(next)))
        } else {
            getString(R.string.blocked_permanent)
        }
    }

    private fun loadLabel(pkg: String): String? = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun askUnlock() {
        if (!Prefs.hasPin) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unlock_no_pin_title)
                .setMessage(R.string.unlock_no_pin_message)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        showEnterPin(this, getString(R.string.unlock_dialog_title)) {
            Prefs.pauseUntil = System.currentTimeMillis() + PAUSE_MINUTES * 60_000L
            launchBlockedApp()
            finish()
        }
    }

    private fun launchBlockedApp() {
        val pkg = blockedPackage ?: return
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } else {
            goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
