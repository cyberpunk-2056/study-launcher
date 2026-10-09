package com.studylauncher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Sees every window change. It (1) keeps unlisted apps out during study time and (2) bounces the
 * student away from any screen that could uninstall, deactivate or reconfigure the launcher.
 */
class GuardAccessibilityService : AccessibilityService() {
    private var lastContentCheck = 0L
    private var lastLog = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Store.released) { disableSelf(); return }
        GuardService.start(this)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (Store.released) { disableSelf(); return }
        val pkg = e.packageName?.toString() ?: return
        val stateChange = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!stateChange) {
            // Content changes are frequent; only look at them for sensitive hosts, and not too often.
            val now = System.currentTimeMillis()
            if (!Policy.isSensitiveHost(pkg) || now - lastContentCheck < 250) return
            lastContentCheck = now
        } else {
            Fg.pkg = pkg
        }

        if (Policy.isSensitiveHost(pkg) && !Enforcer.isUnlocked()) {
            val hit = Sensitive.matchesClass(e.className?.toString()) ||
                Sensitive.matchesText(Sensitive.collectText(rootInActiveWindow), getString(R.string.app_name))
            if (hit) {
                val now = System.currentTimeMillis()
                if (now - lastLog > 5_000) { lastLog = now; Events.log("blocked_settings", "$pkg ${e.className}") }
                bounce("Protected screen")
                return
            }
        }
        if (stateChange && ModeLogic.current().mode == Mode.STUDY && !Policy.isAllowedInStudy(this, pkg)) {
            bounce("Study time: this app is not allowed")
        }
    }

    private fun bounce(msg: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Enforcer.goHome(this)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
