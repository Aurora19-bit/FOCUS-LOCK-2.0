package com.vidhya.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Session end-time is stored as an absolute timestamp, so a reboot mid-session
 * doesn't reset or clear it. This receiver just wakes the timer notification
 * back up after boot; the accessibility service re-enforces automatically
 * once Android restarts it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            SessionManager.isSessionActive(context)) {
            SessionTimerService.start(context)
        }
    }
}
