package com.studylauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifier {
    private const val CH_GUARD = "guard"
    private const val CH_ALERT = "alerts"
    private var nextId = 100

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_GUARD, "Launcher status", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Timers & reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun guard(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_GUARD)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Study Launcher")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(ctx))
            .build()

    fun alert(ctx: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open(ctx))
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(nextId++, n) }
    }
}

// ---------------------------------------------------------------- Stats

data class DayStat(val focusMin: Int = 0, val pomodoros: Int = 0, val tasks: Int = 0)

object Stats {
    val days = MutableStateFlow<Map<String, DayStat>>(emptyMap())

    fun key(ms: Long = Clock.now()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    fun load() {
        val o = runCatching { JSONObject(Store.statsJson ?: "{}") }.getOrDefault(JSONObject())
        days.value = o.keys().asSequence().associateWith {
            val d = o.getJSONObject(it)
            DayStat(d.optInt("f"), d.optInt("p"), d.optInt("t"))
        }
    }

    private fun update(f: (DayStat) -> DayStat) {
        val k = key()
        val m = days.value.toMutableMap()
        m[k] = f(m[k] ?: DayStat())
        days.value = m
        val o = JSONObject()
        m.entries.sortedBy { it.key }.takeLast(60).forEach {
            o.put(it.key, JSONObject().put("f", it.value.focusMin).put("p", it.value.pomodoros).put("t", it.value.tasks))
        }
        Store.statsJson = o.toString()
    }

    fun addFocus(min: Int) = update { it.copy(focusMin = it.focusMin + min, pomodoros = it.pomodoros + 1) }
    fun addTask(delta: Int) = update { it.copy(tasks = maxOf(0, it.tasks + delta)) }

    /** Consecutive days (ending today or yesterday) with at least one focus minute. */
    fun streak(): Int {
        var n = 0
        var t = Clock.now()
        if ((days.value[key(t)]?.focusMin ?: 0) == 0) t -= 86_400_000L
        while ((days.value[key(t)]?.focusMin ?: 0) > 0) { n++; t -= 86_400_000L }
        return n
    }

    fun lastDays(count: Int): List<Pair<String, DayStat>> {
        val now = Clock.now()
        return (count - 1 downTo 0).map { i ->
            val k = key(now - i * 86_400_000L)
            k to (days.value[k] ?: DayStat())
        }
    }
}

// ---------------------------------------------------------------- Tasks

data class Task(val id: Long, val title: String, val done: Boolean, val priority: Int)

object Tasks {
    val list = MutableStateFlow<List<Task>>(emptyList())

    fun load() {
        val a = runCatching { JSONArray(Store.tasksJson ?: "[]") }.getOrDefault(JSONArray())
        list.value = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getString("title"), o.optBoolean("done"), o.optInt("pri", 1))
        }
    }

    private fun save(v: List<Task>) {
        list.value = v
        val a = JSONArray()
        v.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done).put("pri", it.priority)) }
        Store.tasksJson = a.toString()
    }

    fun add(title: String, priority: Int) {
        if (title.isBlank()) return
        save(list.value + Task(System.currentTimeMillis(), title.trim(), false, priority))
    }

    fun toggle(id: Long) {
        val cur = list.value.firstOrNull { it.id == id } ?: return
        Stats.addTask(if (cur.done) -1 else 1)
        save(list.value.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun remove(id: Long) = save(list.value.filter { it.id != id })
}

// ---------------------------------------------------------------- Pomodoro

enum class Phase(val label: String) { FOCUS("Focus"), SHORT("Short break"), LONG("Long break") }

data class PomState(
    val phase: Phase = Phase.FOCUS,
    val running: Boolean = false,
    val endsAt: Long = 0,
    val remainingMs: Long = 25 * 60_000L,
    val doneFocus: Int = 0,
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
) {
    fun lengthOf(p: Phase) = when (p) { Phase.FOCUS -> focusMin; Phase.SHORT -> shortMin; Phase.LONG -> longMin } * 60_000L
}

object Pomodoro {
    val state = MutableStateFlow(PomState())

    fun load() {
        val o = runCatching { JSONObject(Store.pomodoroJson ?: "{}") }.getOrDefault(JSONObject())
        if (o.length() == 0) return
        state.value = PomState(
            phase = runCatching { Phase.valueOf(o.optString("phase")) }.getOrDefault(Phase.FOCUS),
            running = o.optBoolean("running"),
            endsAt = o.optLong("endsAt"),
            remainingMs = o.optLong("remaining", 25 * 60_000L),
            doneFocus = o.optInt("done"),
            focusMin = o.optInt("focus", 25), shortMin = o.optInt("short", 5), longMin = o.optInt("long", 15),
        )
    }

    private fun set(s: PomState) {
        state.value = s
        Store.pomodoroJson = JSONObject().put("phase", s.phase.name).put("running", s.running).put("endsAt", s.endsAt)
            .put("remaining", s.remainingMs).put("done", s.doneFocus).put("focus", s.focusMin).put("short", s.shortMin)
            .put("long", s.longMin).toString()
    }

    fun start() { val s = state.value; if (!s.running) set(s.copy(running = true, endsAt = Clock.now() + s.remainingMs)) }

    fun pause() {
        val s = state.value
        if (s.running) set(s.copy(running = false, remainingMs = maxOf(0, s.endsAt - Clock.now())))
    }

    fun reset() { val s = state.value; set(s.copy(running = false, remainingMs = s.lengthOf(s.phase))) }

    fun skip() { val s = state.value; val n = nextPhase(s); set(s.copy(phase = n, running = false, remainingMs = s.lengthOf(n))) }

    fun setLengths(focus: Int, short: Int, long: Int) {
        val s = state.value.copy(focusMin = focus.coerceIn(1, 180), shortMin = short.coerceIn(1, 60), longMin = long.coerceIn(1, 90))
        set(if (s.running) s else s.copy(remainingMs = s.lengthOf(s.phase)))
    }

    fun remaining(): Long { val s = state.value; return if (s.running) maxOf(0, s.endsAt - Clock.now()) else s.remainingMs }

    private fun nextPhase(s: PomState) = when (s.phase) {
        Phase.FOCUS -> if ((s.doneFocus + 1) % 4 == 0) Phase.LONG else Phase.SHORT
        else -> Phase.FOCUS
    }

    /** Called every second by the guard service; safe to call from anywhere. */
    fun tick(ctx: Context) {
        val s = state.value
        if (!s.running || Clock.now() < s.endsAt) return
        val n = nextPhase(s)
        if (s.phase == Phase.FOCUS) {
            Stats.addFocus(s.focusMin)
            set(s.copy(phase = n, running = false, remainingMs = s.lengthOf(n), doneFocus = s.doneFocus + 1))
            Notifier.alert(ctx, "Focus session complete", "Nice work. Take a ${n.label.lowercase()}.")
        } else {
            set(s.copy(phase = n, running = false, remainingMs = s.lengthOf(n)))
            Notifier.alert(ctx, "Break over", "Ready for the next focus session?")
        }
    }
}
