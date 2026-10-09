package com.studylauncher

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A clock the student cannot move. After each server sync we advance it with elapsedRealtime
 * (monotonic, unaffected by changing the date/time). After a reboot that baseline is lost, so
 * until the next sync the clock is "untrusted" and the launcher fails closed into study mode.
 */
object Clock {
    private lateinit var ctx: Context

    fun init(c: Context) { ctx = c.applicationContext }

    private fun boot(): Int = Settings.Global.getInt(ctx.contentResolver, "boot_count", -1)

    private fun sameBoot(): Boolean =
        Store.syncBoot == boot() && SystemClock.elapsedRealtime() >= Store.syncElapsed

    fun isTrusted(): Boolean = Store.syncTrusted > 0 && sameBoot()

    fun now(): Long = when {
        Store.syncTrusted <= 0 -> System.currentTimeMillis()
        sameBoot() -> Store.syncTrusted + (SystemClock.elapsedRealtime() - Store.syncElapsed)
        else -> System.currentTimeMillis() + Store.wallOffset
    }

    fun sync(serverTime: Long) {
        Store.syncTrusted = serverTime
        Store.syncElapsed = SystemClock.elapsedRealtime()
        Store.syncBoot = boot()
        Store.wallOffset = serverTime - System.currentTimeMillis()
        Store.lastSkewReported = 0
    }

    /** How far the wall clock has been moved since the last sync (0 when untrusted). */
    fun skewMs(): Long = if (!isTrusted()) 0 else System.currentTimeMillis() + Store.wallOffset - now()
}

enum class Mode { STUDY, BREAK }

data class ModeState(val mode: Mode, val msLeft: Long, val forced: Boolean, val trusted: Boolean)

object ModeLogic {
    fun current(cfg: AppConfig? = Store.config): ModeState {
        if (cfg == null) return ModeState(Mode.STUDY, 0, false, true)
        cfg.forceMode?.let { return ModeState(if (it == "break") Mode.BREAK else Mode.STUDY, -1, true, true) }
        if (!Clock.isTrusted()) return ModeState(Mode.STUDY, -1, false, false)
        val study = cfg.studyMin * 60_000L
        val total = study + cfg.breakMin * 60_000L
        val pos = ((Clock.now() - cfg.anchor) % total + total) % total
        return if (pos < study) ModeState(Mode.STUDY, study - pos, false, true)
        else ModeState(Mode.BREAK, total - pos, false, true)
    }
}

/** 6-digit code derived from the per-student secret; matches lib/crypto.ts offlineCode(). */
object OfflineCode {
    private const val WINDOW_MS = 10 * 60 * 1000L

    private fun codeFor(secret: String, window: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val d = mac.doFinal(window.toString().toByteArray(Charsets.UTF_8))
        val n = ByteBuffer.wrap(d, 0, 4).int and 0x7fffffff
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    fun verify(secret: String, input: String, nowMs: Long): Boolean {
        if (secret.isEmpty() || input.length != 6) return false
        val w = nowMs / WINDOW_MS
        return listOf(w, w - 1).any {
            MessageDigest.isEqual(codeFor(secret, it).toByteArray(), input.toByteArray())
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms < 0) return "--:--"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
