package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.R

data class UpdateInfo(val version: String, val releaseUrl: String, val isUpdateAvailable: Boolean)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_OWNER = "shubhendu-rgb"
    private const val GITHUB_REPO = "SkY-Touch"

    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "update_prefs"
    private const val KEY_LAST_NOTIFIED = "last_notified_version"

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SkYTouch-Android")
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // 404 = no published (non-draft, non-prerelease) release yet, or repo is private
                // 403 = GitHub rate limit
                Log.w(TAG, "GitHub returned HTTP $code")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                return@withContext null
            }

            val latest = normalizeVersion(json.getString("tag_name"))
            val htmlUrl = json.getString("html_url")

            @Suppress("DEPRECATION")
            val currentRaw = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "0"
            val current = normalizeVersion(currentRaw)

            UpdateInfo(latest, htmlUrl, isVersionGreater(latest, current))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** "v8.4", "V8.4-beta", "SkY Touch 8.4" -> "8.4" */
    private fun normalizeVersion(raw: String): String {
        val match = Regex("""\d+(\.\d+)*""").find(raw)
        return match?.value ?: "0"
    }

    private fun isVersionGreater(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Shows the notification once per new version. Returns true if it was posted. */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_NOTIFIED, null) == updateInfo.version) return false
        if (!canPostNotifications(context)) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifies you when a new version is released on GitHub" }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle("App Update Available")
            .setContentText("Version ${updateInfo.version} is available to download.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_LAST_NOTIFIED, updateInfo.version).apply()
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
            false
        }
    }
}
