package com.example.ui.island

import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.service.NotchAccessibilityService
import java.util.UUID

enum class CapsuleType {
    BATTERY_CHARGING_STARTED,
    BATTERY_CHARGING_STOPPED,
    BATTERY_LOW,
    WIFI_CONNECTED,
    WIFI_DISCONNECTED,
    HEADPHONES_CONNECTED,
    HEADPHONES_DISCONNECTED,
    AIRPLANE_ON,
    AIRPLANE_OFF,
    VOLUME_CHANGED,
    NOTIFICATION,
    LIVE_ACTIVITY
}

data class NotificationActionItem(
    val title: String,
    val actionIntent: PendingIntent? = null
)

data class MiniCapsuleEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: CapsuleType,
    val title: String,
    val subtitle: String? = null,
    val iconVector: ImageVector? = null,
    val accentColor: Color = Color(0xFF00E676),
    val durationSeconds: Int = 4,
    val packageName: String? = null,
    val actionButtons: List<NotificationActionItem> = emptyList(),
    val quickReplyIntent: PendingIntent? = null,
    val quickReplyKey: String? = null
)

object DynamicNotchManager {

    private const val PREFS_NAME = "dynamic_notch_prefs"
    private const val KEY_ENABLED = "dynamic_notch_enabled"

    val isDynamicNotchEnabled = mutableStateOf(true)
    val activeState = mutableStateOf(IslandState.COLLAPSED)
    val activeSkin = mutableStateOf<DynamicIslandSkin>(PredefinedSkins.CLASSIC)
    val activeBehavior = mutableStateOf(DynamicNotchBehavior())

    val currentCapsuleEvent = mutableStateOf<MiniCapsuleEvent?>(null)
    val liveActivityEvent = mutableStateOf<MiniCapsuleEvent?>(null)

    private val handler = Handler(Looper.getMainLooper())
    private var dismissCapsuleRunnable: Runnable? = null
    private var testEventIndex = 0

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDynamicNotchEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
        activeSkin.value = PredefinedSkins.getSavedSkin(context)
        activeBehavior.value = BehaviorPreferences.load(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        isDynamicNotchEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        NotchAccessibilityService.instance?.notifyDynamicNotchChanged()
    }

    fun updateSkin(context: Context, skin: DynamicIslandSkin) {
        activeSkin.value = skin
        PredefinedSkins.saveSkin(context, skin)
        NotchAccessibilityService.instance?.notifyDynamicNotchChanged()
    }

    fun updateBehavior(context: Context, behavior: DynamicNotchBehavior) {
        activeBehavior.value = behavior
        BehaviorPreferences.save(context, behavior)
        NotchAccessibilityService.instance?.notifyDynamicNotchChanged()
    }

    fun postCapsuleEvent(event: MiniCapsuleEvent) {
        if (!isDynamicNotchEnabled.value) return
        handler.post {
            currentCapsuleEvent.value = event

            dismissCapsuleRunnable?.let { handler.removeCallbacks(it) }
            val timeoutMs = (event.durationSeconds.coerceAtLeast(2)) * 1000L
            val eventId = event.id
            dismissCapsuleRunnable = Runnable {
                if (currentCapsuleEvent.value?.id == eventId) {
                    currentCapsuleEvent.value = null
                }
            }
            handler.postDelayed(dismissCapsuleRunnable!!, timeoutMs)
        }
    }

    fun dismissCapsule() {
        handler.post {
            dismissCapsuleRunnable?.let { handler.removeCallbacks(it) }
            currentCapsuleEvent.value = null
        }
    }

    fun setLiveActivity(event: MiniCapsuleEvent?) {
        if (!isDynamicNotchEnabled.value && event != null) return
        handler.post {
            liveActivityEvent.value = event
        }
    }

    fun clearLiveActivity(packageName: String? = null) {
        handler.post {
            val current = liveActivityEvent.value
            if (packageName == null || current?.packageName == packageName) {
                liveActivityEvent.value = null
            }
        }
    }

    fun sendTestEvent(context: Context) {
        testEventIndex = (testEventIndex + 1) % 4
        val duration = activeBehavior.value.collapseAfterSeconds.coerceAtLeast(3)
        val testEvent = when (testEventIndex) {
            0 -> MiniCapsuleEvent(
                type = CapsuleType.BATTERY_CHARGING_STARTED,
                title = "Charging",
                subtitle = "88% • Fast Charge",
                iconVector = Icons.Default.BatteryChargingFull,
                accentColor = Color(0xFF00E676),
                durationSeconds = duration
            )
            1 -> MiniCapsuleEvent(
                type = CapsuleType.WIFI_CONNECTED,
                title = "Wi-Fi Connected",
                subtitle = "Online • 5 GHz",
                iconVector = Icons.Default.Wifi,
                accentColor = Color(0xFF38BDF8),
                durationSeconds = duration
            )
            2 -> MiniCapsuleEvent(
                type = CapsuleType.NOTIFICATION,
                title = "Messages",
                subtitle = "Alex: See you soon at 6!",
                iconVector = Icons.Default.Notifications,
                accentColor = Color(0xFFEC4899),
                durationSeconds = duration,
                actionButtons = if (activeBehavior.value.showNotificationButtons) listOf(
                    NotificationActionItem(title = "Reply", actionIntent = null),
                    NotificationActionItem(title = "Mark read", actionIntent = null)
                ) else emptyList()
            )
            else -> MiniCapsuleEvent(
                type = CapsuleType.HEADPHONES_CONNECTED,
                title = "Headphones",
                subtitle = "Wireless Audio Connected",
                iconVector = Icons.Default.Headphones,
                accentColor = Color(0xFFA855F7),
                durationSeconds = duration
            )
        }
        postCapsuleEvent(testEvent)
    }

    fun toggleOverlayState() {
        activeState.value = IslandState.COLLAPSED
        NotchAccessibilityService.instance?.notifyDynamicNotchChanged()
    }
}
