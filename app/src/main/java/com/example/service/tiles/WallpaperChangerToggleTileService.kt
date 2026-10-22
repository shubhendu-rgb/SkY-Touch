package com.example.service.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.service.wallpaper.DefaultWallpaperManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings switch for the whole wallpaper changer.
 * ON  = the changer works as set up in the app.
 * OFF = totally disabled: auto-change, schedules, the wallpaper tiles, gestures and live wallpaper
 *       cycling all do nothing. The wallpaper on the phone stays as it is.
 */
class WallpaperChangerToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val context = applicationContext
            if (DefaultWallpaperManager.isPaused(context)) {
                DefaultWallpaperManager.resumeAll(context)
                Toast.makeText(context, "Wallpaper changer is ON", Toast.LENGTH_SHORT).show()
            } else {
                DefaultWallpaperManager.disableChanger(context)
                Toast.makeText(
                    context,
                    "Wallpaper changer is OFF. Your current wallpaper stays as it is.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            refreshTile()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val off = DefaultWallpaperManager.isPaused(applicationContext)
        tile.state = if (off) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = "Wallpaper Changer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (off) "Off" else "On"
        }
        tile.updateTile()
    }
}
