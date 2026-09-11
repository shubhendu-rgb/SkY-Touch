package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

object BackupHelper {
    private const val TAG = "BackupHelper"

    suspend fun exportToString(repository: NotchRepository): String {
        val root = JSONObject()

        // 1. Notch Config
        val notchConfig = repository.getDirectConfig()
        val notchJson = JSONObject().apply {
            put("widthDp", notchConfig.widthDp)
            put("heightDp", notchConfig.heightDp)
            put("xOffsetDp", notchConfig.xOffsetDp)
            put("yOffsetDp", notchConfig.yOffsetDp)
            put("cornerRadiusDp", notchConfig.cornerRadiusDp)
            put("shapeType", notchConfig.shapeType)
            put("vibrationStrength", notchConfig.vibrationStrength)
            put("showVisualOverlay", notchConfig.showVisualOverlay)
            put("overlayColorHex", notchConfig.overlayColorHex)
            put("overlayOpacity", notchConfig.overlayOpacity.toDouble())
            put("disableInFullScreen", notchConfig.disableInFullScreen)
        }
        root.put("notch_config", notchJson)

        // 2. Gesture Actions
        val actions = repository.getDirectActions()
        val actionsArray = JSONArray()
        for (action in actions) {
            val actionJson = JSONObject().apply {
                put("gestureName", action.gestureName)
                put("actionType", action.actionType)
                put("packageToLaunch", action.packageToLaunch ?: JSONObject.NULL)
                put("label", action.label)
                put("extraValue", action.extraValue ?: JSONObject.NULL)
            }
            actionsArray.put(actionJson)
        }
        root.put("gesture_actions", actionsArray)

        // 3. Side Deck Config
        val sideDeckConfig = repository.getDirectSideDeckConfig()
        val sideDeckJson = JSONObject().apply {
            put("enabled", sideDeckConfig.enabled)
            put("edge", sideDeckConfig.edge)
            put("yOffsetPercent", sideDeckConfig.yOffsetPercent.toDouble())
            put("handleHeightDp", sideDeckConfig.handleHeightDp)
            put("handleWidthDp", sideDeckConfig.handleWidthDp)
            put("handleColorHex", sideDeckConfig.handleColorHex)
            put("handleOpacity", sideDeckConfig.handleOpacity.toDouble())
            put("vibrateOnTouch", sideDeckConfig.vibrateOnTouch)
            put("showSystemTools", sideDeckConfig.showSystemTools)
            put("showShortcutApps", sideDeckConfig.showShortcutApps)
            put("pinnedAppPackages", sideDeckConfig.pinnedAppPackages)
            put("deckRoundnessDp", sideDeckConfig.deckRoundnessDp)
            put("deckEdgeDistanceDp", sideDeckConfig.deckEdgeDistanceDp)
            put("handleRoundnessDp", sideDeckConfig.handleRoundnessDp)
            put("handleEdgeDistanceDp", sideDeckConfig.handleEdgeDistanceDp)
            put("blockBackGesture", sideDeckConfig.blockBackGesture)
            put("backGestureBlockBufferDp", sideDeckConfig.backGestureBlockBufferDp)
        }
        root.put("side_deck_config", sideDeckJson)

        // 4. Code Detection Config
        val codeConfig = repository.getDirectCodeConfig()
        val codeJson = JSONObject().apply {
            put("enabled", codeConfig.enabled)
            put("showNotification", codeConfig.showNotification)
            put("autoCopyToClipboard", codeConfig.autoCopyToClipboard)
            put("hapticFeedback", codeConfig.hapticFeedback)
            put("minDigits", codeConfig.minDigits)
            put("maxDigits", codeConfig.maxDigits)
            put("allowAlphanumeric", codeConfig.allowAlphanumeric)
            put("requireKeywordForNumeric", codeConfig.requireKeywordForNumeric)
            put("keywordFilter", codeConfig.keywordFilter)
            put("customRegex", codeConfig.customRegex)
            put("detectFromNotifications", codeConfig.detectFromNotifications)
            put("detectFromSms", codeConfig.detectFromSms)
            put("detectAllApps", codeConfig.detectAllApps)
            put("allowedPackages", codeConfig.allowedPackages)
        }
        root.put("code_detection_config", codeJson)

        // 5. Text Assistant Config
        val assistantConfig = repository.getDirectTextAssistantConfig()
        val assistantJson = JSONObject().apply {
            put("enabled", assistantConfig.enabled)
            put("apiKey", assistantConfig.apiKey)
            put("modelName", assistantConfig.modelName)
            put("triggerPrefix", assistantConfig.triggerPrefix)
            put("hapticFeedback", assistantConfig.hapticFeedback)
            put("showOverlayPill", assistantConfig.showOverlayPill)
            put("enableNumericTriggers", assistantConfig.enableNumericTriggers)
            put("enableEditingCommands", assistantConfig.enableEditingCommands)
        }
        root.put("text_assistant_config", assistantJson)

        // 6. Text Snippets
        val snippets = repository.getDirectSnippets()
        val snippetsArray = JSONArray()
        for (snippet in snippets) {
            val snippetJson = JSONObject().apply {
                put("triggerKeyword", snippet.triggerKeyword)
                put("isAiAction", snippet.isAiAction)
                put("replacementText", snippet.replacementText)
                put("aiPromptInstruction", snippet.aiPromptInstruction)
                put("isEnabled", snippet.isEnabled)
            }
            snippetsArray.put(snippetJson)
        }
        root.put("text_snippets", snippetsArray)

        return root.toString(4) // Pretty format with 4 space indentation
    }

