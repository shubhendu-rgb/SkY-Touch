package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.service.OtpActionReceiver
import java.util.concurrent.atomic.AtomicInteger

object OtpNotificationHelper {

    const val CHANNEL_ID = "otp_codes_channel"
    private const val CHANNEL_NAME = "Verification & OTP Alerts"
    private const val CHANNEL_DESCRIPTION = "Instant notifications and quick-copy actions for verification codes and OTPs"

    private val notificationIdCounter = AtomicInteger(1000)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun postOtpNotification(
        context: Context,
        code: String,
        sourcePackage: String = "",
        snippet: String = ""
    ): Int {
        createNotificationChannel(context)

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            return -1
        }

        val notificationId = notificationIdCounter.incrementAndGet()

        // Content Intent: Open MainActivity
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "CODE_DETECTION")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Intent: Copy code to clipboard directly from notification
        val copyIntent = Intent(context, OtpActionReceiver::class.java).apply {
            action = OtpActionReceiver.ACTION_COPY_CODE
            putExtra(OtpActionReceiver.EXTRA_CODE, code)
            putExtra(OtpActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Intent: Dismiss notification
        val dismissIntent = Intent(context, OtpActionReceiver::class.java).apply {
            action = OtpActionReceiver.ACTION_DISMISS
            putExtra(OtpActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_otp).apply {
            setTextViewText(R.id.notification_code_text, code)
            setOnClickPendingIntent(R.id.notification_copy_btn, copyPendingIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setChronometerCountDown(R.id.notification_timer, true)
            }
            setChronometer(R.id.notification_timer, android.os.SystemClock.elapsedRealtime() + (3 * 60 * 1000), "%s", true)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_otp_notification)
            .setContentTitle("Verification Code")
            .setContentText(code)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(3 * 60 * 1000)
            .setContentIntent(contentPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCustomContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        try {
            notificationManagerCompat.notify(notificationId, builder.build())
            return notificationId
        } catch (_: SecurityException) {
            return -1
        }
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        if (packageName.isBlank() || packageName == context.packageName) return ""
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
