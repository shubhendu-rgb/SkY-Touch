package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notch_config")
data class NotchConfigEntity(
    @PrimaryKey val id: Int = 1,
    val widthDp: Int = 100,
    val heightDp: Int = 36,
    val xOffsetDp: Int = 0,
    val yOffsetDp: Int = 0,
    val cornerRadiusDp: Int = 18,
    val shapeType: String = "capsule", // "capsule", "circle", "rect"
    val vibrationStrength: Int = 2, // 0 = None, 1 = Light, 2 = Medium, 3 = Heavy
    val showVisualOverlay: Boolean = true,
    val overlayColorHex: String = "#FF000000", // Hex color
    val overlayOpacity: Float = 0.8f,
    val disableInFullScreen: Boolean = false
)

@Entity(tableName = "gesture_actions")
data class GestureActionEntity(
    @PrimaryKey val gestureName: String, // "SINGLE_TAP", "DOUBLE_TAP", "TRIPLE_TAP", "LONG_PRESS", "SWIPE_LEFT", "SWIPE_RIGHT", "SWIPE_LEFT_AND_HOLD", "SWIPE_RIGHT_AND_HOLD"
    val actionType: String = "NONE", // "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "SCREENSHOT", "FLASHLIGHT", "MEDIA_PLAY_PAUSE", "VOLUME_UP", "VOLUME_DOWN", "BRIGHTNESS_UP", "BRIGHTNESS_DOWN", "CUSTOM_BRIGHTNESS", "LAUNCH_APP", "SHORTCUT", "NONE"
    val packageToLaunch: String? = null,
    val label: String = "None",
    val extraValue: String? = null // percentage e.g. "75" for brightness, or intent URI for shortcuts
)

@Entity(tableName = "trigger_stats")
data class TriggerStatEntity(
    @PrimaryKey val gestureName: String,
    val count: Int = 0
)

@Entity(tableName = "side_deck_config")
data class SideDeckConfigEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val edge: String = "RIGHT", // "LEFT" or "RIGHT"
    val yOffsetPercent: Float = 0.40f, // 0.10f .. 0.90f
    val handleHeightDp: Int = 90, // 50 .. 180
    val handleWidthDp: Int = 18, // 10 .. 30
    val handleColorHex: String = "#4F46E5", // Indigo theme
    val handleOpacity: Float = 0.85f,
    val vibrateOnTouch: Boolean = true,
    val showSystemTools: Boolean = true,
    val showShortcutApps: Boolean = true,
    val pinnedAppPackages: String = "", // comma-separated package names
    val deckRoundnessDp: Int = 30, // 0 .. 36 dp (deck and pills roundness)
    val deckEdgeDistanceDp: Int = 8, // 0 .. 48 dp (distance from screen edge)
    val handleRoundnessDp: Int = 24, // 0 .. 36 dp (trigger handle roundness)
    val handleEdgeDistanceDp: Int = 0, // 0 .. 48 dp (distance of trigger handle from edge)
    val blockBackGesture: Boolean = true, // Block system back swipe near handle
    val backGestureBlockBufferDp: Int = 10 // Extra buffer in dp (default 10dp) around handle to block system back gesture
)

@Entity(tableName = "code_detection_config")
data class CodeDetectionConfigEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    val showNotification: Boolean = true,
    val autoCopyToClipboard: Boolean = true,
    val hapticFeedback: Boolean = true,
    val minDigits: Int = 4,
    val maxDigits: Int = 8,
    val allowAlphanumeric: Boolean = true,
    val requireKeywordForNumeric: Boolean = false,
    val keywordFilter: String = "code, otp, verification, pin, secret, password, auth, login, confirm, security, passcode, 2fa",
    val customRegex: String = "",
    val detectFromNotifications: Boolean = true,
    val detectFromSms: Boolean = true,
    val detectAllApps: Boolean = true,
    val allowedPackages: String = "",
    val prototypeMessage: String = ""
)

@Entity(tableName = "detected_codes")
data class DetectedCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val codeType: String, // "NUMERIC_OTP", "ALPHANUMERIC_2FA", "SERVICE_PREFIX", "HYPHENATED_2FA", "CUSTOM_REGEX"
    val sourcePackage: String,
    val snippet: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "text_assistant_config")
data class TextAssistantConfigEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    val apiKey: String = "", // Manual user override; falls back to BuildConfig.GEMINI_API_KEY
    val modelName: String = "gemini-2.0-flash", // Selected Gemini model
    val triggerPrefix: String = "?", // e.g. '?', '!', '/', '#'
    val hapticFeedback: Boolean = true,
    val showOverlayPill: Boolean = true,
    val enableNumericTriggers: Boolean = true,
    val enableEditingCommands: Boolean = true,
    val isDefaultMultiplierLineByLine: Boolean = false
)

@Entity(tableName = "text_snippets")
data class TextSnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerKeyword: String, // e.g. "addr", "email", "fix", "formal", "shorten", "reply"
    val isAiAction: Boolean, // false = local instant replacement; true = LLM generation
    val replacementText: String = "", // Used if isAiAction == false
    val aiPromptInstruction: String = "", // System instruction if isAiAction == true
    val isEnabled: Boolean = true
)

