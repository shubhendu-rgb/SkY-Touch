package com.example.util

import android.content.Context
import java.util.Calendar
import java.util.Locale

/**
 * A daily "from time to time" window the user sets (for example 10:00 PM to 7:00 AM).
 *
 * Used by the auto wallpaper changer (one window per screen: HOME / LOCK) and by the whole-app Night
 * Battery Saver (key [NIGHT_SAVER]). It lives in SharedPreferences, so it needs no database change
 * and nothing the user already saved is touched.
 */
object QuietHours {
    const val NIGHT_SAVER = "NIGHT_SAVER"

    private const val PREFS = "quiet_hours_prefs"
    private const val DEFAULT_START = 22 * 60 // 10:00 PM
    private const val DEFAULT_END = 7 * 60    // 7:00 AM

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, key: String): Boolean =
        prefs(context).getBoolean("${key}_enabled", false)

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("${key}_enabled", enabled).apply()
    }

    /** Minutes after midnight. */
    fun getStart(context: Context, key: String): Int =
        prefs(context).getInt("${key}_start", DEFAULT_START)

    fun getEnd(context: Context, key: String): Int =
        prefs(context).getInt("${key}_end", DEFAULT_END)

    fun setStart(context: Context, key: String, minutes: Int) {
        prefs(context).edit().putInt("${key}_start", minutes.coerceIn(0, 1439)).apply()
    }

    fun setEnd(context: Context, key: String, minutes: Int) {
        prefs(context).edit().putInt("${key}_end", minutes.coerceIn(0, 1439)).apply()
    }

    /** True when the window is switched on and the clock is inside it. Handles windows past midnight. */
    fun isActiveNow(context: Context, key: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isEnabled(context, key)) return false
        return isInside(getStart(context, key), getEnd(context, key), minutesOfDay(nowMillis))
    }

    fun isInside(start: Int, end: Int, now: Int): Boolean = when {
        start == end -> false
        start < end -> now in start until end
        else -> now >= start || now < end // e.g. 10 PM to 7 AM
    }

    /** Milliseconds until the window ends, or 0 when it is not active right now. */
    fun millisUntilEnd(context: Context, key: String, nowMillis: Long = System.currentTimeMillis()): Long {
        if (!isActiveNow(context, key, nowMillis)) return 0L
        return millisUntilMinute(getEnd(context, key), nowMillis)
    }

    /** Milliseconds until the next time the window starts or ends, whichever comes first. */
    fun millisUntilNextEdge(context: Context, key: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val toStart = millisUntilMinute(getStart(context, key), nowMillis)
        val toEnd = millisUntilMinute(getEnd(context, key), nowMillis)
        return minOf(toStart, toEnd)
    }

    private fun millisUntilMinute(minuteOfDay: Int, nowMillis: Long): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis - nowMillis
    }

    private fun minutesOfDay(nowMillis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** "10:00 PM" style text. */
    fun format12h(minutes: Int): String {
        val h24 = (minutes / 60) % 24
        val m = minutes % 60
        val suffix = if (h24 < 12) "AM" else "PM"
        val h12 = when (val h = h24 % 12) { 0 -> 12; else -> h }
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }
}
