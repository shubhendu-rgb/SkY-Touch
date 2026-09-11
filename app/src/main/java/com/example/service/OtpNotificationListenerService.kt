package com.example.service

import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.DetectedCodeEntity
import com.example.data.NotchRepository
import com.example.util.CodeDetector
import com.example.util.OtpNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OtpNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: NotchRepository

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = NotchRepository(db)
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Skip notifications from own app
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        // Combine text parts to form full readable notification content
        val combinedText = buildList {
            if (title.isNotBlank()) add(title)
            if (text.isNotBlank()) add(text)
            if (bigText.isNotBlank() && bigText != text) add(bigText)
            if (subText.isNotBlank()) add(subText)
        }.joinToString(" ")

        if (combinedText.isBlank()) return

        serviceScope.launch {
            try {
                val config = repository.getDirectCodeConfig()
                if (!config.enabled) return@launch
                if (!config.detectFromNotifications) return@launch

                if (!config.detectAllApps) {
                    val allowed = config.allowedPackages.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (!allowed.contains(sbn.packageName)) {
                        return@launch
                    }
                }

                val result = CodeDetector.detectCode(combinedText, config) ?: return@launch

                if (CodeDetector.isDuplicate(result.code, sbn.packageName)) return@launch

                // Save detected code into Room database
                val detectedEntity = DetectedCodeEntity(
                    code = result.code,
                    codeType = result.type,
                    sourcePackage = sbn.packageName,
                    snippet = result.contextSnippet,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertDetectedCode(detectedEntity)

                // Post rich heads-up notification with quick copy action
                if (config.showNotification) {
                    OtpNotificationHelper.postOtpNotification(
                        context = applicationContext,
                        code = result.code,
                        sourcePackage = sbn.packageName,
                        snippet = result.contextSnippet
                    )
                }

                // Auto copy to clipboard if enabled
                if (config.autoCopyToClipboard) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Verification Code", result.code)
                    clipboard.setPrimaryClip(clip)
                }

                // Show toast notification
                Handler(Looper.getMainLooper()).post {
                    val message = if (config.autoCopyToClipboard) {
                        "Copied OTP: ${result.code}"
                    } else {
                        "Detected OTP: ${result.code}"
                    }
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                // Ignore background extraction failures gracefully
            }
        }
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration failure
        }
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
