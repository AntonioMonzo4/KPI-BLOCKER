package com.kpiblocker.app.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.kpiblocker.app.R
import com.kpiblocker.app.block.BlockService
import com.kpiblocker.app.data.Prefs
import com.kpiblocker.app.data.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appAdapter: AppListAdapter
    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var serviceSwitch: MaterialSwitch
    private lateinit var usageStatus: TextView
    private lateinit var notifStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var appsCount: TextView
    private lateinit var scheduleHint: TextView

    private var fullAppList: List<AppEntry> = emptyList()
    private var currentQuery: String = ""
    private var suppressSwitchEvent = false

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updatePermissionRows()
            if (!granted) {
                Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialToolbar>(R.id.toolbar).title = getString(R.string.app_name)

        serviceSwitch = findViewById(R.id.serviceSwitch)
        usageStatus = findViewById(R.id.usageStatus)
        notifStatus = findViewById(R.id.notifStatus)
        overlayStatus = findViewById(R.id.overlayStatus)
        batteryStatus = findViewById(R.id.batteryStatus)
        appsCount = findViewById(R.id.appsCount)
        scheduleHint = findViewById(R.id.scheduleHint)

        setupPermissionRows()
        setupServiceSwitch()
        setupAppList()
        setupScheduleSection()
        setupPinSection()

        if (Prefs.serviceEnabled && hasUsageAccess()) {
            BlockService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionRows()
        syncServiceSwitch()
        if (Prefs.serviceEnabled && hasUsageAccess()) {
            BlockService.start(this)
        }
    }

    // ---------- Permisos ----------

    private fun setupPermissionRows() {
        findViewById<Button>(R.id.btnUsageAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, R.string.notif_legacy_ok, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, R.string.battery_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePermissionRows() {
        usageStatus.text = statusText(hasUsageAccess())
        notifStatus.text = statusText(notificationsGranted())
        overlayStatus.text = statusText(Settings.canDrawOverlays(this))
        batteryStatus.text = statusText(isIgnoringBatteryOptimizations())
    }

    private fun statusText(granted: Boolean): String =
        getString(if (granted) R.string.status_granted else R.string.status_not_granted)

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    // ---------- Servicio ----------

    private fun setupServiceSwitch() {
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitchEvent) return@setOnCheckedChangeListener
            if (checked) {
                if (!hasUsageAccess()) {
                    syncServiceSwitch()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.usage_dialog_title)
                        .setMessage(R.string.usage_dialog_message)
                        .setPositiveButton(R.string.usage_dialog_go) { _, _ ->
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                    return@setOnCheckedChangeListener
                }
                if (!Settings.canDrawOverlays(this)) {
                    syncServiceSwitch()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.overlay_dialog_title)
                        .setMessage(R.string.overlay_dialog_message)
                        .setPositiveButton(R.string.overlay_dialog_go) { _, _ ->
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                    return@setOnCheckedChangeListener
                }
                if (!notificationsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Prefs.serviceEnabled = true
                BlockService.start(this)
                Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
            } else {
                if (Prefs.hasPin) {
                    syncServiceSwitch()
                    showEnterPin(this, getString(R.string.pin_disable_service)) {
                        disableService()
                    }
                } else {
                    disableService()
                }
            }
        }
    }

    private fun disableService() {
        Prefs.serviceEnabled = false
        stopService(Intent(this, BlockService::class.java))
        syncServiceSwitch()
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun syncServiceSwitch() {
        val enabled = Prefs.serviceEnabled
        if (serviceSwitch.isChecked != enabled) {
            suppressSwitchEvent = true
            serviceSwitch.isChecked = enabled
            suppressSwitchEvent = false
        }
    }

    // ---------- Lista de aplicaciones ----------

    private fun setupAppList() {
        appAdapter = AppListAdapter { entry, checked ->
            val updated = Prefs.blockedPackages.toMutableSet()
            if (checked) updated.add(entry.packageName) else updated.remove(entry.packageName)
            Prefs.blockedPackages = updated
            appAdapter.setBlocked(entry.packageName, checked)
            updateAppsCount()
        }
        val recycler = findViewById<RecyclerView>(R.id.appsList)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = appAdapter
        recycler.isNestedScrollingEnabled = false

        findViewById<TextInputEditText>(R.id.appsSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim().orEmpty()
                applyAppFilter()
            }
        })

        updateAppsCount()
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { info ->
                        info.packageName != packageName &&
                            pm.getLaunchIntentForPackage(info.packageName) != null
                    }
                    .map { info ->
                        val label = pm.getApplicationLabel(info).toString()
                        val icon = try {
                            pm.getApplicationIcon(info)
                        } catch (_: Exception) {
                            null
                        }
                        AppEntry(label, info.packageName, icon)
                    }
                    .sortedWith(compareBy { it.label.lowercase() })
            }
            fullAppList = apps
            applyAppFilter()
            updateAppsCount()
        }
    }

    private fun applyAppFilter() {
        val query = currentQuery
        val filtered = if (query.isEmpty()) fullAppList
        else fullAppList.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        appAdapter.submit(filtered, Prefs.blockedPackages)
    }

    private fun updateAppsCount() {
        appsCount.text = getString(R.string.apps_count, Prefs.blockedPackages.size, fullAppList.size)
    }

    // ---------- Horarios ----------

    private fun setupScheduleSection() {
        scheduleAdapter = ScheduleAdapter(
            onToggle = { schedule, enabled ->
                saveSchedule(schedule.copy(enabled = enabled))
            },
            onEdit = { schedule ->
                ScheduleEditDialog.show(this, supportFragmentManager, schedule) {
                    saveSchedule(it)
                }
            },
            onDelete = { schedule ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.schedule_delete_title)
                    .setMessage(getString(R.string.schedule_delete_message, schedule.name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Prefs.schedules = Prefs.schedules.filterNot { it.id == schedule.id }
                        refreshSchedules()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        val recycler = findViewById<RecyclerView>(R.id.schedulesList)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = scheduleAdapter
        recycler.isNestedScrollingEnabled = false

        findViewById<Button>(R.id.btnAddSchedule).setOnClickListener {
            ScheduleEditDialog.show(this, supportFragmentManager, null) {
                Prefs.schedules = Prefs.schedules + it
                refreshSchedules()
            }
        }
        refreshSchedules()
    }

    private fun saveSchedule(updated: Schedule) {
        Prefs.schedules = Prefs.schedules.map { if (it.id == updated.id) updated else it }
        refreshSchedules()
    }

    private fun refreshSchedules() {
        val schedules = Prefs.schedules
        scheduleAdapter.submit(schedules)
        scheduleHint.visibility = View.VISIBLE
        scheduleHint.text = if (schedules.isEmpty()) {
            getString(R.string.schedule_empty_hint)
        } else {
            getString(R.string.schedule_hint)
        }
    }

    // ---------- PIN ----------

    private fun setupPinSection() {
        val pinDescription = findViewById<TextView>(R.id.pinDescription)
        val btnPin = findViewById<Button>(R.id.btnPin)

        fun refresh() {
            pinDescription.text = getString(
                if (Prefs.hasPin) R.string.pin_active else R.string.pin_inactive
            )
            btnPin.setText(if (Prefs.hasPin) R.string.pin_change else R.string.pin_create)
        }

        btnPin.setOnClickListener {
            showCreatePin(this, Prefs.hasPin) { refresh() }
        }
        refresh()
    }
}
