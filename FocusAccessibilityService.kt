package com.vidhya.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * The enforcement engine, with two layers so a missed event can't leak:
 *  1. Event layer: reacts instantly to window-state changes.
 *  2. Poll layer: once a second, checks which app is actually on screen.
 *     This catches dropped events, apps already open when a session starts,
 *     and same-app transitions the event layer never reports.
 * Breaks are honoured by both layers (see SessionManager.isInBreak).
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        /** True only while Android has this service genuinely bound and running.
         * More trustworthy than the "enabled" string in Settings, which can
         * stay set after the service has crashed or been stopped. */
        @Volatile
        var isConnected = false

        private const val POLL_INTERVAL_MS = 1000L
        private const val ENFORCE_COOLDOWN_MS = 800L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastEnforceAt = 0L

    private val poller = object : Runnable {
        @Suppress("DEPRECATION")
        override fun run() {
            try {
                val root = rootInActiveWindow
                val pkg = root?.packageName?.toString()
                root?.recycle()
                if (pkg != null) enforceIfNeeded(pkg)
            } catch (_: Exception) {
                // Never let a polling hiccup crash the process — that would
                // take the whole service down with it.
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        handler.removeCallbacks(poller)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        handler.removeCallbacks(poller)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        enforceIfNeeded(pkg)
    }

    private fun enforceIfNeeded(pkg: String) {
        val ctx = applicationContext
        if (!SessionManager.isSessionActive(ctx)) return
        if (SessionManager.isInBreak(ctx)) return
        if (SessionManager.isAllowed(ctx, pkg)) return

        // Settings is simply "not allowed" during a session, which is what
        // stops you disabling this service or the device admin mid-session.
        val now = System.currentTimeMillis()
        if (now - lastEnforceAt < ENFORCE_COOLDOWN_MS) return
        lastEnforceAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockActivity.EXTRA_BLOCKED_PKG, pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() { /* no-op */ }
}
