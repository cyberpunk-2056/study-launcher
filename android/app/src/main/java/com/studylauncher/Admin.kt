package com.studylauncher

import org.json.JSONObject

sealed interface Verify {
    data object Ok : Verify
    data class Fail(val msg: String) : Verify
}

/** Checks the secret password with the server, falling back to a time-limited offline code from the admin. */
object AdminAuth {
    private const val MAX_LOCAL_FAILS = 5
    private const val LOCK_MS = 15 * 60_000L

    /** Blocking; call from a background thread. */
    fun verify(input: String): Verify {
        val now = Clock.now()
        if (Store.localLockUntil > now) {
            return Verify.Fail("Too many wrong attempts. Try again in ${(Store.localLockUntil - now) / 60_000 + 1} min.")
        }
        val pw = input.trim()
        if (pw.isEmpty()) return Verify.Fail("Enter the password")

        var serverMsg: String? = null
        val r = Api.post("/api/device/verify-password", JSONObject().put("password", pw))
        if (r != null) {
            val j = r.json
            if (r.code == 200 && j?.optBoolean("ok") == true) return success()
            if (r.code == 429) serverMsg = "Locked after too many wrong attempts. Try again later."
            else if (r.code == 401 && j != null && j.has("attemptsLeft")) serverMsg = "Wrong password (${j.optInt("attemptsLeft")} attempts left)."
        }
        if (OfflineCode.verify(Store.offlineSecret, pw, now)) return success()

        val fails = Store.localFails + 1
        if (fails >= MAX_LOCAL_FAILS) {
            Store.localFails = 0
            Store.localLockUntil = now + LOCK_MS
            Events.log("password_lockout", "local limiter")
        } else {
            Store.localFails = fails
        }
        if (r == null) Events.log("password_wrong", "offline attempt")
        return Verify.Fail(serverMsg ?: if (r == null) "Wrong code (no internet)." else "Wrong password.")
    }

    private fun success(): Verify {
        Store.localFails = 0
        Store.localLockUntil = 0
        return Verify.Ok
    }

    /** Let Settings open for 10 minutes. */
    fun unlockSettings() {
        Store.unlockedUntil = Clock.now() + 10 * 60_000L
        Events.log("settings_unlocked", "10 minute window")
    }
}

/** "Forgot password": a reset code is e-mailed by the server to the guardian address the admin approved. */
object Recovery {
    private fun result(r: Api.Resp?, okKey: String? = null): Pair<Boolean, String> {
        if (r == null) return false to "No internet connection."
        val j = r.json
        return if (r.code == 200 && j?.optBoolean("ok") == true) true to (okKey?.let { j.optString(it) } ?: "")
        else false to (j?.optString("error")?.ifEmpty { null } ?: "Something went wrong (${r.code}).")
    }

    /** Blocking. On success the string is the masked guardian e-mail the code was sent to. */
    fun requestCode(): Pair<Boolean, String> = result(Api.post("/api/device/forgot-password", JSONObject()), "maskedEmail")

    /** Blocking. */
    fun resetPassword(code: String, newPassword: String): Pair<Boolean, String> =
        result(Api.post("/api/device/reset-password", JSONObject().put("code", code.trim()).put("newPassword", newPassword)))
}
