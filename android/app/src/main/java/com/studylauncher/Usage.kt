package com.studylauncher

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Collects per-day screen time from usage stats and reports it, plus device/setup status, to the admin panel. */
object Reporter {
    private var yesterdayDone: String? = null

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayJson(ctx: Context, from: Long, to: Long, key: String): JSONObject? {
        if (!Protection.hasUsageAccess(ctx)) return null
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager
        val skip = setOf(ctx.packageName, "com.android.systemui", "android")
        val list = usm.queryAndAggregateUsageStats(from, to).values
            .filter { it.totalTimeInForeground > 0 && it.packageName !in skip }
            .sortedByDescending { it.totalTimeInForeground }
        val apps = JSONArray()
        list.take(30).forEach {
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() }.getOrDefault(it.packageName)
            apps.put(JSONObject().put("package", it.packageName).put("label", label).put("ms", it.totalTimeInForeground))
        }
        val stat = Stats.days.value[key]
        return JSONObject().put("day", key).put("screenMs", list.sumOf { it.totalTimeInForeground }).put("apps", apps)
            .put("focusMin", stat?.focusMin ?: 0).put("pomodoros", stat?.pomodoros ?: 0).put("tasks", stat?.tasks ?: 0)
    }

    /** Blocking. Uploads today (always) and yesterday (once per day, to finalise it). */
    fun usage(ctx: Context) {
        val now = Clock.now()
        val todayStart = startOfDay(now)
        val days = JSONArray()
        dayJson(ctx, todayStart, now, Stats.key(now))?.let { days.put(it) }
        val yKey = Stats.key(todayStart - 1)
        if (yesterdayDone != yKey) {
            dayJson(ctx, startOfDay(todayStart - 1), todayStart, yKey)?.let { days.put(it); yesterdayDone = yKey }
        }
        if (days.length() > 0) Api.post("/api/device/usage", JSONObject().put("days", days))
    }

    /** Blocking. Lets the admin see the model, versions and which setup steps are still missing. */
    fun status(ctx: Context) {
        val missing = JSONArray().also { a -> Protection.missing(ctx).forEach { a.put(it.name.lowercase()) } }
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        Api.post(
            "/api/device/status",
            JSONObject().put("model", "${Build.MANUFACTURER} ${Build.MODEL}").put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                .put("appVersion", version).put("missing", missing)
        )
    }
}
