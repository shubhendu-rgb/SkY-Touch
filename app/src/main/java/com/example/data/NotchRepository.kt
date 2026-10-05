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
    private val autoClickDao = db.autoClickDao()

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

    val autoClickConfigFlow: Flow<AutoClickConfigEntity> = autoClickDao.getConfigFlow().map {
        it ?: AutoClickConfigEntity()
    }

    val autoClickPointsFlow: Flow<List<AutoClickPointEntity>> = autoClickDao.getPointsFlow()

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

    suspend fun updateAutoClickConfig(config: AutoClickConfigEntity) {
        autoClickDao.insertOrUpdateConfig(config)
    }

    suspend fun insertAutoClickPoint(point: AutoClickPointEntity) {
        autoClickDao.insertPoint(point)
    }

    suspend fun updateAutoClickPoint(point: AutoClickPointEntity) {
        autoClickDao.updatePoint(point)
    }

    suspend fun deleteAutoClickPoint(id: Int) {
        autoClickDao.deletePoint(id)
    }

    suspend fun deleteLastAutoClickPoint() {
        autoClickDao.deleteLastPoint()
    }

    suspend fun clearAutoClickPoints() {
        autoClickDao.clearAllPoints()
    }

    suspend fun getAutoClickPointsDirect(): List<AutoClickPointEntity> {
        return autoClickDao.getPointsDirect()
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

        // Initialize AutoClick config
        val currentAutoClickConfig = autoClickDao.getConfigDirect()
        if (currentAutoClickConfig == null) {
            autoClickDao.insertOrUpdateConfig(AutoClickConfigEntity())
        }

        // Initialize default snippets
        val currentSnippets = textAssistantDao.getAllSnippetsDirect()
        if (currentSnippets.isEmpty()) {
            textAssistantDao.insertSnippets(getDefaultSnippets())
        } else {
            val existingKeywords = currentSnippets.map { it.triggerKeyword.lowercase().trim() }.toSet()
            val missingSnippets = getDefaultSnippets().filter { it.triggerKeyword.lowercase().trim() !in existingKeywords }
            if (missingSnippets.isNotEmpty()) {
                textAssistantDao.insertSnippets(missingSnippets)
            }
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

    fun getDefaultSnippets(): List<TextSnippetEntity> {
        return listOf(
            // AI smart transformations from user screenshots
            TextSnippetEntity(
                triggerKeyword = "improve",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite to improve clarity, flow, and coherence.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "shorten",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite to be more concise while preserving the core meaning.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "expand",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite with more detail. Elaborate only on what is stated or widely known - do not fabricate information.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "formal",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite in a formal, professional tone.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "casual",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite in a casual, friendly tone.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "emoji",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Add relevant emojis throughout.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "human",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Rewrite to sound naturally human, not AI-generated. Never use emdashes or semicolons, use commas or periods instead. Drop AI clichés and filler phrases. Use contractions, everyday words, and varied sentence lengths. Keep all facts, names, and numbers intact.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "reply",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Generate a contextual reply to this message.",
                isEnabled = true
            ),
            TextSnippetEntity(
                triggerKeyword = "fix",
                isAiAction = true,
                replacementText = "",
                aiPromptInstruction = "Fix grammar, spelling, and punctuation errors.",
                isEnabled = true
            ),
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
            )
        )
    }


    private fun getDefaultActions(): List<GestureActionEntity> {
        return listOf(
            GestureActionEntity("SINGLE_TAP", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("DOUBLE_TAP", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("TRIPLE_TAP", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("LONG_PRESS", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("SWIPE_LEFT", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("SWIPE_RIGHT", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("SWIPE_LEFT_AND_HOLD", "NONE", label = "Disabled (No Action)"),
            GestureActionEntity("SWIPE_RIGHT_AND_HOLD", "NONE", label = "Disabled (No Action)")
        )
    }
}
