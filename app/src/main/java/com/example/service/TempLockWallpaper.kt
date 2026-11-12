package com.example.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.service.wallpaper.DefaultWallpaperManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Notch gesture "Temporary lock wallpaper": shows a chosen picture on the lock screen for 2 minutes and
 * then puts the previous lock screen back.
 *
 * Before the first change the current lock wallpaper is remembered:
 *  - if the lock screen has no wallpaper of its own it simply follows the home wallpaper, so going back = clearing the lock wallpaper;
 *  - otherwise the current lock picture is saved to a private file (when Android allows reading it), or the
 *    default Lock picture from the wallpaper settings is used as the way back.
 * If none of these is possible the lock screen is NOT changed (so it can never get stuck on the temporary picture).
 */
object TempLockWallpaper {
    private const val TAG = "TempLockWallpaper"
    private const val PREFS = "temp_lock_wallpaper"
    private const val KEY_RESTORE_AT = "restore_at"
    private const val KEY_MODE = "mode"
    private const val KEY_FILE = "restore_file"
    private const val WORK_NAME = "TempLockWallpaperRestore"
    const val DURATION_MS = 2 * 60 * 1000L

    private const val MODE_CLEAR = "CLEAR"
    private const val MODE_FILE = "FILE"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean = prefs(context).getLong(KEY_RESTORE_AT, 0L) > 0L

    /** Applies [imagePath] to the lock screen now and schedules the way back. Returns an error text, or null on success. */
    suspend fun start(context: Context, imagePath: String?): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val imageFile = imagePath?.let { File(it) }
        if (imageFile == null || !imageFile.exists()) return@withContext "No picture selected for this gesture"

        try {
            val wm = WallpaperManager.getInstance(app)

            // Remember the current lock screen only once: a second trigger must not save the temporary picture
            if (!isActive(app)) {
                val saved = rememberCurrentLock(app, wm)
                if (!saved) {
                    return@withContext "Can't save the current lock wallpaper to go back to. Set a default Lock picture in Wallpaper settings first."
                }
            }

            val bitmap = WallpaperWorker.processBitmap(
                context = app,
                uri = Uri.fromFile(imageFile),
                applyGradient = false,
                applyGrayscale = false
            ) ?: return@withContext "Could not read the selected picture"

            val crop = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            wm.setBitmap(bitmap, crop, false, WallpaperManager.FLAG_LOCK)

            val restoreAt = System.currentTimeMillis() + DURATION_MS
            prefs(app).edit().putLong(KEY_RESTORE_AT, restoreAt).commit()
            scheduleWorker(app, DURATION_MS)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Temporary lock wallpaper failed: ${e.message}", e)
            // Never leave a half applied state behind
            if (isActive(app)) restoreNow(app)
            "Could not change the lock wallpaper"
        }
    }

    /** Remembers how to get the lock screen back. Returns false when that is impossible. */
    private fun rememberCurrentLock(context: Context, wm: WallpaperManager): Boolean {
        val dir = File(context.filesDir, "temp_lock_backup").apply { mkdirs() }
        val backup = File(dir, "previous_lock.img")
        backup.delete()

        // 1. The lock screen follows the home wallpaper (no own lock wallpaper): going back = clear it
        val lockId = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wm.getWallpaperId(WallpaperManager.FLAG_LOCK) else -1
        } catch (_: Exception) { 0 }
        if (lockId < 0) {
            prefs(context).edit().putString(KEY_MODE, MODE_CLEAR).remove(KEY_FILE).commit()
            return true
        }

        // 2. Save the current lock picture
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use { pfd ->
                    java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                        backup.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                if (backup.exists() && backup.length() > 0 && isDecodable(backup)) {
                    prefs(context).edit().putString(KEY_MODE, MODE_FILE).putString(KEY_FILE, backup.absolutePath).commit()
                    return true
                }
                backup.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the current lock wallpaper: ${e.message}")
            backup.delete()
        }

        // 3. The default Lock picture from the wallpaper settings
        val default = DefaultWallpaperManager.getDefaultFile(context, DefaultWallpaperManager.LOCK)
        if (default != null) {
            prefs(context).edit().putString(KEY_MODE, MODE_FILE).putString(KEY_FILE, default.absolutePath).commit()
            return true
        }
        return false
    }

    private fun isDecodable(file: File): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    /**
     * Copies a picture the user picked into private app storage (scaled down to a sensible size) so the
     * gesture keeps working after the original is moved. Returns the new file path, or null on failure.
     */
    fun importPicture(context: Context, uri: Uri): String? {
        return try {
            val app = context.applicationContext
            val dir = File(app.filesDir, "temp_lock_pictures").apply { mkdirs() }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2600) sample *= 2
            val bitmap = app.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null

            val target = File(dir, "lock_${System.currentTimeMillis()}.jpg")
            target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Importing the picture failed: ${e.message}", e)
            null
        }
    }

    /** Deletes a picture imported with [importPicture] (ignores any other path). */
    fun deletePicture(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val dir = File(context.applicationContext.filesDir, "temp_lock_pictures")
            val file = File(path)
            if (file.parentFile?.absolutePath == dir.absolutePath) file.delete()
        } catch (_: Exception) {}
    }

    /** Puts the previous lock screen back right now (no-op when nothing is pending). */
    fun restoreNow(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        if (p.getLong(KEY_RESTORE_AT, 0L) <= 0L) return
        val mode = p.getString(KEY_MODE, MODE_CLEAR)
        try {
            val wm = WallpaperManager.getInstance(app)
            if (mode == MODE_FILE) {
                val file = p.getString(KEY_FILE, null)?.let { File(it) }
                if (file != null && file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK)
                    } else {
                        clearLock(wm)
                    }
                } else {
                    clearLock(wm)
                }
            } else {
                clearLock(wm)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restoring the lock wallpaper failed: ${e.message}", e)
        } finally {
            p.edit().remove(KEY_RESTORE_AT).remove(KEY_MODE).remove(KEY_FILE).commit()
            try { WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME) } catch (_: Exception) {}
        }
    }

    private fun clearLock(wm: WallpaperManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wm.clear(WallpaperManager.FLAG_LOCK)
    }

    /** Restores when the time is up. Called when the service starts and by the backup timer. */
    fun restoreIfDue(context: Context) {
        val at = prefs(context).getLong(KEY_RESTORE_AT, 0L)
        if (at > 0L && System.currentTimeMillis() >= at) restoreNow(context)
    }

    /** Milliseconds until the pending restore (0 when nothing is pending or it is already due). */
    fun remainingMs(context: Context): Long {
        val at = prefs(context).getLong(KEY_RESTORE_AT, 0L)
        return if (at <= 0L) 0L else (at - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun scheduleWorker(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Backup timer: puts the lock screen back even if the accessibility service was restarted meanwhile. */
    class RestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            restoreNow(applicationContext)
            return Result.success()
        }
    }
}
