package com.example.service.wallpaper

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class LiveWallpaperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!LiveWallpaperManager.isAutoChangeEnabled(context)) {
                return@withContext Result.failure()
            }

            val isRandom = LiveWallpaperManager.isAutoChangeRandom(context)
            LiveWallpaperManager.cycleNextLiveWallpaper(context, isRandom = isRandom)
            LiveWallpaperManager.setLastAutoChangeTime(context, System.currentTimeMillis())

            // If using exact schedule or interval < 15 min, re-enqueue next work
            val useSchedule = LiveWallpaperManager.isAutoChangeUseSchedule(context)
            val interval = LiveWallpaperManager.getAutoChangeInterval(context)
            if (useSchedule || interval < 15) {
                scheduleWork(context)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("LiveWallpaperWorker", "Failed auto change live wallpaper: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "LiveWallpaperAutoChangeWork"

        fun scheduleWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val isEnabled = LiveWallpaperManager.isAutoChangeEnabled(context)

            if (!isEnabled) {
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
                if (intervalMinutes >= 15) {
                    val request = PeriodicWorkRequestBuilder<LiveWallpaperWorker>(
                        intervalMinutes.toLong(),
                        TimeUnit.MINUTES
                    ).build()
                    workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
                } else {
                    val request = OneTimeWorkRequestBuilder<LiveWallpaperWorker>()
                        .setInitialDelay(intervalMinutes.coerceAtLeast(1).toLong(), TimeUnit.MINUTES)
                        .build()
                    workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
                }
            }
        }
    }
}
