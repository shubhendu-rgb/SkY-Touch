package com.example.service.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.service.audio.EqualizerController

class EqualizerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        EqualizerController.init(applicationContext)
        refreshTile()
    }

    override fun onClick() {
        EqualizerController.toggle(applicationContext)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val s = EqualizerController.state.value
        tile.state = if (s.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Equalizer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (s.enabled) s.presetName else "Off"
        }
        tile.updateTile()
    }
}
