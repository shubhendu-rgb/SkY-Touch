package com.example.service.wallpaper

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.util.QuietHours
import java.util.Calendar
import java.util.concurrent.TimeUnit

class LiveWallpaperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (DefaultWallpaperManager.isPaused(context)) return@withContext Result.success()
            if (!LiveWallpaperManager.isAutoChangeEnabled(context)) {
                return@withContext Result.failure()
            }

            val useSchedule = LiveWallpaperManager.isAutoChangeUseSchedule(context)
            val intervalMs = TimeUnit.MINUTES.toMillis(LiveWallpaperManager.getAutoChangeInterval(context).toLong())
            val now = System.currentTimeMillis()
            val last = LiveWallpaperManager.getLastAutoChangeTime(context)

            if (QuietHours.isActiveNow(context, QuietHours.NIGHT_SAVER) || com.example.util.GameMode.isActive()) {
                // Night Battery Saver / Game Mode: no change now, look again a bit later
                scheduleWork(context, minDelayMs = TimeUnit.MINUTES.toMillis(5))
                return@withContext Result.success()
            }

            // Interval mode: never change earlier than the chosen interval (a stray or immediate run is ignored)
            val minGapMs = if (useSchedule) TimeUnit.MINUTES.toMillis(30) else intervalMs * 9 / 10
            val tooEarly = last > 0L && last <= now && (now - last) < minGapMs
            if (!tooEarly) {
                val isRandom = LiveWallpaperManager.isAutoChangeRandom(context)
                LiveWallpaperManager.cycleNextLiveWallpaper(context, isRandom = isRandom)
                LiveWallpaperManager.setLastAutoChangeTime(context, System.currentTimeMillis())
            }

            // Always chain the next run (exact time of day, or last change + interval)
            scheduleWork(context)

            Result.success()
        } catch (e: Exception) {
            Log.e("LiveWallpaperWorker", "Failed auto change live wallpaper: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "LiveWallpaperAutoChangeWork"

        fun scheduleWork(context: Context, minDelayMs: Long = 0L) {
            val workManager = WorkManager.getInstance(context)
            val isEnabled = LiveWallpaperManager.isAutoChangeEnabled(context)

            // Changer switched off or Night Battery Saver active: keep nothing scheduled
            if (!isEnabled || DefaultWallpaperManager.isPaused(context) || com.example.util.NightSaver.isActive(context)) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val useSchedule = LiveWallpaperManager.isAutoChangeUseSchedule(context)
            val scheduledTime = LiveWallpaperManager.getAutoChangeScheduledTime(context)
            val intervalMinutes = LiveWallpaperManager.getAutoChangeInterval(context)

            if (useSchedule && !scheduledTime.isNullOrEmpty()) {
                val parts = scheduledTime.split(":")
                val targetHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                val targetMin = parts.getOrNull(1)?.toIntOrNull() ?: 0

                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                }

                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }

                val delayMs = target.timeInMillis - now.timeInMillis
                val request = OneTimeWorkRequestBuilder<LiveWallpaperWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()

                workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            } else {
                // One-time work chained after every change: the next change is exactly "last change + interval".
                // (Periodic work runs once immediately when it is enqueued and its timer restarts every time it is
                // re-scheduled, which made the wallpaper change much earlier than the chosen interval.)
                val intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes.coerceAtLeast(1).toLong())
                val now = System.currentTimeMillis()
                var base = LiveWallpaperManager.getLastAutoChangeTime(context)
                if (base <= 0L || base > now) {
                    base = now
                    LiveWallpaperManager.setLastAutoChangeTime(context, now)
                }
                val delay = maxOf((base + intervalMs - now).coerceAtLeast(0L), minDelayMs)
                val request = OneTimeWorkRequestBuilder<LiveWallpaperWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()
                workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
}
