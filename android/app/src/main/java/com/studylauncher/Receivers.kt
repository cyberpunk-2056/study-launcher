package com.studylauncher

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Events.log("admin_disable_requested")
        return "Study Launcher can only be removed with the secret password. This attempt was reported."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        if (!Store.released) {
            Events.log("admin_disabled", "device admin switched off without the password")
            GuardService.start(context)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Protection.armed(ctx)) return
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                Events.log("clock_changed", "system ${intent.action?.substringAfterLast('.')} broadcast")
            Intent.ACTION_BOOT_COMPLETED -> Events.log("boot")
        }
        GuardService.start(ctx)
    }

    companion object { const val WATCHDOG = "com.studylauncher.WATCHDOG" }
}

/** Silences notifications from non-allowed apps while it is study time. */
class BlockNotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Protection.armed(this)) return
        if (ModeLogic.current().mode != Mode.STUDY) return
        val pkg = sbn.packageName
        if (Policy.isAllowedInStudy(this, pkg)) return
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_ALARM || sbn.isOngoing) return
        cancelNotification(sbn.key)
    }
}
