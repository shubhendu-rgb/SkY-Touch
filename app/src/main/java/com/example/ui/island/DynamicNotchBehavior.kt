package com.example.ui.island

import android.content.Context
import androidx.compose.animation.core.Spring

enum class IdleMode(val label: String) {
    HIDDEN("Hidden"),
    EMPTY_PILL("Empty pill"),
    CLOCK("Clock"),
    BATTERY("Battery")
}

enum class AnimationSpeed(
    val label: String,
    val stiffness: Float,
    val dampingRatio: Float
) {
    CALM("Calm", Spring.StiffnessLow, Spring.DampingRatioNoBouncy),
    NORMAL("Normal", Spring.StiffnessMediumLow, Spring.DampingRatioLowBouncy),
    SNAPPY("Snappy", Spring.StiffnessMedium, Spring.DampingRatioMediumBouncy)
}

data class DynamicNotchBehavior(
    // When nothing is happening
    val idleMode: IdleMode = IdleMode.EMPTY_PILL,

    // Touch & Interaction
    val isUntouchable: Boolean = false,

    // Timing
    val collapseAfterSeconds: Int = 5,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,

    // Visibility
    val showOnLockScreen: Boolean = true,
    val showInLandscape: Boolean = false,
    val startAfterReboot: Boolean = true,

    // Notification Filter
    val includeOngoingNotifications: Boolean = false,
    val includeSilentNotifications: Boolean = false,

    // Actions and Live Activities
    val liveActivities: Boolean = true,
    val showNotificationButtons: Boolean = true,
    val quickReply: Boolean = true,

    // Mini Capsule Events - Battery
    val batteryChargingStarted: Boolean = true,
    val batteryChargingStopped: Boolean = true,
    val batteryLowWarning: Boolean = true,

    // Mini Capsule Events - Wi-Fi
    val wifiConnected: Boolean = true,
    val wifiDisconnected: Boolean = true,

    // Mini Capsule Events - Headphones
    val headphonesConnected: Boolean = true,
    val headphonesDisconnected: Boolean = true,

    // Mini Capsule Events - Airplane Mode
    val airplaneModeOn: Boolean = true,
    val airplaneModeOff: Boolean = true,

    // Mini Capsule Events - Volume
    val volumeModeChanged: Boolean = true,

    // Text Adjustment for Dynamic Notch texts
    val textScale: Float = 1.0f,
    val textBold: Boolean = false,
    val textColorOverrideHex: String = ""
)

object BehaviorPreferences {
    private const val PREFS_NAME = "dynamic_notch_behavior_prefs"

    private const val KEY_IDLE_MODE = "idle_mode"
    private const val KEY_IS_UNTOUCHABLE = "is_untouchable"
    private const val KEY_COLLAPSE_AFTER = "collapse_after_seconds"
    private const val KEY_ANIMATION_SPEED = "animation_speed"
    private const val KEY_SHOW_LOCK_SCREEN = "show_on_lock_screen"
    private const val KEY_SHOW_LANDSCAPE = "show_in_landscape"
    private const val KEY_START_REBOOT = "start_after_reboot"
    private const val KEY_INCLUDE_ONGOING = "include_ongoing"
    private const val KEY_INCLUDE_SILENT = "include_silent"
    private const val KEY_LIVE_ACTIVITIES = "live_activities"
    private const val KEY_SHOW_NOTIF_BUTTONS = "show_notif_buttons"
    private const val KEY_QUICK_REPLY = "quick_reply"

    private const val KEY_BATT_CHARGING_STARTED = "batt_charging_started"
    private const val KEY_BATT_CHARGING_STOPPED = "batt_charging_stopped"
    private const val KEY_BATT_LOW_WARNING = "batt_low_warning"
    private const val KEY_WIFI_CONNECTED = "wifi_connected"
    private const val KEY_WIFI_DISCONNECTED = "wifi_disconnected"
    private const val KEY_HEADPHONES_CONNECTED = "headphones_connected"
    private const val KEY_HEADPHONES_DISCONNECTED = "headphones_disconnected"
    private const val KEY_AIRPLANE_ON = "airplane_on"
    private const val KEY_AIRPLANE_OFF = "airplane_off"
    private const val KEY_VOLUME_CHANGED = "volume_changed"

    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_TEXT_BOLD = "text_bold"
    private const val KEY_TEXT_COLOR_OVERRIDE = "text_color_override"

