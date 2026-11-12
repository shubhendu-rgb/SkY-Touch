package com.example.util

import android.content.Context
import com.example.service.NotchAccessibilityService

/**
 * Game Mode: while one of the games chosen by the user is on screen, the whole app stands still
 * (overlays removed, Auto Click stopped, notification / SMS listeners and wallpaper changes skipped,
 * accessibility events reduced to the bare minimum needed to notice when the game is closed).
 * Everything comes back by itself when another app or the home screen is opened.
 */
object GameMode {
    private const val PREFS = "game_mode_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PACKAGES = "game_packages"

    /** True while a chosen game is in the foreground (set by the accessibility service). */
    @Volatile var active: Boolean = false

    @Volatile private var cachedEnabled: Boolean? = null
    @Volatile private var cachedPackages: Set<String>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(): Boolean = active

    fun isEnabled(context: Context): Boolean =
        cachedEnabled ?: prefs(context).getBoolean(KEY_ENABLED, false).also { cachedEnabled = it }

    fun getPackages(context: Context): Set<String> =
        cachedPackages ?: (prefs(context).getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet())
            .toSet().also { cachedPackages = it }

    fun isGame(context: Context, pkg: String): Boolean =
        isEnabled(context) && getPackages(context).contains(pkg)

    fun setEnabled(context: Context, enabled: Boolean) {
        cachedEnabled = enabled
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        NotchAccessibilityService.instance?.onGameModeSettingsChanged()
    }

    fun setPackages(context: Context, packages: Set<String>) {
        cachedPackages = packages.toSet()
        prefs(context).edit().putStringSet(KEY_PACKAGES, packages.toSet()).apply()
        NotchAccessibilityService.instance?.onGameModeSettingsChanged()
    }
}
