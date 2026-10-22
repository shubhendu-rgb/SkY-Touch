package com.example.service.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.service.WallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Pause all wallpapers" switch.
 *
 * While paused, every automatic wallpaper change (auto-change timers, schedules, Quick Settings tiles,
 * notch gestures, live wallpaper cycling) does nothing, and the default Home / Lock wallpapers the user
 * picked are applied. That also replaces any live wallpaper (GIF / video) that was running.
 * Applying a wallpaper by hand still works. Turning the switch off lets the automation continue.
 */
object DefaultWallpaperManager {
    const val HOME = "HOME"
    const val LOCK = "LOCK"

    private const val PREFS = "default_wallpaper_prefs"
    private const val KEY_PAUSED = "all_wallpapers_paused"
    private const val DIR = "default_wallpapers"
    private const val TAG = "DefaultWallpaper"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPaused(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSED, false)

    private fun setPaused(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).commit()
    }

    private fun fileFor(context: Context, screen: String): File =
        File(File(context.filesDir, DIR), "default_${screen.lowercase()}.img")

    /** The saved default picture for [screen] ("HOME" or "LOCK"), or null when none is set. */
    fun getDefaultFile(context: Context, screen: String): File? =
        fileFor(context, screen).takeIf { it.exists() && it.length() > 0 }

    /** Copies the picked picture into app storage so access never expires. */
    suspend fun importDefault(context: Context, uri: Uri, screen: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val target = fileFor(context, screen)
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
                if (tmp.length() == 0L) {
                    tmp.delete()
                    return@withContext false
                }
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            } catch (e: Exception) {
                Log.e(TAG, "Failed importing default $screen wallpaper: ${e.message}", e)
                false
            }
        }

    fun clearDefault(context: Context, screen: String) {
        try { fileFor(context, screen).delete() } catch (_: Exception) {}
    }

    /**
     * Turns the automation off and applies the default Home and Lock wallpapers.
     * Returns false (and leaves everything as it was) when a default is missing or applying failed.
     */
    suspend fun pauseAllAndApplyDefaults(context: Context): Boolean = withContext(Dispatchers.IO) {
        val homeFile = getDefaultFile(context, HOME)
        val lockFile = getDefaultFile(context, LOCK)
        if (homeFile == null || lockFile == null) return@withContext false

        setPaused(context, true)
        try {
            val wm = WallpaperManager.getInstance(context)

            val homeBitmap = WallpaperWorker.processBitmap(
                context = context, uri = Uri.fromFile(homeFile),
                applyGradient = false, applyGrayscale = false
            ) ?: throw IllegalStateException("Could not read the default Home picture")
            val lockBitmap = WallpaperWorker.processBitmap(
                context = context, uri = Uri.fromFile(lockFile),
                applyGradient = false, applyGrayscale = false
            ) ?: throw IllegalStateException("Could not read the default Lock picture")

            // Setting a still picture on Home also removes any live wallpaper (GIF / video / preset).
            wm.setBitmap(homeBitmap, null, false, WallpaperManager.FLAG_SYSTEM)
            wm.setBitmap(lockBitmap, null, false, WallpaperManager.FLAG_LOCK)

            cancelScheduledWork(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying default wallpapers: ${e.message}", e)
            setPaused(context, false)
            false
        }
    }

    private fun cancelScheduledWork(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("AutoWallpaperWork_HOME")
            workManager.cancelUniqueWork("AutoWallpaperWork_LOCK")
            workManager.cancelUniqueWork(LiveWallpaperWorker.WORK_NAME)
        } catch (_: Exception) {}
    }

    /**
     * Switches the wallpaper changer off completely (auto-change, schedules, tiles, gestures, live cycling)
     * and leaves the wallpaper that is on the phone right now exactly as it is. Used by the Quick Settings tile.
     */
    suspend fun disableChanger(context: Context) = withContext(Dispatchers.IO) {
        setPaused(context, true)
        cancelScheduledWork(context)
    }

    /** Turns the automation back on: re-schedules whatever the user had enabled. */
    suspend fun resumeAll(context: Context) = withContext(Dispatchers.IO) {
        setPaused(context, false)
        try {
            val dao = AppDatabase.getDatabase(context).wallpaperConfigDao()
            for (screen in listOf(HOME, LOCK)) {
                val cfg = dao.getConfig(screen)
                if (cfg != null && cfg.isEnabled) {
                    WallpaperWorker.scheduleWallpaperWork(context, cfg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not re-schedule static wallpaper work: ${e.message}")
        }
        try {
            LiveWallpaperWorker.scheduleWork(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not re-schedule live wallpaper work: ${e.message}")
        }
    }
}
