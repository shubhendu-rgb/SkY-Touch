package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotchRepository(private val db: AppDatabase) {
    private val notchConfigDao = db.notchConfigDao()
    private val gestureActionDao = db.gestureActionDao()
    private val triggerStatDao = db.triggerStatDao()
    private val sideDeckConfigDao = db.sideDeckConfigDao()
    private val codeDetectionDao = db.codeDetectionDao()
    private val textAssistantDao = db.textAssistantDao()

    val configFlow: Flow<NotchConfigEntity> = notchConfigDao.getConfigFlow().map {
        it ?: NotchConfigEntity()
    }

    val sideDeckConfigFlow: Flow<SideDeckConfigEntity> = sideDeckConfigDao.getConfigFlow().map {
        it ?: SideDeckConfigEntity()
    }

    val codeConfigFlow: Flow<CodeDetectionConfigEntity> = codeDetectionDao.getConfigFlow().map {
        it ?: CodeDetectionConfigEntity()
    }

    val textAssistantConfigFlow: Flow<TextAssistantConfigEntity> = textAssistantDao.getConfigFlow().map {
        it ?: TextAssistantConfigEntity()
    }

    val snippetsFlow: Flow<List<TextSnippetEntity>> = textAssistantDao.getAllSnippetsFlow()

    val detectedCodesFlow: Flow<List<DetectedCodeEntity>> = codeDetectionDao.getAllDetectedCodesFlow()

    val actionsFlow: Flow<List<GestureActionEntity>> = gestureActionDao.getAllActionsFlow()

    val statsFlow: Flow<List<TriggerStatEntity>> = triggerStatDao.getAllStatsFlow()

    suspend fun getDirectConfig(): NotchConfigEntity {
        return notchConfigDao.getConfigDirect() ?: NotchConfigEntity()
    }

    suspend fun getDirectSideDeckConfig(): SideDeckConfigEntity {
        return sideDeckConfigDao.getConfigDirect() ?: SideDeckConfigEntity()
    }

    suspend fun getDirectCodeConfig(): CodeDetectionConfigEntity {
        return codeDetectionDao.getConfigDirect() ?: CodeDetectionConfigEntity()
    }

    suspend fun getDirectTextAssistantConfig(): TextAssistantConfigEntity {
        return textAssistantDao.getConfigDirect() ?: TextAssistantConfigEntity()
    }

    suspend fun getDirectSnippets(): List<TextSnippetEntity> {
        val snippets = textAssistantDao.getAllSnippetsDirect()
        return snippets.ifEmpty { getDefaultSnippets() }
    }

    suspend fun getDirectActiveSnippets(): List<TextSnippetEntity> {
        return textAssistantDao.getActiveSnippetsDirect()
    }

    suspend fun getDirectActions(): List<GestureActionEntity> {
        val actions = gestureActionDao.getAllActionsDirect()
        return actions.ifEmpty { getDefaultActions() }
    }

    suspend fun updateConfig(config: NotchConfigEntity) {
        notchConfigDao.insertOrUpdateConfig(config)
    }

    suspend fun updateSideDeckConfig(config: SideDeckConfigEntity) {
        sideDeckConfigDao.insertOrUpdateConfig(config)
    }

    suspend fun updateCodeConfig(config: CodeDetectionConfigEntity) {
        codeDetectionDao.insertOrUpdateConfig(config)
    }

    suspend fun updateTextAssistantConfig(config: TextAssistantConfigEntity) {
        textAssistantDao.insertOrUpdateConfig(config)
    }

    suspend fun insertSnippet(snippet: TextSnippetEntity): Long {
        return textAssistantDao.insertSnippet(snippet)
    }

    suspend fun updateSnippet(snippet: TextSnippetEntity) {
        textAssistantDao.updateSnippet(snippet)
    }

    suspend fun deleteSnippet(id: Long) {
        textAssistantDao.deleteSnippet(id)
    }

    suspend fun clearAllSnippets() {
        textAssistantDao.clearAllSnippets()
    }

    suspend fun resetSnippetsToDefault() {
        textAssistantDao.clearAllSnippets()
        textAssistantDao.insertSnippets(getDefaultSnippets())
    }

    suspend fun insertDetectedCode(code: DetectedCodeEntity) {
        codeDetectionDao.insertDetectedCode(code)
    }

    suspend fun deleteDetectedCode(id: Long) {
        codeDetectionDao.deleteDetectedCode(id)
    }

    suspend fun clearDetectedCodes() {
        codeDetectionDao.clearAllDetectedCodes()
    }

    suspend fun updateAction(action: GestureActionEntity) {
        gestureActionDao.insertAction(action)
    }

    suspend fun incrementStat(gestureName: String) {
        triggerStatDao.incrementStat(gestureName)
    }

    suspend fun resetStats() {
        triggerStatDao.resetStats()
    }

    suspend fun initializeDefaultsIfNeeded() {
        // Initialize config
        val currentConfig = notchConfigDao.getConfigDirect()
        if (currentConfig == null) {
            notchConfigDao.insertOrUpdateConfig(NotchConfigEntity())
        }

        // Initialize Side Deck config
        val currentSideDeckConfig = sideDeckConfigDao.getConfigDirect()
        if (currentSideDeckConfig == null) {
            sideDeckConfigDao.insertOrUpdateConfig(SideDeckConfigEntity())
        }

        // Initialize Code Detection config
        val currentCodeConfig = codeDetectionDao.getConfigDirect()
        if (currentCodeConfig == null) {
            codeDetectionDao.insertOrUpdateConfig(CodeDetectionConfigEntity())
        }

        // Initialize Text Assistant config
        val currentTextAssistantConfig = textAssistantDao.getConfigDirect()
        if (currentTextAssistantConfig == null) {
            textAssistantDao.insertOrUpdateConfig(TextAssistantConfigEntity())
        }

        // Initialize default snippets
        val currentSnippets = textAssistantDao.getAllSnippetsDirect()
        if (currentSnippets.isEmpty()) {
            textAssistantDao.insertSnippets(getDefaultSnippets())
        }

        // Clean up removed gestures
        gestureActionDao.deleteAction("SWIPE_DOWN")
        triggerStatDao.deleteStat("SWIPE_DOWN")

        // Initialize actions
        val currentActions = gestureActionDao.getAllActionsDirect()
        if (currentActions.isEmpty()) {
            gestureActionDao.insertActions(getDefaultActions())
        } else {
            // Ensure any new default gestures are added for existing installations
            val existingNames = currentActions.map { it.gestureName }.toSet()
            val missingActions = getDefaultActions().filter { it.gestureName !in existingNames }
            if (missingActions.isNotEmpty()) {
                gestureActionDao.insertActions(missingActions)
            }
        }
    }

    private fun getDefaultSnippets(): List<TextSnippetEntity> {
        return listOf(
            // Local quick text expansions
            TextSnippetEntity(
                triggerKeyword = "addr",
                isAiAction = false,
                replacementText = "123 Innovation Way, Suite 400, Tech City, CA 94016",
                aiPromptInstruction = "",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "email",
                isAiAction = false,
                replacementText = "contact@example.com",
                aiPromptInstruction = "",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "meet",
                isAiAction = false,
                replacementText = "Let's schedule a quick sync. Feel free to send over a time that works best for you.",
                aiPromptInstruction = "",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "shrug",
                isAiAction = false,
                replacementText = "¯\\_(ツ)_/¯",
                aiPromptInstruction = "",
                isEnabled = true
            ),
            // AI smart transformations
            TextSnippetEntity(
                triggerKeyword = "fix",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Fix all spelling, grammar, punctuation, and capitalization errors while preserving original tone. Output ONLY the polished text without explanations or quotes.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "formal",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite this message into a polite, respectful, and professional business tone. Output ONLY the rewritten text without quotes or preamble.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "shorten",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Condense and summarize this text to make it clear, concise, and punchy while retaining all key points. Output ONLY the shortened text.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "reply",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Generate a thoughtful, friendly, and helpful direct reply to this message. Output ONLY the response text.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "translate",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Translate this text into fluent, natural English (or if already in English, translate to Spanish). Output ONLY the translation.",
                isEnabled = true
            )
        )
    }


    private fun getDefaultActions(): List<GestureActionEntity> {
        return listOf(
            GestureActionEntity("SINGLE_TAP", "SCREENSHOT", label = "Take Screenshot"),
            GestureActionEntity("DOUBLE_TAP", "FLASHLIGHT", label = "Toggle Flashlight"),
            GestureActionEntity("TRIPLE_TAP", "MEDIA_PLAY_PAUSE", label = "Media Play / Pause"),
            GestureActionEntity("LONG_PRESS", "QUICK_SETTINGS", label = "Open Quick Settings"),
            GestureActionEntity("SWIPE_LEFT", "BACK", label = "Go Back"),
            GestureActionEntity("SWIPE_RIGHT", "HOME", label = "Go Home"),
            GestureActionEntity("SWIPE_LEFT_AND_HOLD", "RECENTS", label = "Open Recents Overview"),
            GestureActionEntity("SWIPE_RIGHT_AND_HOLD", "NOTIFICATIONS", label = "Open Notifications Shade")
        )
    }
}
