package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.service.wallpaper.LiveWallpaperConfig
import com.example.service.wallpaper.LiveWallpaperManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LiveWallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (LiveWallpaperManager.isAutoChangeEnabled(applicationContext)) {
                val nextConfig: LiveWallpaperConfig = LiveWallpaperManager.cycleNextLiveWallpaper(applicationContext)
                LiveWallpaperManager.setLastAutoChangeTime(applicationContext, System.currentTimeMillis())
                Log.i("LiveWallpaperWorker", "Auto changed live wallpaper to: ${nextConfig.title}")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("LiveWallpaperWorker", "Failed auto changing live wallpaper: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "LiveWallpaperAutoChangeWork"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (!LiveWallpaperManager.isAutoChangeEnabled(context)) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val intervalMins = LiveWallpaperManager.getAutoChangeInterval(context).coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<LiveWallpaperWorker>(
                intervalMins.toLong(),
                TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun triggerNow(context: Context): LiveWallpaperConfig {
            val nextConfig = LiveWallpaperManager.cycleNextLiveWallpaper(context)
            LiveWallpaperManager.setLastAutoChangeTime(context, System.currentTimeMillis())
            return nextConfig
        }
    }
}