    fun load(context: Context): DynamicNotchBehavior {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idleModeStr = prefs.getString(KEY_IDLE_MODE, IdleMode.EMPTY_PILL.name) ?: IdleMode.EMPTY_PILL.name
        val animSpeedStr = prefs.getString(KEY_ANIMATION_SPEED, AnimationSpeed.NORMAL.name) ?: AnimationSpeed.NORMAL.name

        return DynamicNotchBehavior(
            idleMode = try { IdleMode.valueOf(idleModeStr) } catch (_: Exception) { IdleMode.EMPTY_PILL },
            isUntouchable = prefs.getBoolean(KEY_IS_UNTOUCHABLE, false),
            collapseAfterSeconds = prefs.getInt(KEY_COLLAPSE_AFTER, 5),
            animationSpeed = try { AnimationSpeed.valueOf(animSpeedStr) } catch (_: Exception) { AnimationSpeed.NORMAL },
            showOnLockScreen = prefs.getBoolean(KEY_SHOW_LOCK_SCREEN, true),
            showInLandscape = prefs.getBoolean(KEY_SHOW_LANDSCAPE, false),
            startAfterReboot = prefs.getBoolean(KEY_START_REBOOT, true),
            includeOngoingNotifications = prefs.getBoolean(KEY_INCLUDE_ONGOING, false),
            includeSilentNotifications = prefs.getBoolean(KEY_INCLUDE_SILENT, false),
            liveActivities = prefs.getBoolean(KEY_LIVE_ACTIVITIES, true),
            showNotificationButtons = prefs.getBoolean(KEY_SHOW_NOTIF_BUTTONS, true),
            quickReply = prefs.getBoolean(KEY_QUICK_REPLY, true),
            batteryChargingStarted = prefs.getBoolean(KEY_BATT_CHARGING_STARTED, true),
            batteryChargingStopped = prefs.getBoolean(KEY_BATT_CHARGING_STOPPED, true),
            batteryLowWarning = prefs.getBoolean(KEY_BATT_LOW_WARNING, true),
            wifiConnected = prefs.getBoolean(KEY_WIFI_CONNECTED, true),
            wifiDisconnected = prefs.getBoolean(KEY_WIFI_DISCONNECTED, true),
            headphonesConnected = prefs.getBoolean(KEY_HEADPHONES_CONNECTED, true),
            headphonesDisconnected = prefs.getBoolean(KEY_HEADPHONES_DISCONNECTED, true),
            airplaneModeOn = prefs.getBoolean(KEY_AIRPLANE_ON, true),
            airplaneModeOff = prefs.getBoolean(KEY_AIRPLANE_OFF, true),
            volumeModeChanged = prefs.getBoolean(KEY_VOLUME_CHANGED, true),
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f),
            textBold = prefs.getBoolean(KEY_TEXT_BOLD, false),
            textColorOverrideHex = prefs.getString(KEY_TEXT_COLOR_OVERRIDE, "") ?: ""
        )
    }

    fun save(context: Context, behavior: DynamicNotchBehavior) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_IDLE_MODE, behavior.idleMode.name)
            .putBoolean(KEY_IS_UNTOUCHABLE, behavior.isUntouchable)
            .putInt(KEY_COLLAPSE_AFTER, behavior.collapseAfterSeconds)
            .putString(KEY_ANIMATION_SPEED, behavior.animationSpeed.name)
            .putBoolean(KEY_SHOW_LOCK_SCREEN, behavior.showOnLockScreen)
            .putBoolean(KEY_SHOW_LANDSCAPE, behavior.showInLandscape)
            .putBoolean(KEY_START_REBOOT, behavior.startAfterReboot)
            .putBoolean(KEY_INCLUDE_ONGOING, behavior.includeOngoingNotifications)
            .putBoolean(KEY_INCLUDE_SILENT, behavior.includeSilentNotifications)
            .putBoolean(KEY_LIVE_ACTIVITIES, behavior.liveActivities)
            .putBoolean(KEY_SHOW_NOTIF_BUTTONS, behavior.showNotificationButtons)
            .putBoolean(KEY_QUICK_REPLY, behavior.quickReply)
            .putBoolean(KEY_BATT_CHARGING_STARTED, behavior.batteryChargingStarted)
            .putBoolean(KEY_BATT_CHARGING_STOPPED, behavior.batteryChargingStopped)
            .putBoolean(KEY_BATT_LOW_WARNING, behavior.batteryLowWarning)
            .putBoolean(KEY_WIFI_CONNECTED, behavior.wifiConnected)
            .putBoolean(KEY_WIFI_DISCONNECTED, behavior.wifiDisconnected)
            .putBoolean(KEY_HEADPHONES_CONNECTED, behavior.headphonesConnected)
            .putBoolean(KEY_HEADPHONES_DISCONNECTED, behavior.headphonesDisconnected)
            .putBoolean(KEY_AIRPLANE_ON, behavior.airplaneModeOn)
            .putBoolean(KEY_AIRPLANE_OFF, behavior.airplaneModeOff)
            .putBoolean(KEY_VOLUME_CHANGED, behavior.volumeModeChanged)
            .putFloat(KEY_TEXT_SCALE, behavior.textScale)
            .putBoolean(KEY_TEXT_BOLD, behavior.textBold)
            .putString(KEY_TEXT_COLOR_OVERRIDE, behavior.textColorOverrideHex)
            .apply()
    }
}
