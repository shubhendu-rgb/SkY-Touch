package com.example.service.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SideDeckTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val config = db.sideDeckConfigDao().getConfigDirect()
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                val enabled = config?.enabled ?: false
                tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = if (enabled) "Side Deck (On)" else "Side Deck (Off)"
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.sideDeckConfigDao()
            val config = dao.getConfigDirect() ?: return@launch
            val newState = !config.enabled
            dao.insertOrUpdateConfig(config.copy(enabled = newState))
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                tile.state = if (newState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = if (newState) "Side Deck (On)" else "Side Deck (Off)"
                tile.updateTile()
            }
        }
    }
}
