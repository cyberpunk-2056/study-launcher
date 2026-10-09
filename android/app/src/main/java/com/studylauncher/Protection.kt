package com.studylauncher

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat

/** Things that must be granted for the launcher to be tamper-resistant. */
enum class Req(val title: String, val why: String) {
    HOME("Make this the home app", "So the home button always returns here."),
    ADMIN("Device administrator", "Blocks uninstalling the launcher from Settings."),
    ACCESSIBILITY("Accessibility service", "Lets the launcher block unlisted apps and the screens used to remove it."),
    USAGE("Usage access", "A second, independent way to see which app is open."),
    OVERLAY("Display over other apps", "Lets the launcher bring itself back if it is interrupted."),
    NOTIF("Notification access", "Silences distracting notifications during study time."),
    BATTERY("Unrestricted battery", "Stops the system from putting the launcher to sleep."),
}

object Protection {
    fun adminComponent(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isDefaultHome(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = ctx.packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == ctx.packageName
    }

    fun isAdminActive(ctx: Context): Boolean =
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent(ctx))

    fun isAccessibilityOn(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, GuardAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun isNotifListener(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun isBatteryFree(ctx: Context) =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun granted(ctx: Context, r: Req): Boolean = when (r) {
        Req.HOME -> isDefaultHome(ctx)
        Req.ADMIN -> isAdminActive(ctx)
        Req.ACCESSIBILITY -> isAccessibilityOn(ctx)
        Req.USAGE -> hasUsageAccess(ctx)
        Req.OVERLAY -> Settings.canDrawOverlays(ctx)
        Req.NOTIF -> isNotifListener(ctx)
        Req.BATTERY -> isBatteryFree(ctx)
    }

    /**
     * Enforcement is on once the phone has been paired, or while the system still has our admin /
     * accessibility service enabled. The second part means wiping the app's data does not switch
     * protection off: an unpaired-but-armed phone stays locked down until the admin re-pairs it.
     */
    fun armed(ctx: Context): Boolean =
        !Store.released && (Store.token != null || isAdminActive(ctx) || isAccessibilityOn(ctx))

    fun missing(ctx: Context): List<Req> = if (Store.released) emptyList() else Req.values().filter { !granted(ctx, it) }

    /** Drop all protection so the app can be uninstalled. Only reachable after the password / admin release. */
    fun release(ctx: Context, reason: String) {
        Events.log("released", reason)
        Store.released = true
        runCatching {
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).removeActiveAdmin(adminComponent(ctx))
        }
        Thread { Events.flush() }.start()
    }
}

/** Foreground package as last seen by the accessibility service or usage-stats poller. */
object Fg {
    @Volatile var pkg: String = ""
}

object Policy {
    private var cache: Set<String> = emptySet()
    private var cachedAt = 0L

    /** Screens that can uninstall apps, change admins/accessibility, or reset the phone. */
    fun isSensitiveHost(pkg: String): Boolean =
        pkg.contains("settings") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller") ||
            pkg == "com.android.vending" || pkg.contains("securitycenter") || pkg.contains("devicecare")

    /** Apps that must never be blocked (emergency calling, system UI, keyboards, permission prompts). */
    fun alwaysAllowed(ctx: Context): Set<String> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 30_000 && cache.isNotEmpty()) return cache
        val s = mutableSetOf(
            ctx.packageName, "android", "com.android.systemui", "com.android.server.telecom", "com.android.phone",
            "com.android.incallui", "com.android.emergency", "com.google.android.gms",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        )
        runCatching { (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage?.let { s += it } }
        runCatching {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach { s += it.packageName }
        }
        cache = s
        cachedAt = now
        return s
    }

    fun isAllowedInStudy(ctx: Context, pkg: String): Boolean {
        if (pkg.isEmpty() || pkg in alwaysAllowed(ctx)) return true
        if (Store.config?.allowed?.contains(pkg) == true) return true
        // Settings is only reachable from the launcher's own setup / quick-settings buttons.
        if (isSensitiveHost(pkg) && Enforcer.isUnlocked()) return true
        return false
    }
}

object Enforcer {
    @Volatile private var lastBounce = 0L

    /** True while the admin password was entered recently, or a setup button opened Settings. */
    fun isUnlocked(): Boolean {
        val n = Clock.now()
        return Store.unlockedUntil > n || Store.fixingUntil > n
    }

    fun openFixWindow(seconds: Int = 90) { Store.fixingUntil = Clock.now() + seconds * 1000L }

    fun goHome(ctx: Context) {
        val n = System.currentTimeMillis()
        if (n - lastBounce < 800) return
        lastBounce = n
        runCatching {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}

/** Detects the screens used to uninstall / disable the launcher, in English, Hindi and by class name. */
object Sensitive {
    private val words = listOf(
        "uninstall", "deactivate", "device admin", "device administrator", "accessibility", "default home",
        "home app", "force stop", "special app access", "usage access", "usage data access",
        "display over other apps", "appear on top", "draw over other apps", "safe mode", "reset options",
        "factory data reset", "erase all data", "developer options", "usb debugging", "notification access",
        "app info", "clear data", "default apps",
        "अनइंस्टॉल", "डिवाइस एडमिन", "डिवाइस व्यवस्थापक", "एक्सेसिबिलिटी", "सुलभता", "जबरन रोकें", "बलपूर्वक रोकें",
        "ऐप की जानकारी", "ऐप्लिकेशन की जानकारी", "डेवलपर विकल्प", "फ़ैक्टरी डेटा रीसेट", "डिफ़ॉल्ट ऐप",
    )
    private val classes = listOf(
        "uninstall", "installedappdetails", "appinfo", "deviceadmin", "accessibility", "homesettings", "defaultapps",
        "manageapplications", "appdashboard", "development", "resetdashboard", "factoryreset", "masterclear",
        "specialaccess", "usageaccess", "drawoverlay", "appmanagement",
    )

    fun matchesClass(className: String?): Boolean {
        val c = className?.lowercase() ?: return false
        return classes.any { c.contains(it) }
    }

    fun matchesText(texts: String, appLabel: String): Boolean {
        val t = texts.lowercase()
        return t.contains(appLabel.lowercase()) || words.any { t.contains(it) }
    }

    fun collectText(root: AccessibilityNodeInfo?, limit: Int = 400): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val n = queue.removeFirst()
            seen++
            n.text?.let { sb.append(it).append('\n') }
            n.contentDescription?.let { sb.append(it).append('\n') }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return sb.toString()
    }
}
