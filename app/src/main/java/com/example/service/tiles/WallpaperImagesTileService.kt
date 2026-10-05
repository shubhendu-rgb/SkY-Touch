package com.example.service.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.service.WallpaperWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperImagesTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Wallpaper: Images"
        tile.updateTile()
    }

    override fun onClick() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        scope.launch {
            try {
                val changed = WallpaperWorker.applyNextWallpaperNow(applicationContext, sourceFilter = "IMAGES_ONLY")
                withContext(Dispatchers.Main) {
                    tile.state = Tile.STATE_INACTIVE
                    tile.updateTile()
                    if (changed) {
                        Toast.makeText(applicationContext, "Wallpaper changed from selected images", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "No selected images. Add images in Wallpaper Changer first.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tile.state = Tile.STATE_INACTIVE
                    tile.updateTile()
                }
            }
        }
    }
}