    suspend fun importFromString(repository: NotchRepository, jsonStr: String): Boolean {
        try {
            val root = JSONObject(jsonStr)

            // 1. Notch Config
            if (root.has("notch_config")) {
                val notchJson = root.getJSONObject("notch_config")
                val notchConfig = NotchConfigEntity(
                    id = 1,
                    widthDp = notchJson.optInt("widthDp", 100),
                    heightDp = notchJson.optInt("heightDp", 36),
                    xOffsetDp = notchJson.optInt("xOffsetDp", 0),
                    yOffsetDp = notchJson.optInt("yOffsetDp", 0),
                    cornerRadiusDp = notchJson.optInt("cornerRadiusDp", 18),
                    shapeType = notchJson.optString("shapeType", "capsule"),
                    vibrationStrength = notchJson.optInt("vibrationStrength", 2),
                    showVisualOverlay = notchJson.optBoolean("showVisualOverlay", true),
                    overlayColorHex = notchJson.optString("overlayColorHex", "#FF000000"),
                    overlayOpacity = notchJson.optDouble("overlayOpacity", 0.8).toFloat(),
                    disableInFullScreen = notchJson.optBoolean("disableInFullScreen", false)
                )
                repository.updateConfig(notchConfig)
            }

            // 2. Gesture Actions
            if (root.has("gesture_actions")) {
                val actionsArray = root.getJSONArray("gesture_actions")
                for (i in 0 until actionsArray.length()) {
                    val actJson = actionsArray.getJSONObject(i)
                    val gestureName = actJson.getString("gestureName")
                    val action = GestureActionEntity(
                        gestureName = gestureName,
                        actionType = actJson.optString("actionType", "NONE"),
                        packageToLaunch = if (actJson.isNull("packageToLaunch")) null else actJson.optString("packageToLaunch"),
                        label = actJson.optString("label", "None"),
                        extraValue = if (actJson.isNull("extraValue")) null else actJson.optString("extraValue")
                    )
                    repository.updateAction(action)
                }
            }

            // 3. Side Deck Config
            if (root.has("side_deck_config")) {
                val sideDeckJson = root.getJSONObject("side_deck_config")
                val sideDeckConfig = SideDeckConfigEntity(
                    id = 1,
                    enabled = sideDeckJson.optBoolean("enabled", false),
                    edge = sideDeckJson.optString("edge", "RIGHT"),
                    yOffsetPercent = sideDeckJson.optDouble("yOffsetPercent", 0.40).toFloat(),
                    handleHeightDp = sideDeckJson.optInt("handleHeightDp", 90),
                    handleWidthDp = sideDeckJson.optInt("handleWidthDp", 18),
                    handleColorHex = sideDeckJson.optString("handleColorHex", "#4F46E5"),
                    handleOpacity = sideDeckJson.optDouble("handleOpacity", 0.85).toFloat(),
                    vibrateOnTouch = sideDeckJson.optBoolean("vibrateOnTouch", true),
                    showSystemTools = sideDeckJson.optBoolean("showSystemTools", true),
                    showShortcutApps = sideDeckJson.optBoolean("showShortcutApps", true),
                    pinnedAppPackages = sideDeckJson.optString("pinnedAppPackages", ""),
                    deckRoundnessDp = sideDeckJson.optInt("deckRoundnessDp", 30),
                    deckEdgeDistanceDp = sideDeckJson.optInt("deckEdgeDistanceDp", 8),
                    handleRoundnessDp = sideDeckJson.optInt("handleRoundnessDp", 24),
                    handleEdgeDistanceDp = sideDeckJson.optInt("handleEdgeDistanceDp", 0),
                    blockBackGesture = sideDeckJson.optBoolean("blockBackGesture", true),
                    backGestureBlockBufferDp = sideDeckJson.optInt("backGestureBlockBufferDp", 10)
                )
                repository.updateSideDeckConfig(sideDeckConfig)
            }

            // 4. Code Detection Config
            if (root.has("code_detection_config")) {
                val codeJson = root.getJSONObject("code_detection_config")
                val codeConfig = CodeDetectionConfigEntity(
                    id = 1,
                    enabled = codeJson.optBoolean("enabled", true),
                    showNotification = codeJson.optBoolean("showNotification", true),
                    autoCopyToClipboard = codeJson.optBoolean("autoCopyToClipboard", true),
                    hapticFeedback = codeJson.optBoolean("hapticFeedback", true),
                    minDigits = codeJson.optInt("minDigits", 4),
                    maxDigits = codeJson.optInt("maxDigits", 8),
                    allowAlphanumeric = codeJson.optBoolean("allowAlphanumeric", true),
                    requireKeywordForNumeric = codeJson.optBoolean("requireKeywordForNumeric", false),
                    keywordFilter = codeJson.optString("keywordFilter", "code, otp, verification, pin, secret, password, auth, login, confirm, security, passcode, 2fa"),
                    customRegex = codeJson.optString("customRegex", ""),
                    detectFromNotifications = codeJson.optBoolean("detectFromNotifications", true),
                    detectFromSms = codeJson.optBoolean("detectFromSms", true),
                    detectAllApps = codeJson.optBoolean("detectAllApps", true),
                    allowedPackages = codeJson.optString("allowedPackages", "")
                )
                repository.updateCodeConfig(codeConfig)
            }

            // 5. Text Assistant Config
            if (root.has("text_assistant_config")) {
                val assistantJson = root.getJSONObject("text_assistant_config")
                val assistantConfig = TextAssistantConfigEntity(
                    id = 1,
                    enabled = assistantJson.optBoolean("enabled", true),
                    apiKey = assistantJson.optString("apiKey", ""),
                    modelName = assistantJson.optString("modelName", "gemini-2.0-flash"),
                    triggerPrefix = assistantJson.optString("triggerPrefix", "?"),
                    hapticFeedback = assistantJson.optBoolean("hapticFeedback", true),
                    showOverlayPill = assistantJson.optBoolean("showOverlayPill", true),
                    enableNumericTriggers = assistantJson.optBoolean("enableNumericTriggers", true),
                    enableEditingCommands = assistantJson.optBoolean("enableEditingCommands", true)
                )
                repository.updateTextAssistantConfig(assistantConfig)
            }

            // 6. Text Snippets
            if (root.has("text_snippets")) {
                val snippetsArray = root.getJSONArray("text_snippets")
                // Clear existing snippets first
                repository.clearAllSnippets()
                for (i in 0 until snippetsArray.length()) {
                    val snJson = snippetsArray.getJSONObject(i)
                    val snippet = TextSnippetEntity(
                        triggerKeyword = snJson.getString("triggerKeyword"),
                        isAiAction = snJson.optBoolean("isAiAction", false),
                        replacementText = snJson.optString("replacementText", ""),
                        aiPromptInstruction = snJson.optString("aiPromptInstruction", ""),
                        isEnabled = snJson.optBoolean("isEnabled", true)
                    )
                    repository.insertSnippet(snippet)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing settings JSON", e)
            return false
        }
    }

    fun readUriToString(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    sb.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from URI", e)
            null
        }
    }

    fun writeStringToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to URI", e)
            false
        }
    }
}
