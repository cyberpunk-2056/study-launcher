package com.studylauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * The always-on enforcer: runs the pomodoro, syncs with the server, double-checks the foreground app
 * through usage stats, watches for lost permissions / clock changes, and restarts itself if killed.
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncing = AtomicBoolean(false)
    private var lastSync = 0L
    private var lastFlush = 0L
    private var lastApps = 0L
    private var lastUsage = 0L
    private var lastNote = 0L
    private var lastMode: Mode? = null
    private var lastIntegrity = ""
    private var usageCursor = System.currentTimeMillis() - 5_000

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this, 1, Notifier.guard(this, "Protection active"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        scheduleWatchdog()
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) { restartSoon() }

    override fun onDestroy() {
        scope.cancel()
        restartSoon()
        super.onDestroy()
    }

    private fun restartSoon() {
        if (Store.released) return
        sendBroadcast(Intent(this, BootReceiver::class.java).setAction(BootReceiver.WATCHDOG))
    }

    private fun scheduleWatchdog() {
        val pi = PendingIntent.getBroadcast(
            this, 1, Intent(this, BootReceiver::class.java).setAction(BootReceiver.WATCHDOG),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60_000, pi)
    }

    private suspend fun loop() {
        while (scope.isActive) {
            if (Store.released) { stopSelf(); return }
            if (Protection.armed(this)) {
                runCatching {
                    Pomodoro.tick(this)
                    pollUsage()
                    checkIntegrity()
                    checkClock()
                    enforceStudy()
                    if (Store.token != null) background()
                    notifyStatus()
                }
            }
            delay(1000)
        }
    }

    /** Independent foreground-app detection, in case the accessibility service is off. */
    @Suppress("DEPRECATION")
    private fun pollUsage() {
        if (!Protection.hasUsageAccess(this)) return
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val ev = usm.queryEvents(usageCursor, end)
        usageCursor = end
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) Fg.pkg = e.packageName
        }
    }

    private fun enforceStudy() {
        if (ModeLogic.current().mode == Mode.STUDY && !Policy.isAllowedInStudy(this, Fg.pkg)) Enforcer.goHome(this)
    }

    private fun checkIntegrity() {
        val miss = Protection.missing(this)
        val sig = miss.joinToString(",") { it.name }
        if (sig != lastIntegrity) {
            if (miss.isNotEmpty()) Events.log("protection_lost", sig)
            else if (lastIntegrity.isNotEmpty()) Events.log("protection_restored", lastIntegrity)
            lastIntegrity = sig
        }
        // Something was switched off: pull the student back to the setup screen. Settings stays reachable
        // only through the setup buttons (which open a short "fix" window).
        if (miss.isNotEmpty()) {
            val fg = Fg.pkg
            val ok = fg == packageName || fg in Policy.alwaysAllowed(this) || (Policy.isSensitiveHost(fg) && Enforcer.isUnlocked())
            if (!ok) Enforcer.goHome(this)
        }
    }

    private fun checkClock() {
        val skew = Clock.skewMs()
        if (abs(skew) > 120_000 && abs(skew - Store.lastSkewReported) > 60_000) {
            Store.lastSkewReported = skew
            Events.log("clock_changed", "wall clock off by ${skew / 1000}s; using server-anchored time")
            lastSync = 0 // resync now
        }
    }

    private fun background() {
        val t = SystemClock.elapsedRealtime()
        if (t - lastSync > 60_000 && syncing.compareAndSet(false, true)) {
            lastSync = t
            scope.launch(Dispatchers.IO) {
                try {
                    Sync.config(this@GuardService)
                    if (t - lastApps > 6 * 3600_000L || lastApps == 0L) { lastApps = t; Sync.reportApps(this@GuardService) }
                    Reporter.status(this@GuardService)
                    if (t - lastUsage > 5 * 60_000L || lastUsage == 0L) { lastUsage = t; Reporter.usage(this@GuardService) }
                } finally { syncing.set(false) }
            }
        }
        if (t - lastFlush > 30_000) {
            lastFlush = t
            scope.launch(Dispatchers.IO) { Events.flush() }
        }
    }

    private fun notifyStatus() {
        val ms = ModeLogic.current()
        val t = SystemClock.elapsedRealtime()
        if (ms.mode != lastMode) {
            if (lastMode != null) Notifier.alert(this, if (ms.mode == Mode.STUDY) "Study time" else "Break time",
                if (ms.mode == Mode.STUDY) "Only your allowed apps are available." else "All apps are available for now.")
            lastMode = ms.mode
            lastNote = 0
        }
        if (t - lastNote < 15_000) return
        lastNote = t
        val text = when {
            !ms.trusted -> "Connect to the internet to verify the time"
            ms.mode == Mode.STUDY -> "Study time" + if (ms.msLeft >= 0) " · ${formatDuration(ms.msLeft)} left" else ""
            else -> "Break" + if (ms.msLeft >= 0) " · ${formatDuration(ms.msLeft)} left" else ""
        }
        getSystemService(android.app.NotificationManager::class.java).notify(1, Notifier.guard(this, text))
    }

    companion object {
        fun start(ctx: Context) {
            if (!Protection.armed(ctx)) return
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, GuardService::class.java)) } catch (_: Exception) {}
        }
    }
}
