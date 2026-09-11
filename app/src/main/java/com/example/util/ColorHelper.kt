package com.example.util

import android.content.Context
import android.graphics.Color
import android.os.Build

object ColorHelper {
    fun getDynamicColorSafe(context: Context, colorId: Int, fallbackHex: String): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.resources.getColor(colorId, null)
            } catch (e: Exception) {
                // Ignore
            }
        }
        return Color.parseColor(fallbackHex)
    }
}
