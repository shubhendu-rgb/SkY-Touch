package com.example.service.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the app process alive while the equalizer is on, shows a small ongoing notification and
 * listens for apps announcing their audio sessions (a dynamic receiver is not affected by
 * background-broadcast limits).
 */
class EqualizerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionReceiver = AudioSessionReceiver()
    private var receiverRegistered = false
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        EqualizerController.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            EqualizerController.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        val settings = EqualizerController.state.value
        val notification = buildNotification(settings.presetName)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }

        // Keep the notification text in sync, and stop when the user turns the equalizer off.
        observeJob?.cancel()
        observeJob = scope.launch {
            EqualizerController.state
                .map { it.enabled to it.presetName }
                .distinctUntilChanged()
                .collect { (enabled, preset) ->
                    if (!enabled) {
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(NOTIF_ID, buildNotification(preset))
                    }
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(sessionReceiver) } catch (_: Throwable) {}
            receiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Equalizer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the system equalizer is active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(preset: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("OPEN_SCREEN", "EQUALIZER")
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, EqualizerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_equalizer)
            .setContentTitle("Equalizer is on")
            .setContentText("Preset: $preset")
            .setContentIntent(openPending)
            .addAction(0, "Turn off", stopPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.equalizer.STOP"
        private const val CHANNEL_ID = "equalizer_channel"
        private const val NOTIF_ID = 7301
    }
}
