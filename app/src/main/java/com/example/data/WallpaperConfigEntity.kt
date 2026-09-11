package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpaper_config")
data class WallpaperConfigEntity(
    @PrimaryKey val screenType: String,
    val folderUri: String? = null,
    val isEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val scheduledTime: String? = null,
    val useSchedule: Boolean = false,
    val applyGradient: Boolean = false,
    val applyGrayscale: Boolean = false
)
