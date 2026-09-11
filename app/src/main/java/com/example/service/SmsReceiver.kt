package com.example.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsMessage
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.DetectedCodeEntity
import com.example.data.NotchRepository
import com.example.util.CodeDetector
import com.example.util.OtpNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repository = NotchRepository(db)
                val config = repository.getDirectCodeConfig()

                if (!config.enabled || !config.detectFromSms) {
                    pendingResult.finish()
                    return@launch
                }

                val extras = intent.extras ?: run {
                    pendingResult.finish()
                    return@launch
                }
                val pdus = extras.get("pdus") as? Array<*> ?: run {
                    pendingResult.finish()
                    return@launch
                }
                val format = extras.getString("format")

                var fullMessageBody = ""
                var senderAddress = "Unknown"

                for (pdu in pdus) {
                    val pduBytes = pdu as? ByteArray ?: continue
                    val smsMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pduBytes, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pduBytes)
                    }
                    if (smsMessage != null) {
                        fullMessageBody += smsMessage.messageBody
                        senderAddress = smsMessage.originatingAddress ?: "Unknown"
                    }
                }

                if (fullMessageBody.isBlank()) {
                    pendingResult.finish()
                    return@launch
                }

                val result = CodeDetector.detectCode(fullMessageBody, config)
                if (result != null) {
                    if (CodeDetector.isDuplicate(result.code, "android.provider.Telephony")) {
                        pendingResult.finish()
                        return@launch
                    }

                    val detectedEntity = DetectedCodeEntity(
                        code = result.code,
                        codeType = result.type,
                        sourcePackage = "android.provider.Telephony",
                        snippet = "SMS from $senderAddress: ${result.contextSnippet}",
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertDetectedCode(detectedEntity)

                    if (config.showNotification) {
                        OtpNotificationHelper.postOtpNotification(
                            context = context,
                            code = result.code,
                            sourcePackage = "android.provider.Telephony",
                            snippet = "SMS from $senderAddress: ${result.contextSnippet}"
                        )
                    }

                    if (config.autoCopyToClipboard) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("SMS Verification Code", result.code)
                        clipboard.setPrimaryClip(clip)
                    }

                    if (config.hapticFeedback) {
                        performHapticFeedback(context)
                    }

                    Handler(Looper.getMainLooper()).post {
                        val message = if (config.autoCopyToClipboard) {
                            "Copied SMS OTP: ${result.code}"
                        } else {
                            "Detected SMS OTP: ${result.code}"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun performHapticFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (_: Exception) {}
    }
}
