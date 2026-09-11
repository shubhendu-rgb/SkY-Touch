package com.example.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.example.data.AppDatabase
import com.example.data.WallpaperConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val screenType = inputData.getString("screenType") ?: return@withContext Result.failure()
        
        try {
            val db = AppDatabase.getDatabase(context)
            val config = db.wallpaperConfigDao().getConfig(screenType)
            
            if (config == null || !config.isEnabled || config.folderUri.isNullOrEmpty()) {
                return@withContext Result.failure()
            }

            val folderUri = Uri.parse(config.folderUri)
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)

            if (documentFile == null || !documentFile.exists() || !documentFile.isDirectory) {
                return@withContext Result.failure()
            }

            val validImages = documentFile.listFiles().filter { file ->
                val type = file.type
                type != null && type.startsWith("image/")
            }

            if (validImages.isEmpty()) {
                return@withContext Result.failure()
            }

            val randomImage = validImages.random()
            
            val bitmap = processBitmap(context, randomImage.uri, config.applyGradient, config.applyGrayscale)
            
            if (bitmap != null) {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val flag = if (screenType == "LOCK") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                wallpaperManager.setBitmap(bitmap, null, true, flag)
                Log.i("WallpaperWorker", "Successfully set $screenType wallpaper from: ${randomImage.name}")
            }

            // If we are using exact time scheduling, we need to schedule the next day's work manually
            // since PeriodicWorkRequest doesn't easily support exact time of day.
            if (config.useSchedule && config.scheduledTime != null) {
                scheduleWallpaperWork(context, config)
            } else if (!config.useSchedule && config.intervalMinutes < 15) {
                // Workaround for WorkManager's 15 min minimum periodic limit
                scheduleWallpaperWork(context, config)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Error applying wallpaper: ${e.message}", e)
            Result.failure()
        }
    }
    
    private fun processBitmap(context: Context, uri: Uri, applyGradient: Boolean, applyGrayscale: Boolean): Bitmap? {
        try {
            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            var resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            if (applyGrayscale) {
                val grayBitmap = Bitmap.createBitmap(resultBitmap.width, resultBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(grayBitmap)
                val paint = Paint()
                val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(resultBitmap, 0f, 0f, paint)
                resultBitmap = grayBitmap
            }
            
            if (applyGradient) {
                val canvas = Canvas(resultBitmap)
                val paint = Paint()
                val w = resultBitmap.width.toFloat()
                val h = resultBitmap.height.toFloat()
                
                // Top Gradient (0 to 20% of height)
                paint.shader = LinearGradient(0f, 0f, 0f, h * 0.2f, 
                    intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h * 0.2f, paint)
                
                // Bottom Gradient (80% to 100% of height)
                paint.shader = LinearGradient(0f, h * 0.8f, 0f, h, 
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#CC000000")),
                    null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, h * 0.8f, w, h, paint)
            }
            
            return resultBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    companion object {
        fun scheduleWallpaperWork(context: Context, config: WallpaperConfigEntity) {
            val workManager = WorkManager.getInstance(context)
            val workName = "AutoWallpaperWork_${config.screenType}"
            
            if (!config.isEnabled) {
                workManager.cancelUniqueWork(workName)
                return
            }
            
            val data = Data.Builder().putString("screenType", config.screenType).build()

            if (config.useSchedule && config.scheduledTime != null) {
                // Scheduled exact time
                val parts = config.scheduledTime.split(":")
                val targetHour = parts[0].toInt()
                val targetMin = parts[1].toInt()
                
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
                
                val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()
                    
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            } else {
                // Interval based
                if (config.intervalMinutes >= 15) {
                    val request = PeriodicWorkRequestBuilder<WallpaperWorker>(config.intervalMinutes.toLong(), TimeUnit.MINUTES)
                        .setInputData(data)
                        .build()
                    workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
                } else {
                    // Less than 15 mins (WorkManager minimum) -> Use OneTime chaining
                    val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                        .setInitialDelay(config.intervalMinutes.toLong(), TimeUnit.MINUTES)
                        .setInputData(data)
                        .build()
                    workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
                }
            }
        }
    }
}
