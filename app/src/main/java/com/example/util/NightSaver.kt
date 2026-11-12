package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.service.NotchAccessibilityService
import com.example.service.wallpaper.DefaultWallpaperManager
import com.example.service.wallpaper.SkyLiveWallpaperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Night Battery Saver controller.
 *
 * While the sleeping window is active the whole app is suspended: wallpaper jobs are cancelled (no more
 * wake-ups), overlays are removed, the accessibility service stops receiving events, auto click stops and
 * the live wallpaper freezes on one frame. When the window ends everything is switched back on.
 *
 * It works WITHOUT the accessibility service: its own alarm + boot / time-change receiver drive it.
 */
object NightSaver {
    private const val TAG = "NightSaver"
    private const val ACTION_TICK = "com.example.ACTION_NIGHT_SAVER_EDGE"
    private const val REQUEST_CODE = 7042
    private const val PREFS = "night_saver_state"
    private const val KEY_SUSPENDED = "suspended"

    @Volatile private var cachedAt = 0L
    @Volatile private var cachedActive = false

    /** Cheap "is the sleeping window active right now" check for hot paths (cached for 5 s). */
    fun isActive(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - cachedAt > 5_000L) {
            cachedActive = QuietHours.isActiveNow(context, QuietHours.NIGHT_SAVER)
            cachedAt = now
        }
        return cachedActive
    }

    private fun invalidateCache() { cachedAt = 0L }

    /**
     * Re-evaluates everything. Call after the user changes the Night Saver settings, at the start / end
     * edge of the window, after boot and after a time change. Safe to call many times.
     */
    fun sync(context: Context, force: Boolean = false, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        invalidateCache()
        scheduleNextEdge(app)

        val active = isActive(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasSuspended = prefs.getBoolean(KEY_SUSPENDED, false)
        prefs.edit().putBoolean(KEY_SUSPENDED, active).apply()

        // Only when the state really flipped (or the user changed the settings): rebuilding the overlays on every
        // screen-on would cost more battery than it saves.
        if (force || active != wasSuspended) {
            // Tell the running parts of the app (overlays, accessibility events, live wallpaper engines)
            try { NotchAccessibilityService.instance?.onNightSaverChanged() } catch (_: Exception) {}
            try { SkyLiveWallpaperService.activeEngines.forEach { it.onNightSaverChanged() } } catch (_: Exception) {}
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (active) {
                    DefaultWallpaperManager.cancelScheduledWork(app)
                } else if ((wasSuspended || force) && !DefaultWallpaperManager.isPaused(app)) {
                    // Window ended (or Night Saver was switched off): bring the wallpaper jobs back
                    DefaultWallpaperManager.rescheduleEnabledWork(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "sync failed: ${e.message}")
            } finally {
                onDone?.invoke()
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, NightSaverReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** One alarm for the next start or end of the window. It wakes the phone once, so the app resumes on time. */
    private fun scheduleNextEdge(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            if (!QuietHours.isEnabled(context, QuietHours.NIGHT_SAVER)) {
                am.cancel(pi)
                return
            }
            val triggerAt = System.currentTimeMillis() +
                QuietHours.millisUntilNextEdge(context, QuietHours.NIGHT_SAVER) + 1_000L
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule edge alarm: ${e.message}")
        }
    }
}

/** Fires at the start / end of the sleeping window, after boot, app update and clock / timezone changes. */
class NightSaverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        NightSaver.sync(context, force = false) { try { pending.finish() } catch (_: Exception) {} }
    }
}
