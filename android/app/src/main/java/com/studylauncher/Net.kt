package com.studylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking HTTP client. Always call from a background thread. */
object Api {
    class Resp(val code: Int, val json: JSONObject?)

    private fun call(method: String, path: String, body: JSONObject?, auth: Boolean): Resp? = try {
        val c = URL(Store.baseUrl + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Content-Type", "application/json")
        if (auth) c.setRequestProperty("Authorization", "Bearer ${Store.token}")
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        Resp(code, runCatching { JSONObject(text) }.getOrNull())
    } catch (_: Exception) {
        null
    }

    fun get(path: String) = call("GET", path, null, true)
    fun post(path: String, body: JSONObject, auth: Boolean = true) = call("POST", path, body, auth)
}

object Events {
    private val lock = Any()

    fun log(type: String, detail: String? = null) = synchronized(lock) {
        val arr = Store.pendingEvents
        arr.put(JSONObject().put("type", type).put("detail", detail ?: JSONObject.NULL).put("time", Clock.now()))
        val trimmed = JSONArray()
        for (i in maxOf(0, arr.length() - 200) until arr.length()) trimmed.put(arr.get(i))
        Store.pendingEvents = trimmed
    }

    fun flush() {
        if (Store.token == null) return
        val batch = synchronized(lock) { Store.pendingEvents }
        if (batch.length() == 0) return
        val r = Api.post("/api/device/events", JSONObject().put("events", batch)) ?: return
        if (r.code != 200) return
        synchronized(lock) {
            val cur = Store.pendingEvents
            val rest = JSONArray()
            for (i in batch.length() until cur.length()) rest.put(cur.get(i))
            Store.pendingEvents = rest
        }
    }
}

object Sync {
    /** Pull config + server time. Returns true on success. */
    fun config(ctx: Context): Boolean {
        val r = Api.get("/api/device/config") ?: return false
        val j = r.json
        if (r.code != 200 || j == null) return false
        Clock.sync(j.getLong("serverTime"))
        val cfg = AppConfig.parse(j)
        Store.config = cfg
        if (cfg.releaseRequested && !Store.released) Protection.release(ctx, "admin released the phone remotely")
        return true
    }

    /** Tell the admin panel which apps are installed so it can offer a real picker. */
    fun reportApps(ctx: Context) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val arr = JSONArray()
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != ctx.packageName }
            .forEach { arr.put(JSONObject().put("package", it.first).put("label", it.second)) }
        Api.post("/api/device/apps", JSONObject().put("apps", arr))
    }
}
