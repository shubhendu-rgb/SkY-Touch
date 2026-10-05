package com.example.service.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.service.NotchAccessibilityService

class AutoClickTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val service = NotchAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "SkY Touch Accessibility Service is not running", Toast.LENGTH_SHORT).show()
        } else {
            service.toggleAutoClickFloatingMenu()
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isMenuVisible = NotchAccessibilityService.isAutoClickMenuVisible
        tile.state = if (isMenuVisible) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
