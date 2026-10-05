package com.example.service.tiles

import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NotchTileService : TileService() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    override fun onStartListening() {
        super.onStartListening()
        val enabled = prefs.getBoolean("notch_master_toggle", true)
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (enabled) "Notch (On)" else "Notch (Off)"
        tile.updateTile()
    }

    override fun onClick() {
        val enabled = prefs.getBoolean("notch_master_toggle", true)
        val newState = !enabled
        prefs.edit().putBoolean("notch_master_toggle", newState).apply()
        
        val tile = qsTile ?: return
        tile.state = if (newState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (newState) "Notch (On)" else "Notch (Off)"
        tile.updateTile()
    }
}
