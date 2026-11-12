package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import com.example.data.GestureActionEntity
import com.example.service.NotchAccessibilityService
import com.example.service.WallpaperRules
import com.example.service.WallpaperWorker
import com.example.util.WebLinkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles clicks from the home screen widget and its individual components,
 * executing the chosen action (same options as notch gesture actions).
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val actionType = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val packageToLaunch = intent.getStringExtra(EXTRA_PACKAGE)
        val extraValue = intent.getStringExtra(EXTRA_EXTRA_VALUE)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""

        // Brief haptic feedback on widget touch
        vibrate(context)

        execute(context, actionType, appWidgetId, packageToLaunch, extraValue, label)
    }

    private fun execute(
        context: Context,
        actionType: String,
        appWidgetId: Int,
        packageToLaunch: String?,
        extraValue: String?,
        label: String
    ) {
        // Special case: Open Widget Editor
        if (actionType == "OPEN_EDITOR") {
            val editorIntent = Intent(context, WidgetEditorActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("skywidget://edit/$appWidgetId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(editorIntent)
            return
        }

        if (actionType == "NONE") return

        val entity = GestureActionEntity(
            gestureName = "WIDGET_TAP",
            actionType = actionType,
            packageToLaunch = packageToLaunch,
            label = label,
            extraValue = extraValue
        )

        // If NotchAccessibilityService is running, it handles all global system actions, apps, shortcuts & gestures!
        val handledByService = NotchAccessibilityService.executeActionExternal(entity)
        if (handledByService) return

        // Standalone fallbacks when Accessibility Service is not active
        when (actionType) {
            "LAUNCH_APP" -> {
                if (!packageToLaunch.isNullOrBlank()) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageToLaunch)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        try {
                            context.startActivity(launchIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open app: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "App not installed: $packageToLaunch", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            "SHORTCUT" -> {
                if (!extraValue.isNullOrBlank()) {
                    try {
                        val shortcutIntent = Intent.parseUri(extraValue, Intent.URI_INTENT_SCHEME).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(shortcutIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot launch shortcut: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            "OPEN_URL" -> {
                if (!WebLinkHelper.open(context, extraValue)) {
                    Toast.makeText(context, "No valid web link set, or no browser found", Toast.LENGTH_SHORT).show()
                }
            }

            "HOME" -> {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(home)
            }

            "BACK" -> showServiceNeeded(context, "Go Back")
            "RECENTS" -> showServiceNeeded(context, "Recents")
            "NOTIFICATIONS" -> showServiceNeeded(context, "Notifications")
            "QUICK_SETTINGS" -> showServiceNeeded(context, "Quick Settings")
            "SCREENSHOT" -> showServiceNeeded(context, "Screenshot")

            "FLASHLIGHT" -> toggleFlashlight(context)

            "MEDIA_PLAY_PAUSE" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "MEDIA_NEXT" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            "MEDIA_PREVIOUS" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

            "VOLUME_UP" -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            "VOLUME_DOWN" -> adjustVolume(context, AudioManager.ADJUST_LOWER)

            "BRIGHTNESS_UP" -> adjustBrightness(context, 8)
            "BRIGHTNESS_DOWN" -> adjustBrightness(context, -8)
            "CUSTOM_BRIGHTNESS" -> setCustomBrightness(context, extraValue?.toIntOrNull() ?: 50)

            "WALLPAPER_NEXT" -> cycleWallpaper(context, "NEXT")
            "WALLPAPER_PREVIOUS" -> cycleWallpaper(context, "PREV")
            "WALLPAPER_RANDOM" -> cycleWallpaper(context, "RANDOM")
            "WALLPAPER_HOME_NEXT" -> cycleWallpaper(context, "NEXT", screenType = "HOME")
            "WALLPAPER_LOCK_NEXT" -> cycleWallpaper(context, "NEXT", screenType = "LOCK")
            "WALLPAPER_IMAGES_NEXT" -> cycleWallpaper(context, "NEXT", sourceFilter = "IMAGES_ONLY")
            "WALLPAPER_FOLDER_NEXT" -> cycleWallpaper(context, "NEXT", sourceFilter = "FOLDER_ONLY")

            // Never fail silently: tell the user why nothing happened
            else -> showServiceNeeded(context, label.ifBlank { actionType })
        }
    }

    private fun showServiceNeeded(context: Context, name: String) {
        Toast.makeText(context, "$name requires Accessibility Service (enable Notch Gesture in SkY Touch)", Toast.LENGTH_SHORT).show()
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            }
        } catch (_: Exception) {}
    }

    private fun toggleFlashlight(context: Context) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull() ?: return
            isFlashlightOn = !isFlashlightOn
            cm.setTorchMode(cameraId, isFlashlightOn)
        } catch (e: Exception) {
            Toast.makeText(context, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMediaKey(context: Context, keyCode: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
    }

    private fun adjustVolume(context: Context, direction: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {}
    }

    private fun adjustBrightness(context: Context, deltaPercent: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                val cr = context.contentResolver
                val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
                val step = (255 * (deltaPercent / 100f)).toInt()
                val target = (cur + step).coerceIn(10, 255)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target)
            } catch (_: Exception) {}
        } else {
            Toast.makeText(context, "Modify System Settings permission required for brightness", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setCustomBrightness(context: Context, percent: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                val cr = context.contentResolver
                val target = ((percent.coerceIn(5, 100) / 100f) * 255).toInt().coerceIn(10, 255)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target)
            } catch (_: Exception) {}
        }
    }

    private fun cycleWallpaper(
        context: Context,
        direction: String,
        screenType: String? = null,
        sourceFilter: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rule = WallpaperRules.load(context, WallpaperRules.TRIGGER_GESTURE)
                val target = screenType ?: rule.target
                WallpaperWorker.applyRule(
                    context = context,
                    trigger = WallpaperRules.TRIGGER_GESTURE,
                    direction = direction,
                    targetOverride = target,
                    sourceOverride = sourceFilter
                )
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.skytouch.sk.action.WIDGET_CLICK"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_EXTRA_VALUE = "extra_extra_value"
        const val EXTRA_LABEL = "extra_label"

        private var isFlashlightOn = false

        fun createPendingIntent(
            context: Context,
            appWidgetId: Int,
            componentId: String,
            action: WidgetClickAction
        ): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = ACTION_WIDGET_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_ACTION_TYPE, action.actionType)
                putExtra(EXTRA_PACKAGE, action.packageToLaunch)
                putExtra(EXTRA_EXTRA_VALUE, action.extraValue)
                putExtra(EXTRA_LABEL, action.label)
                data = Uri.parse("skywidget://click/$appWidgetId/$componentId/${action.actionType}")
            }
            val reqCode = (appWidgetId * 1000 + componentId.hashCode()).and(0x7FFFFFFF)
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
