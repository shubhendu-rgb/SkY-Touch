package com.example.service.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Music/video apps announce the audio session they play on with
 * ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION (and close it with ..._CLOSE_...).
 * We attach the equalizer to exactly those sessions. Declared in the manifest AND registered
 * dynamically by [EqualizerService].
 */
class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val opened = when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> true
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> false
            else -> return
        }
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)

        // Keep the process alive until the effects are attached.
        val pending = goAsync()
        EqualizerController.onSessionEvent(context.applicationContext, opened, sessionId, pkg) {
            try { pending.finish() } catch (_: Throwable) {}
        }
    }
}
