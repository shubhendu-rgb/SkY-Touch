package com.example.service.wallpaper

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

enum class LiveMediaType {
    VIDEO,
    GIF,
    PRESET_AURORA,
    PRESET_NEBULA,
    PRESET_MATRIX
}

enum class LiveCropMode {
    FILL,       // Center crop to fill screen
    FIT,        // Fit center with letterbox
    LEFT,       // Align to left edge
    RIGHT,      // Align to right edge
    STRETCH     // Stretch to bounds
}

enum class LiveColorFilter(val displayName: String) {
    NONE("Original"),
    GRAYSCALE("B&W Monochrome"),
    SEPIA("Vintage Sepia"),
    INVERT("Cyber Invert"),
    VIVID("Vivid Pop"),
    COOL("Neon Cool"),
    CONTRAST("High Contrast"),
    SUNSET("Sunset Warm")
}

data class LiveWallpaperConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Neon Aurora",
    val mediaType: LiveMediaType = LiveMediaType.PRESET_AURORA,
    val mediaPath: String? = null,
    val rotationDegrees: Int = 0,        // 0, 90, 180, 270
    val cropMode: LiveCropMode = LiveCropMode.FILL,
    val colorFilter: LiveColorFilter = LiveColorFilter.NONE,
    val playbackSpeed: Float = 1.0f,     // 0.5f, 1.0f, 1.25f, 1.5f, 2.0f
    val isMuted: Boolean = true,
    val blurRadius: Int = 0,             // 0 = off, 1..30
    val scaleFactor: Float = 1.0f,       // 0.5f .. 4.0f
    val offsetX: Float = 0.0f,           // pixel pan offset X
    val offsetY: Float = 0.0f,           // pixel pan offset Y
    val dateAdded: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("mediaType", mediaType.name)
            put("mediaPath", mediaPath ?: "")
            put("rotationDegrees", rotationDegrees)
            put("cropMode", cropMode.name)
            put("colorFilter", colorFilter.name)
            put("playbackSpeed", playbackSpeed.toDouble())
            put("isMuted", isMuted)
            put("blurRadius", blurRadius)
            put("scaleFactor", scaleFactor.toDouble())
            put("offsetX", offsetX.toDouble())
            put("offsetY", offsetY.toDouble())
            put("dateAdded", dateAdded)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): LiveWallpaperConfig {
            return LiveWallpaperConfig(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Live Wallpaper"),
                mediaType = try {
                    LiveMediaType.valueOf(json.optString("mediaType", LiveMediaType.PRESET_AURORA.name))
                } catch (_: Exception) {
                    LiveMediaType.PRESET_AURORA
                },
                mediaPath = json.optString("mediaPath", "").takeIf { it.isNotBlank() },
                rotationDegrees = json.optInt("rotationDegrees", 0),
                cropMode = try {
                    LiveCropMode.valueOf(json.optString("cropMode", LiveCropMode.FILL.name))
                } catch (_: Exception) {
                    LiveCropMode.FILL
                },
                colorFilter = try {
                    LiveColorFilter.valueOf(json.optString("colorFilter", LiveColorFilter.NONE.name))
                } catch (_: Exception) {
                    LiveColorFilter.NONE
                },
                playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat(),
                isMuted = json.optBoolean("isMuted", true),
                blurRadius = json.optInt("blurRadius", 0),
                scaleFactor = json.optDouble("scaleFactor", 1.0).toFloat().coerceIn(0.2f, 5.0f),
                offsetX = json.optDouble("offsetX", 0.0).toFloat(),
                offsetY = json.optDouble("offsetY", 0.0).toFloat(),
                dateAdded = json.optLong("dateAdded", System.currentTimeMillis())
            )
        }
    }
}

data class SystemLiveWallpaperInfo(
    val title: String,
    val packageName: String,
    val serviceName: String,
    val description: String?,
    val author: String?,
    val icon: Drawable?,
    val thumbnail: Drawable?
)

