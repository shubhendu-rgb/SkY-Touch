package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotchConfigEntity::class,
        GestureActionEntity::class,
        TriggerStatEntity::class,
        SideDeckConfigEntity::class,
        CodeDetectionConfigEntity::class,
        DetectedCodeEntity::class,
        TextAssistantConfigEntity::class,
        TextSnippetEntity::class,
        WallpaperConfigEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notchConfigDao(): NotchConfigDao
    abstract fun gestureActionDao(): GestureActionDao
    abstract fun triggerStatDao(): TriggerStatDao
    abstract fun sideDeckConfigDao(): SideDeckConfigDao
    abstract fun codeDetectionDao(): CodeDetectionDao
    abstract fun textAssistantDao(): TextAssistantDao
    abstract fun wallpaperConfigDao(): WallpaperConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sky_touch_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
