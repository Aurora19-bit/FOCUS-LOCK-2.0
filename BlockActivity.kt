package com.vidhya.focuslock

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen "you can't open that right now" interstitial. Deliberately has
 * no way to get back to the blocked app: back button is disabled and the only
 * button takes you to FocusLock's own allowed-apps screen.
 */
class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_PKG = "blocked_pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        val remaining = (SessionManager.sessionEndTime(this) - System.currentTimeMillis())
            .coerceAtLeast(0) / 60000
        val next = SessionManager.nextBreak(this)
        val breakInfo = if (next != null) {
            val untilBreak = ((next.first - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            "\nNext scheduled break in $untilBreak min"
        } else ""
        findViewById<TextView>(R.id.blockTimeRemaining).text =
            "$remaining min left in this session$breakInfo"

        findViewById<android.widget.Button>(R.id.goHomeButton).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }

    // Deliberately disabled: pressing back must never return to the blocked app.
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
