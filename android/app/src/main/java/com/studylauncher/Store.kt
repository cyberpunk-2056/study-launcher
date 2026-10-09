package com.studylauncher

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Config pushed by the admin panel. */
data class AppConfig(
    val studentName: String,
    val studyMin: Int,
    val breakMin: Int,
    val anchor: Long,
    val forceMode: String?,
    val allowed: Set<String>,
    val releaseRequested: Boolean,
    val message: String = "",
    val approved: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("studentName", studentName).put("studyMinutes", studyMin).put("breakMinutes", breakMin)
        .put("cycleAnchor", anchor).put("forceMode", forceMode ?: JSONObject.NULL)
        .put("allowedPackages", JSONArray(allowed.toList())).put("releaseRequested", releaseRequested)
        .put("message", message).put("approved", approved)

    companion object {
        fun parse(j: JSONObject): AppConfig {
            val arr = j.optJSONArray("allowedPackages") ?: JSONArray()
            return AppConfig(
                studentName = j.optString("studentName", ""),
                studyMin = j.optInt("studyMinutes", 180),
                breakMin = j.optInt("breakMinutes", 60),
                anchor = j.optLong("cycleAnchor", 0L),
                forceMode = if (j.isNull("forceMode")) null else j.optString("forceMode").ifEmpty { null },
                allowed = (0 until arr.length()).map { arr.getString(it) }.toSet(),
                releaseRequested = j.optBoolean("releaseRequested", false),
                message = j.optString("message", ""),
                approved = j.optBoolean("approved", true),
            )
        }
    }
}

/** Observable bits the UI reads. */
object AppState {
    val config = MutableStateFlow<AppConfig?>(null)
    val tab = MutableStateFlow(0)
}

/** All persistent state. Everything lives in one private SharedPreferences file. */
object Store {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("sl", Context.MODE_PRIVATE)
        AppState.config.value = config
    }

    private fun str(k: String): String? = sp.getString(k, null)
    private fun put(k: String, v: String?) = sp.edit().putString(k, v).apply()
    private fun long(k: String, d: Long = 0L) = sp.getLong(k, d)
    private fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()

    var baseUrl: String
        get() = str("baseUrl") ?: BuildConfig.SERVER_URL
        set(v) = put("baseUrl", v.trim().trimEnd('/'))
    var token: String?
        get() = str("token")
        set(v) = put("token", v)
    var offlineSecret: String
        get() = str("offlineSecret") ?: ""
        set(v) = put("offlineSecret", v)
    var config: AppConfig?
        get() = str("config")?.let { runCatching { AppConfig.parse(JSONObject(it)) }.getOrNull() }
        set(v) { put("config", v?.toJson()?.toString()); AppState.config.value = v }

    /** Protection dropped (admin password or remote release). */
    var released: Boolean
        get() = sp.getBoolean("released", false)
        set(v) = sp.edit().putBoolean("released", v).apply()

    // Trusted clock baseline (see Clock)
    var syncTrusted: Long get() = long("syncTrusted"); set(v) = putLong("syncTrusted", v)
    var syncElapsed: Long get() = long("syncElapsed"); set(v) = putLong("syncElapsed", v)
    var syncBoot: Int get() = sp.getInt("syncBoot", -1); set(v) = sp.edit().putInt("syncBoot", v).apply()
    var wallOffset: Long get() = long("wallOffset"); set(v) = putLong("wallOffset", v)
    var lastSkewReported: Long get() = long("skewReported"); set(v) = putLong("skewReported", v)

    // Time windows during which Settings may be opened (trusted-clock ms)
    var unlockedUntil: Long get() = long("unlockedUntil"); set(v) = putLong("unlockedUntil", v)
    var fixingUntil: Long get() = long("fixingUntil"); set(v) = putLong("fixingUntil", v)

    // Local password attempt limiter (used when offline)
    var localFails: Int get() = sp.getInt("localFails", 0); set(v) = sp.edit().putInt("localFails", v).apply()
    var localLockUntil: Long get() = long("localLockUntil"); set(v) = putLong("localLockUntil", v)

    var pendingEvents: JSONArray
        get() = runCatching { JSONArray(str("events") ?: "[]") }.getOrDefault(JSONArray())
        set(v) = put("events", v.toString())

    var tasksJson: String? get() = str("tasks"); set(v) = put("tasks", v)
    var statsJson: String? get() = str("stats"); set(v) = put("stats", v)
    var pomodoroJson: String? get() = str("pomodoro"); set(v) = put("pomodoro", v)
}
