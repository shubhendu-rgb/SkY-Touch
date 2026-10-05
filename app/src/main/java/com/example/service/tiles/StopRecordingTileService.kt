package com.example.service.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.service.NotchAccessibilityService

/**
 * Quick Settings Tile to stop silent camera recording.
 * When a silent recording is active, this tile shows as active and tapping it
 * immediately stops and saves the video recording safely.
 */
class StopRecordingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = NotchAccessibilityService.isSilentRecording
        if (isRecording) {
            val stopped = NotchAccessibilityService.stopSilentRecording(this)
            if (stopped) {
                Toast.makeText(this, "Silent recording stopped and saved", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No active silent recording", Toast.LENGTH_SHORT).show()
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRecording = NotchAccessibilityService.isSilentRecording
        tile.state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRecording) "Stop Recording" else "Silent Cam"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRecording) "Recording Active • Tap to Stop" else "Idle"
        }
        tile.updateTile()
    }
}
