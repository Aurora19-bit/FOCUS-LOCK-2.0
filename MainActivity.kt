package com.vidhya.focuslock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var durationInput: EditText
    private lateinit var uninstallButton: Button
    private lateinit var breakStartInput: EditText
    private lateinit var breakLengthInput: EditText
    private lateinit var breaksListText: TextView
    private lateinit var addBreakButton: Button

    // Breaks staged before a session starts. Cleared once a session begins
    // (they're baked into SessionManager at that point) and cleared again
    // after the session ends so stale breaks don't carry into the next one.
    private val pendingBreaks = mutableListOf<Pair<Int, Int>>()

    private val puzzleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            proceedToUninstall()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recycler = findViewById(R.id.appListRecycler)
        durationInput = findViewById(R.id.durationInput)
        breakStartInput = findViewById(R.id.breakStartInput)
        breakLengthInput = findViewById(R.id.breakLengthInput)
        breaksListText = findViewById(R.id.breaksListText)
        addBreakButton = findViewById(R.id.addBreakButton)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.grantPermissionsButton).setOnClickListener {
            requestPermissions()
        }

        addBreakButton.setOnClickListener {
            val offset = breakStartInput.text.toString().toIntOrNull()
            val length = breakLengthInput.text.toString().toIntOrNull()
            if (offset == null || offset <= 0 || length == null || length <= 0) {
                Toast.makeText(this, "Enter valid numbers for both fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingBreaks.add(offset to length)
            breakStartInput.setText("")
            breakLengthInput.setText("")
            renderPendingBreaks()
        }

        findViewById<Button>(R.id.startSessionButton).setOnClickListener {
            val minutes = durationInput.text.toString().toIntOrNull()
            if (minutes == null || minutes <= 0) {
                Toast.makeText(this, "Enter a valid number of minutes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Grant all permissions first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!FocusAccessibilityService.isConnected) {
                Toast.makeText(
                    this,
                    "Enforcement service isn't running. Turn FocusLock off and on again in Accessibility settings.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            val overrunning = pendingBreaks.filter { (offset, length) -> offset + length > minutes }
            if (overrunning.isNotEmpty()) {
                Toast.makeText(this, "A break runs past the session end — fix it first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            SessionManager.startSession(this, minutes * 60_000L, pendingBreaks.toList())
            try {
                SessionTimerService.start(this)
            } catch (e: Exception) {
                // The countdown notification is cosmetic. If it fails, enforcement
                // must still run — and the app process must not crash, because
                // that would take the accessibility service down with it.
                Toast.makeText(this, "Session running (notification unavailable)", Toast.LENGTH_SHORT).show()
            }
            pendingBreaks.clear()
            renderPendingBreaks()
            updateStatus()
            Toast.makeText(this, "Focus session started for $minutes min", Toast.LENGTH_SHORT).show()
        }

        uninstallButton = findViewById(R.id.uninstallButton)
        uninstallButton.setOnClickListener {
            if (SessionManager.isSessionActive(this)) {
                Toast.makeText(this, "Can't uninstall during an active session", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            puzzleLauncher.launch(Intent(this, MathPuzzleActivity::class.java))
        }

        loadAppList()
        renderPendingBreaks()
    }

    /** Called only after the math-streak gate is passed. Deactivates device
     * admin (required before Android will allow uninstall) then hands off to
     * the system uninstall confirmation — the actual removal still needs one
     * final tap on the system's own "Uninstall" dialog, which is expected. */
    private fun proceedToUninstall() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, SessionAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin)
        }
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun loadAppList() {
        val apps = AppListAdapter.loadInstalledApps(packageManager)
            .filterNot { it.packageName == packageName } // don't let user un-allow itself
        recycler.adapter = AppListAdapter(
            apps,
            isChecked = { pkg -> SessionManager.getAllowedPackages(this).contains(pkg) },
            onToggle = { pkg, checked ->
                val current = SessionManager.getAllowedPackages(this).toMutableSet()
                if (checked) current.add(pkg) else current.remove(pkg)
                SessionManager.setAllowedPackages(this, current)
            }
        )
    }

    private fun renderPendingBreaks() {
        breaksListText.text = if (pendingBreaks.isEmpty()) {
            "No breaks scheduled — session will lock continuously."
        } else {
            pendingBreaks.mapIndexed { i, (offset, length) ->
                "${i + 1}. +$offset min for $length min"
            }.joinToString("\n")
        }
    }

    private fun updateStatus() {
        val active = SessionManager.isSessionActive(this)
        statusText.text = if (active) {
            val minsLeft = (SessionManager.sessionEndTime(this) - System.currentTimeMillis()) / 60000
            if (SessionManager.isInBreak(this)) {
                val breakEnd = SessionManager.currentBreak(this)?.second ?: 0L
                val breakMinsLeft = ((breakEnd - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                "On a scheduled break — $breakMinsLeft min left, then locked again ($minsLeft min total remaining)."
            } else {
                val next = SessionManager.nextBreak(this)
                val nextInfo = if (next != null) {
                    val untilBreak = ((next.first - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                    " Next break in $untilBreak min."
                } else ""
                "Session active — $minsLeft min remaining. Allowed-apps list is locked.$nextInfo"
            }
        } else if (!allPermissionsGranted()) {
            "Setup incomplete — grant permissions below before starting a session."
        } else if (!FocusAccessibilityService.isConnected) {
            "Enforcement service NOT running — toggle FocusLock off/on in Accessibility settings."
        } else {
            "No active session. Set a duration, schedule breaks if you want, and pick allowed apps."
        }
        // Lock editing the allowed list, duration, and breaks while a session runs.
        recycler.alpha = if (active) 0.5f else 1f
        for (i in 0 until (recycler.adapter?.itemCount ?: 0)) { /* checkboxes disabled via touch intercept below */ }
        recycler.suppressLayout(active)
        durationInput.isEnabled = !active
        uninstallButton.isEnabled = !active
        breakStartInput.isEnabled = !active
        breakLengthInput.isEnabled = !active
        addBreakButton.isEnabled = !active
    }

    private fun allPermissionsGranted(): Boolean {
        return isAccessibilityServiceEnabled() && isOverlayGranted() && isDeviceAdminActive()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${FocusAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(expected)
    }

    private fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, SessionAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    private fun requestPermissions() {
        if (!isAccessibilityServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable FocusLock under Downloaded/Installed apps", Toast.LENGTH_LONG).show()
            return
        }
        if (!isOverlayGranted()) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!isDeviceAdminActive()) {
            val admin = ComponentName(this, SessionAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Needed so FocusLock can't be uninstalled mid-session.")
            startActivity(intent)
            return
        }
        Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        updateStatus()
    }
}
