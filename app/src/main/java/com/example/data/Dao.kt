package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotchConfigDao {
    @Query("SELECT * FROM notch_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<NotchConfigEntity?>

    @Query("SELECT * FROM notch_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigDirect(): NotchConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: NotchConfigEntity)
}

@Dao
interface GestureActionDao {
    @Query("SELECT * FROM gesture_actions")
    fun getAllActionsFlow(): Flow<List<GestureActionEntity>>

    @Query("SELECT * FROM gesture_actions")
    suspend fun getAllActionsDirect(): List<GestureActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: GestureActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<GestureActionEntity>)

    @Query("DELETE FROM gesture_actions WHERE gestureName = :gestureName")
    suspend fun deleteAction(gestureName: String)
}

@Dao
interface TriggerStatDao {
    @Query("SELECT * FROM trigger_stats")
    fun getAllStatsFlow(): Flow<List<TriggerStatEntity>>

    @Query("INSERT INTO trigger_stats (gestureName, count) VALUES (:gestureName, 1) ON CONFLICT(gestureName) DO UPDATE SET count = count + 1")
    suspend fun incrementStat(gestureName: String)

    @Query("UPDATE trigger_stats SET count = 0")
    suspend fun resetStats()

    @Query("DELETE FROM trigger_stats WHERE gestureName = :gestureName")
    suspend fun deleteStat(gestureName: String)
}

@Dao
interface SideDeckConfigDao {
    @Query("SELECT * FROM side_deck_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<SideDeckConfigEntity?>

    @Query("SELECT * FROM side_deck_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigDirect(): SideDeckConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: SideDeckConfigEntity)
}

@Dao
interface CodeDetectionDao {
    @Query("SELECT * FROM code_detection_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<CodeDetectionConfigEntity?>

    @Query("SELECT * FROM code_detection_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigDirect(): CodeDetectionConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: CodeDetectionConfigEntity)

    @Query("SELECT * FROM detected_codes ORDER BY timestamp DESC LIMIT 100")
    fun getAllDetectedCodesFlow(): Flow<List<DetectedCodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedCode(code: DetectedCodeEntity)

    @Query("DELETE FROM detected_codes WHERE id = :id")
    suspend fun deleteDetectedCode(id: Long)

    @Query("DELETE FROM detected_codes")
    suspend fun clearAllDetectedCodes()
}

@Dao
interface TextAssistantDao {
    @Query("SELECT * FROM text_assistant_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<TextAssistantConfigEntity?>

    @Query("SELECT * FROM text_assistant_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigDirect(): TextAssistantConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: TextAssistantConfigEntity)

    @Query("SELECT * FROM text_snippets ORDER BY isAiAction ASC, triggerKeyword ASC")
    fun getAllSnippetsFlow(): Flow<List<TextSnippetEntity>>

    @Query("SELECT * FROM text_snippets WHERE isEnabled = 1")
    suspend fun getActiveSnippetsDirect(): List<TextSnippetEntity>

    @Query("SELECT * FROM text_snippets")
    suspend fun getAllSnippetsDirect(): List<TextSnippetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: TextSnippetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<TextSnippetEntity>)

    @Update
    suspend fun updateSnippet(snippet: TextSnippetEntity)

    @Query("DELETE FROM text_snippets WHERE id = :id")
    suspend fun deleteSnippet(id: Long)

    @Query("DELETE FROM text_snippets")
    suspend fun clearAllSnippets()
}


