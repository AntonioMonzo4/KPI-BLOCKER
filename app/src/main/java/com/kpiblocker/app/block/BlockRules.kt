package com.kpiblocker.app.block

import android.content.Context
import com.kpiblocker.app.data.Prefs
import com.kpiblocker.app.data.Schedule
import java.util.Calendar

object BlockRules {

    fun isBlockedNow(context: Context, packageName: String): Boolean =
        isBlocked(
            packageName = packageName,
            selfPackage = context.packageName,
            blockedPackages = Prefs.blockedPackages,
            schedules = Prefs.schedules,
            pauseUntil = Prefs.pauseUntil,
            now = System.currentTimeMillis()
        )

    fun isBlocked(
        packageName: String,
        selfPackage: String,
        blockedPackages: Set<String>,
        schedules: List<Schedule>,
        pauseUntil: Long,
        now: Long
    ): Boolean {
        if (packageName == selfPackage) return false
        if (now < pauseUntil) return false
        if (packageName !in blockedPackages) return false

        val active = schedules.filter { it.enabled && it.days.isNotEmpty() }
        if (active.isEmpty()) return true

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return active.any { it.covers(cal) }
    }

    fun nextUnlockMillis(now: Long = System.currentTimeMillis()): Long? =
        nextUnlock(now, Prefs.schedules, Prefs.pauseUntil)

    fun nextUnlock(now: Long, schedules: List<Schedule>, pauseUntil: Long): Long? {
        if (now < pauseUntil) return pauseUntil

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val covering = schedules.filter {
            it.enabled && it.days.isNotEmpty() && it.covers(cal)
        }
        if (covering.isEmpty()) return null

        val startOfDay = cal.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        startOfDay.set(Calendar.MILLISECOND, 0)

        return covering.minOf { s ->
            var end = startOfDay.timeInMillis + s.endMin * 60_000L
            if (end <= now) end += 24 * 60 * 60_000L
            end
        }
    }
}