object LiveWallpaperManager {
    private const val PREFS_NAME = "sky_live_wallpaper_prefs"
    private const val KEY_ACTIVE_CONFIG = "active_live_config"
    private const val KEY_SAVED_LIST = "saved_live_wallpapers"
    private const val KEY_AUTO_CHANGE_ENABLED = "live_auto_change_enabled"
    private const val KEY_AUTO_CHANGE_INTERVAL = "live_auto_change_interval_mins"
    private const val KEY_LAST_AUTO_CHANGE_TIME = "live_last_auto_change_time"
    private const val KEY_AUTO_CHANGE_USE_SCHEDULE = "live_auto_change_use_schedule"
    private const val KEY_AUTO_CHANGE_SCHEDULED_TIME = "live_auto_change_scheduled_time"
    private const val KEY_AUTO_CHANGE_RANDOM = "live_auto_change_random"
    private const val LIVE_DIR_NAME = "live_wallpapers"

    fun isAutoChangeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHANGE_ENABLED, false)
    }

    fun setAutoChangeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHANGE_ENABLED, enabled)
            .apply()
    }

    fun getAutoChangeInterval(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AUTO_CHANGE_INTERVAL, 30)
    }

    fun setAutoChangeInterval(context: Context, intervalMins: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AUTO_CHANGE_INTERVAL, intervalMins.coerceAtLeast(1))
            .apply()
    }

    fun isAutoChangeUseSchedule(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHANGE_USE_SCHEDULE, false)
    }

    fun setAutoChangeUseSchedule(context: Context, useSchedule: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHANGE_USE_SCHEDULE, useSchedule)
            .apply()
    }

    fun getAutoChangeScheduledTime(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTO_CHANGE_SCHEDULED_TIME, "09:00") ?: "09:00"
    }

    fun setAutoChangeScheduledTime(context: Context, time: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTO_CHANGE_SCHEDULED_TIME, time)
            .apply()
    }

    fun isAutoChangeRandom(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHANGE_RANDOM, false)
    }

    fun setAutoChangeRandom(context: Context, random: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHANGE_RANDOM, random)
            .apply()
    }

    fun getLastAutoChangeTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTO_CHANGE_TIME, 0L)
    }

    fun setLastAutoChangeTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_CHANGE_TIME, time)
            .apply()
    }

    fun getActiveConfig(context: Context): LiveWallpaperConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ACTIVE_CONFIG, null)
        if (jsonStr.isNullOrEmpty()) {
            return LiveWallpaperConfig()
        }
        return try {
            LiveWallpaperConfig.fromJsonObject(JSONObject(jsonStr))
        } catch (e: Exception) {
            LiveWallpaperConfig()
        }
    }

    fun saveActiveConfig(context: Context, config: LiveWallpaperConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVE_CONFIG, config.toJsonObject().toString())
            .commit()

        // Also add or update in saved list
        addToSavedList(context, config)

        // Directly notify running service engines in real time
        try {
            SkyLiveWallpaperService.activeEngines.forEach { it.onWallpaperConfigChanged() }
        } catch (_: Exception) {}

        // Broadcast to running SkyLiveWallpaperService so changes take effect immediately
        try {
            val intent = Intent("com.example.LIVE_WALLPAPER_CHANGED").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    fun getSavedList(context: Context): List<LiveWallpaperConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SAVED_LIST, null) ?: return emptyList()
        val list = mutableListOf<LiveWallpaperConfig>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(LiveWallpaperConfig.fromJsonObject(obj))
            }
        } catch (_: Exception) {}
        return list
    }

    fun addToSavedList(context: Context, config: LiveWallpaperConfig) {
        val existing = getSavedList(context).toMutableList()
        val idx = existing.indexOfFirst { it.id == config.id || (it.mediaPath != null && it.mediaPath == config.mediaPath) }
        if (idx >= 0) {
            existing[idx] = config
        } else {
            existing.add(0, config)
        }
        val array = JSONArray()
        existing.forEach { array.put(it.toJsonObject()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_LIST, array.toString())
            .apply()
    }

    /**
     * Returns all available wallpapers to cycle through (saved user media + built-in presets).
     */
    fun getAvailableWallpapers(context: Context): List<LiveWallpaperConfig> {
        val saved = getSavedList(context).toMutableList()
        val defaultPresets = listOf(
            LiveWallpaperConfig(
                id = "preset_aurora",
                title = "Neon Aurora",
                mediaType = LiveMediaType.PRESET_AURORA
            ),
            LiveWallpaperConfig(
                id = "preset_nebula",
                title = "Deep Nebula",
                mediaType = LiveMediaType.PRESET_NEBULA
            ),
            LiveWallpaperConfig(
                id = "preset_matrix",
                title = "Digital Matrix",
                mediaType = LiveMediaType.PRESET_MATRIX
            )
        )
        for (preset in defaultPresets) {
            if (saved.none { it.mediaType == preset.mediaType && it.mediaPath.isNullOrEmpty() }) {
                saved.add(preset)
            }
        }
        return saved
    }

    /**
     * Checks if SkY Touch's SkyLiveWallpaperService is currently active on the device.
     */
    fun isOurLiveWallpaperActive(context: Context): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val info = wm.wallpaperInfo
            info != null && (info.packageName == context.packageName || info.serviceName.contains("SkyLiveWallpaperService"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if ANY live wallpaper is currently set as the device wallpaper.
     */
    fun isAnyLiveWallpaperActive(context: Context): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            wm.wallpaperInfo != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Cycles to next, previous, or random live wallpaper according to direction parameter.
     * Direction can be "NEXT", "PREV", "PREVIOUS", or "RANDOM".
     */
    fun cycleLiveWallpaper(context: Context, direction: String = "NEXT"): LiveWallpaperConfig {
        val available = getAvailableWallpapers(context)
        if (available.isEmpty()) {
            val fallback = LiveWallpaperConfig()
            saveActiveConfig(context, fallback)
            return fallback
        }

        val active = getActiveConfig(context)
        val currentIndex = available.indexOfFirst {
            if (it.mediaPath != null && active.mediaPath != null) {
                it.mediaPath == active.mediaPath
            } else {
                it.id == active.id || (it.mediaType == active.mediaType && it.mediaPath.isNullOrEmpty())
            }
        }

        val dir = direction.uppercase(Locale.ROOT)
        val nextIndex = when {
            dir == "RANDOM" -> {
                if (available.size > 1) {
                    var r = (0 until available.size).random()
                    if (r == currentIndex) r = (r + 1) % available.size
                    r
                } else 0
            }
            dir == "PREV" || dir == "PREVIOUS" -> {
                if (currentIndex > 0) currentIndex - 1 else available.size - 1
            }
            else -> {
                // "NEXT"
                if (currentIndex >= 0) (currentIndex + 1) % available.size else 0
            }
        }

        val nextConfig = available[nextIndex]
        saveActiveConfig(context, nextConfig)
        return nextConfig
    }

    /**
     * Cycles to the next available live wallpaper and saves it as active config.
     * Returns the newly active config.
     */
    fun cycleNextLiveWallpaper(context: Context, isRandom: Boolean = false): LiveWallpaperConfig {
        return cycleLiveWallpaper(context, if (isRandom) "RANDOM" else "NEXT")
    }

    fun removeFromSavedList(context: Context, configId: String) {
        val existing = getSavedList(context).filter { it.id != configId }
        val array = JSONArray()
        existing.forEach { array.put(it.toJsonObject()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_LIST, array.toString())
            .apply()
    }

    /**
     * Copies selected URI to app internal storage permanently so file permissions never expire.
     */
    fun importMediaFile(context: Context, uri: Uri, isVideo: Boolean): File? {
        return try {
            val liveDir = File(context.filesDir, LIVE_DIR_NAME)
            if (!liveDir.exists()) {
                liveDir.mkdirs()
            }
            val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: ""
            val isRealVideo = isVideo || mime.startsWith("video/")
            val extension = if (isRealVideo) ".mp4" else ".gif"
            val targetFile = File(liveDir, "live_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}$extension")
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            Log.e("LiveWallpaperManager", "Error importing media: ${e.message}", e)
            null
        }
    }

    /**
     * Query all installed live wallpapers on the system.
     */
    fun querySystemLiveWallpapers(context: Context): List<SystemLiveWallpaperInfo> {
        val pm = context.packageManager
        val intent = Intent(WallpaperService.SERVICE_INTERFACE)
        val resolveInfos = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        val list = mutableListOf<SystemLiveWallpaperInfo>()

        for (ri in resolveInfos) {
            val sInfo = ri.serviceInfo ?: continue
            // Skip our own service in the system list
            if (sInfo.packageName == context.packageName) continue

            try {
                val wpInfo = WallpaperInfo(context, ri)
                val title = sInfo.loadLabel(pm).toString()
                val desc = try { wpInfo.loadDescription(pm)?.toString() } catch (_: Exception) { null }
                val author = try { wpInfo.loadAuthor(pm)?.toString() } catch (_: Exception) { null }
                val thumb = try { wpInfo.loadThumbnail(pm) } catch (_: Exception) { null }
                val icon = try { sInfo.loadIcon(pm) } catch (_: Exception) { null }

                list.add(
                    SystemLiveWallpaperInfo(
                        title = title,
                        packageName = sInfo.packageName,
                        serviceName = sInfo.name,
                        description = desc,
                        author = author,
                        icon = icon,
                        thumbnail = thumb
                    )
                )
            } catch (_: Exception) {
                val title = sInfo.loadLabel(pm).toString()
                val icon = try { sInfo.loadIcon(pm) } catch (_: Exception) { null }
                list.add(
                    SystemLiveWallpaperInfo(
                        title = title,
                        packageName = sInfo.packageName,
                        serviceName = sInfo.name,
                        description = null,
                        author = null,
                        icon = icon,
                        thumbnail = null
                    )
                )
            }
        }
        return list
    }

    /**
     * Launch system live wallpaper preview and apply screen, or refresh directly if already active.
     */
    fun applyLiveWallpaper(context: Context, packageName: String? = null, serviceName: String? = null) {
        val targetPkg = packageName ?: context.packageName
        val targetService = serviceName ?: "com.example.service.wallpaper.SkyLiveWallpaperService"
        val component = ComponentName(targetPkg, targetService)

        // If our live wallpaper service is already the active system wallpaper,
        // config changes are applied instantly in real-time without needing system re-prompt!
        if (packageName == null && isOurLiveWallpaperActive(context)) {
            Toast.makeText(context, "Live wallpaper updated directly on your phone!", Toast.LENGTH_SHORT).show()
            try {
                SkyLiveWallpaperService.activeEngines.forEach { it.onWallpaperConfigChanged() }
            } catch (_: Exception) {}
            try {
                val intent = Intent("com.example.LIVE_WALLPAPER_CHANGED").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            } catch (_: Exception) {}
            return
        }

        val intents = listOf(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT", component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.service.wallpaper.CHANGE_LIVE_WALLPAPER").apply {
                putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT", component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_SET_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        var launched = false
        for (intent in intents) {
            try {
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {}
        }

        if (!launched) {
            Toast.makeText(context, "Could not open system preview. Please activate in phone Wallpaper settings.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Returns ColorMatrix array for color filters.
     */
    fun getColorMatrix(filter: LiveColorFilter): FloatArray {
        return when (filter) {
            LiveColorFilter.NONE -> floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.GRAYSCALE -> floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.SEPIA -> floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.INVERT -> floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.VIVID -> floatArrayOf(
                1.4f, -0.2f, -0.2f, 0f, 10f,
                -0.2f, 1.4f, -0.2f, 0f, 10f,
                -0.2f, -0.2f, 1.4f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.COOL -> floatArrayOf(
                0.7f, 0f, 0f, 0f, 0f,
                0f, 1.1f, 0f, 0f, 10f,
                0f, 0.2f, 1.4f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.CONTRAST -> floatArrayOf(
                1.6f, 0f, 0f, 0f, -50f,
                0f, 1.6f, 0f, 0f, -50f,
                0f, 0f, 1.6f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            )
            LiveColorFilter.SUNSET -> floatArrayOf(
                1.4f, 0.1f, 0f, 0f, 25f,
                0.1f, 1.1f, 0f, 0f, 10f,
                0f, 0f, 0.7f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }
    }
}
