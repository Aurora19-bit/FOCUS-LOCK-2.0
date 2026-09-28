package com.vidhya.focuslock

import android.content.Context

/**
 * Single source of truth for whether a focus session is active, when it ends,
 * which package names are allowed during a session, and any scheduled break
 * windows within it. Backed by SharedPreferences so it survives process
 * death and reboot.
 */
object SessionManager {
    private const val PREFS = "focuslock_prefs"
    private const val KEY_START_TIME = "session_start_time"
    private const val KEY_END_TIME = "session_end_time"
    private const val KEY_ALLOWED = "allowed_packages"
    private const val KEY_BREAKS = "break_windows" // "offsetMin:durationMin" entries

    // Packages that must ALWAYS be reachable, even mid-session, so the phone
    // stays usable (dialer, our own app, the default launcher).
    fun corePackages(context: Context): Set<String> {
        val launcher = context.packageManager.resolveActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

        val imePackage = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/')

        return setOfNotNull(
            context.packageName,
            launcher,
            imePackage,
            "android",
            "com.android.systemui",
            "com.android.dialer",
            "com.android.phone",
            "com.android.server.telecom"
        )
    }

    fun isSessionActive(context: Context): Boolean {
        val end = prefs(context).getLong(KEY_END_TIME, 0L)
        return end > System.currentTimeMillis()
    }

    fun sessionEndTime(context: Context): Long = prefs(context).getLong(KEY_END_TIME, 0L)
    fun sessionStartTime(context: Context): Long = prefs(context).getLong(KEY_START_TIME, 0L)

    /**
     * @param breaks list of (offsetMinutesFromStart, durationMinutes), decided
     * up front and baked into the session — there is deliberately no API to
     * add a break once a session is running.
     */
    fun startSession(context: Context, durationMillis: Long, breaks: List<Pair<Int, Int>> = emptyList()) {
        val start = System.currentTimeMillis()
        val breakEntries = breaks.map { (offset, dur) -> "$offset:$dur" }.toSet()
        prefs(context).edit()
            .putLong(KEY_START_TIME, start)
            .putLong(KEY_END_TIME, start + durationMillis)
            .putStringSet(KEY_BREAKS, breakEntries)
            .apply()
    }

    /** Ends the session immediately. Only ever called from inside the app itself
     * (e.g. an explicit "end early" flow you design), never from a blocked app. */
    fun endSessionNow(context: Context) {
        prefs(context).edit()
            .putLong(KEY_END_TIME, 0L)
            .putLong(KEY_START_TIME, 0L)
            .putStringSet(KEY_BREAKS, emptySet())
            .apply()
    }

    /** Absolute (startMillis, endMillis) for every scheduled break in the
     * current session, derived from the session's start time. */
    fun breakWindows(context: Context): List<Pair<Long, Long>> {
        val start = sessionStartTime(context)
        if (start == 0L) return emptyList()
        val raw = prefs(context).getStringSet(KEY_BREAKS, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val parts = entry.split(":")
            val offsetMin = parts.getOrNull(0)?.toLongOrNull()
            val durMin = parts.getOrNull(1)?.toLongOrNull()
            if (offsetMin == null || durMin == null) return@mapNotNull null
            val windowStart = start + offsetMin * 60_000L
            val windowEnd = windowStart + durMin * 60_000L
            windowStart to windowEnd
        }.sortedBy { it.first }
    }

    fun isInBreak(context: Context): Boolean {
        val now = System.currentTimeMillis()
        return breakWindows(context).any { (s, e) -> now in s until e }
    }

    /** The break currently running, if any — handy for status text/countdowns. */
    fun currentBreak(context: Context): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        return breakWindows(context).firstOrNull { (s, e) -> now in s until e }
    }

    /** The next upcoming break, if any, for showing "next break in X min". */
    fun nextBreak(context: Context): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        return breakWindows(context).firstOrNull { (s, _) -> s > now }
    }

    fun getAllowedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet()
    }

    fun setAllowedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALLOWED, packages).apply()
    }

    fun isAllowed(context: Context, packageName: String): Boolean {
        return packageName in corePackages(context) || packageName in getAllowedPackages(context)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
