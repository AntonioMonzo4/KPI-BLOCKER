package com.kpiblocker.app.block

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.kpiblocker.app.R
import com.kpiblocker.app.data.Prefs
import com.kpiblocker.app.ui.MainActivity

class BlockService : Service() {

    companion object {
        private const val CHANNEL_ID = "blocker_channel"
        private const val NOTIF_ID = 42
        private const val POLL_MS = 700L

        fun start(context: Context) {
            val intent = Intent(context, BlockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastLaunchAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            evaluate()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()
        handler.post(pollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.serviceEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de bloqueo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el bloqueo de aplicaciones"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_block)
            .setContentTitle("Bloqueo activo")
            .setContentText("Vigilando ${Prefs.blockedPackages.size} apps bloqueadas")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun evaluate() {
        if (!Prefs.serviceEnabled) {
            stopSelf()
            return
        }

        val now = System.currentTimeMillis()
        if (now < Prefs.pauseUntil) return

        val fg = currentForegroundPackage() ?: return
        if (fg == packageName) return
        if (fg == homePackage() || fg == "com.android.systemui") return

        if (BlockRules.isBlockedNow(this, fg) && !BlockActivity.isShowing) {
            // Evita relanzar en cada ciclo del sondeo (la actividad tarda en quedar visible)
            if (now - lastLaunchAt < 800L) return
            lastLaunchAt = now
            try {
                startActivity(
                    Intent(this, BlockActivity::class.java)
                        .putExtra(BlockActivity.EXTRA_PACKAGE, fg)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                )
            } catch (_: Exception) {
                // Sin permiso de pantalla superpuesta Android 12+ puede rechazar el inicio
            }
        }
    }

    private fun currentForegroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000, now)
            val event = UsageEvents.Event()
            var pkg: String? = null
            var lastTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val resumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (resumed && event.timeStamp >= lastTs) {
                    lastTs = event.timeStamp
                    pkg = event.packageName
                }
            }
            pkg
        } catch (_: SecurityException) {
            null
        }
    }

    private var cachedHome: String? = null

    private fun homePackage(): String {
        cachedHome?.let { return it }
        val pkg = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )?.activityInfo?.packageName
        cachedHome = pkg
        return pkg ?: ""
    }
}
