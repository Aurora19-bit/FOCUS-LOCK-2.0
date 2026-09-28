package com.vidhya.focuslock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that just keeps a persistent notification showing time
 * remaining in the session, so it's always visible and can't be swiped away
 * mid-session.
 */
class SessionTimerService : Service() {

    private var timer: CountDownTimer? = null

    companion object {
        private const val CHANNEL_ID = "focuslock_session"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SessionTimerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionTimerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus session", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val remaining = SessionManager.sessionEndTime(this) - System.currentTimeMillis()
        if (remaining <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification(remaining))
        timer?.cancel()
        timer = object : CountDownTimer(remaining, 30_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val mgr = getSystemService(NotificationManager::class.java)
                mgr.notify(NOTIF_ID, buildNotification(millisUntilFinished))
            }
            override fun onFinish() {
                stopSelf()
            }
        }.start()
        return START_STICKY
    }

    private fun buildNotification(remainingMillis: Long) : android.app.Notification {
        val minutes = (remainingMillis / 60000).toInt().coerceAtLeast(0)
        val text = if (SessionManager.isInBreak(this)) {
            val breakEnd = SessionManager.currentBreak(this)?.second ?: 0L
            val breakMinsLeft = ((breakEnd - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            "On break — $breakMinsLeft min left, then locked again ($minutes min total remaining)"
        } else {
            val next = SessionManager.nextBreak(this)
            if (next != null) {
                val untilBreak = ((next.first - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                "$minutes min remaining — next break in $untilBreak min"
            } else {
                "$minutes min remaining — only allowed apps are usable"
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus session active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
