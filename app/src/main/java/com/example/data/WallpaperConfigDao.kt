package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperConfigDao {
    @Query("SELECT * FROM wallpaper_config WHERE screenType = :screenType")
    fun getConfigFlow(screenType: String): Flow<WallpaperConfigEntity?>

    @Query("SELECT * FROM wallpaper_config WHERE screenType = :screenType")
    suspend fun getConfig(screenType: String): WallpaperConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: WallpaperConfigEntity)
}
