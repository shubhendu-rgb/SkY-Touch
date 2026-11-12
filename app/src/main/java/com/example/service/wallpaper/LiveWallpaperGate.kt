package com.example.service.wallpaper

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Decides when the live wallpaper may animate: ONLY while the user is looking at the launcher (home screen).
 * It is closed while an app is open, while the screen is off and while the device is locked, so the wallpaper
 * costs nothing in the background and starts again by itself when the user is back on the home screen.
 *
 * The foreground app comes from the accessibility service. If that service is off, the gate falls back to
 * the normal Android visibility of the wallpaper (it never blocks the wallpaper by mistake).
 */
object LiveWallpaperGate {
    @Volatile private var launcherForeground = true
    private var homePackages: Set<String>? = null

    fun isOpen(context: Context): Boolean = launcherForeground && !isLockedOrScreenOff(context)

    private fun isLockedOrScreenOff(context: Context): Boolean = try {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        km.isKeyguardLocked || !pm.isInteractive
    } catch (_: Exception) {
        false
    }

    private fun loadHomePackages(context: Context): Set<String> {
        homePackages?.let { return it }
        val set = try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        homePackages = set
        return set
    }

    private fun isLauncher(context: Context, pkg: String): Boolean {
        if (loadHomePackages(context).contains(pkg)) return true
        // a launcher installed after the list was read
        if (pkg.contains("launcher", ignoreCase = true) || pkg.contains("quickstep", ignoreCase = true)) {
            homePackages = null
            return true
        }
        return false
    }

    /** Called by the accessibility service on every window change. [ignorable] = status bar, keyboard... */
    fun onForegroundPackage(context: Context, pkg: String, ignorable: Boolean) {
        if (pkg.isEmpty() || ignorable) return
        val launcher = isLauncher(context, pkg)
        if (launcher != launcherForeground) {
            launcherForeground = launcher
            notifyEngines()
        }
    }

    /** Accessibility service stopped: never keep the wallpaper blocked. */
    fun reset() {
        if (!launcherForeground) {
            launcherForeground = true
            notifyEngines()
        }
    }

    private fun notifyEngines() {
        try { SkyLiveWallpaperService.activeEngines.forEach { it.onGateChanged() } } catch (_: Exception) {}
    }
}
