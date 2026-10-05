package com.example.service.tiles

import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.MainActivity
import com.example.service.wallpaper.LiveWallpaperManager
import com.example.service.wallpaper.SkyLiveWallpaperService

class LiveWallpaperTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val activeConfig = LiveWallpaperManager.getActiveConfig(applicationContext)
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Live Wallpaper"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = activeConfig.title
        }
        tile.updateTile()
    }

    override fun onClick() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        try {
            val nextConfig = LiveWallpaperManager.cycleNextLiveWallpaper(applicationContext)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = nextConfig.title
            }
            tile.updateTile()

            val wm = WallpaperManager.getInstance(applicationContext)
            val isOurServiceActive = wm.wallpaperInfo?.packageName == packageName

            if (!isOurServiceActive) {
                Toast.makeText(
                    applicationContext,
                    "Live Wallpaper set to: ${nextConfig.title}\nTap 'Set Wallpaper' to activate!",
                    Toast.LENGTH_LONG
                ).show()

                val applyIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(applicationContext, SkyLiveWallpaperService::class.java)
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        applyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(applyIntent)
                }
            } else {
                Toast.makeText(
                    applicationContext,
                    "Live Wallpaper changed to: ${nextConfig.title}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Error changing live wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
