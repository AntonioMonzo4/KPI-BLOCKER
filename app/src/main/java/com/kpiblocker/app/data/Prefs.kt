package com.kpiblocker.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object Prefs {

    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        if (sp == null) {
            sp = context.applicationContext.getSharedPreferences("kpiblocker_prefs", Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences = sp ?: error("Prefs.init() no llamado")

    var blockedPackages: Set<String>
        get() = p().getStringSet("blocked_packages", emptySet()) ?: emptySet()
        set(value) {
            p().edit().putStringSet("blocked_packages", value).apply()
        }

    var schedules: List<Schedule>
        get() = decodeSchedules(p().getString("schedules", "[]") ?: "[]")
        set(value) {
            p().edit().putString("schedules", encodeSchedules(value)).apply()
        }

    var pinHash: String?
        get() = p().getString("pin_hash", null)
        set(value) {
            if (value == null) p().edit().remove("pin_hash").apply()
            else p().edit().putString("pin_hash", value).apply()
        }

    val hasPin: Boolean get() = pinHash != null

    fun checkPin(pin: String): Boolean = pinHash != null && pinHash == hashPin(pin)

    var pauseUntil: Long
        get() = p().getLong("pause_until", 0L)
        set(value) {
            p().edit().putLong("pause_until", value).apply()
        }

    var serviceEnabled: Boolean
        get() = p().getBoolean("service_enabled", false)
        set(value) {
            p().edit().putBoolean("service_enabled", value).apply()
        }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("kpiblocker::" + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun encodeSchedules(list: List<Schedule>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("days", JSONArray(s.days.sorted()))
                put("start", s.startMin)
                put("end", s.endMin)
                put("enabled", s.enabled)
            })
        }
        return arr.toString()
    }

    private fun decodeSchedules(raw: String): List<Schedule> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            Schedule(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                name = o.optString("name", "Horario"),
                days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet(),
                startMin = o.optInt("start", 0),
                endMin = o.optInt("end", 1440 - 1),
                enabled = o.optBoolean("enabled", true)
            )
        }
    }.getOrElse { emptyList() }
}
