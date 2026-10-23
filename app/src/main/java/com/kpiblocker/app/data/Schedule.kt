package com.kpiblocker.app.data

import java.util.Calendar

data class Schedule(
    val id: String,
    val name: String,
    val days: Set<Int>,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean = true
) {

    fun covers(now: Calendar): Boolean {
        if (!enabled || days.isEmpty()) return false
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = isoDayOfWeek(now)

        if (startMin == endMin) return today in days

        return if (startMin < endMin) {
            nowMin >= startMin && nowMin < endMin && today in days
        } else {
            val yesterday = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_MONTH, -1)
            }
            val coveredToday = nowMin >= startMin && today in days
            val coveredFromYesterday = nowMin < endMin && isoDayOfWeek(yesterday) in days
            coveredToday || coveredFromYesterday
        }
    }

    fun daysLabel(): String {
        if (days.size == 7) return "Todos los días"
        if (days == setOf(1, 2, 3, 4, 5)) return "Lunes a viernes"
        if (days == setOf(6, 7)) return "Fin de semana"
        return days.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
    }

    fun timeLabel(): String = "${formatMinutes(startMin)} - ${formatMinutes(endMin)}"

    companion object {
        val DAY_NAMES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

        fun isoDayOfWeek(cal: Calendar): Int = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

        fun formatMinutes(min: Int): String {
            val h = min / 60
            val m = min % 60
            return "%02d:%02d".format(h, m)
        }
    }
}
