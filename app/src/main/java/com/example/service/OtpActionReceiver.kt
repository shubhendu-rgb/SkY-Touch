package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

class OtpActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COPY_CODE = "com.example.ACTION_COPY_CODE"
        const val ACTION_DISMISS = "com.example.ACTION_DISMISS_NOTIFICATION"
        const val EXTRA_CODE = "extra_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationId != -1) {
            notificationManager?.cancel(notificationId)
        }

        when (intent.action) {
            ACTION_COPY_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE)
                if (!code.isNullOrBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Verification Code", code)
                    clipboard.setPrimaryClip(clip)

                    // Provide haptic feedback
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                            vibratorManager?.defaultVibrator?.vibrate(
                                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(80)
                            }
                        }
                    } catch (_: Exception) {}

                    Toast.makeText(context, "Copied code $code to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_DISMISS -> {
                // Already canceled notification above
            }
        }
    }
}
