package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoClickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_AUTOCLICK") {
            NotchAccessibilityService.instance?.autoClickEngine?.stop("Stopped from notification")
        }
    }
}
