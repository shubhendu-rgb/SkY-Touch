package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpaper_config")
data class WallpaperConfigEntity(
    @PrimaryKey val screenType: String,
    val folderUri: String? = null,
    val imageUris: String? = null,
    val isEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val scheduledTime: String? = null,
    val useSchedule: Boolean = false,
    val applyGradient: Boolean = false,
    val applyGrayscale: Boolean = false,
    val cropAlignment: String = "CENTER", // "LEFT", "CENTER", "RIGHT"
    val isScrollable: Boolean = false,
    val blurRadius: Int = 0 // 0 = off, 1..30
) {
    fun getImageUriList(): List<String> {
        if (imageUris.isNullOrBlank()) return emptyList()
        return imageUris.split("|").filter { it.isNotBlank() }
    }
}
