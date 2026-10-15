package com.example.service.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores the equalizer after a reboot or an app update, if it was switched on. */
class EqualizerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                EqualizerController.init(context.applicationContext)
                EqualizerController.ensureService(context.applicationContext)
            }
        }
    }
}
