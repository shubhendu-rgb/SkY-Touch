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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.R

data class UpdateInfo(val version: String, val releaseUrl: String, val isUpdateAvailable: Boolean)

object UpdateChecker {
    // ⚠️ REPLACE THESE WITH YOUR ACTUAL GITHUB REPO DETAILS BEFORE PUBLISHING ⚠️
    // e.g., if your repo is https://github.com/john/my-app
    // GITHUB_OWNER = "john", GITHUB_REPO = "my-app"
    private const val GITHUB_OWNER = "YOUR_GITHUB_USERNAME"
    private const val GITHUB_REPO = "YOUR_REPO_NAME"

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        if (GITHUB_OWNER == "YOUR_GITHUB_USERNAME") {
            Log.w("UpdateChecker", "GitHub repo details not configured. Skipping update check.")
            return@withContext null
        }

        try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                // GitHub tags often start with 'v' (e.g. "v1.0.1"), strip it for comparison
                val tagName = jsonObject.getString("tag_name").replace("v", "", ignoreCase = true)
                val htmlUrl = jsonObject.getString("html_url")

                val currentVersionInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = currentVersionInfo.versionName?.replace("v", "", ignoreCase = true) ?: "0.0.0"

                val isUpdateAvailable = isVersionGreater(tagName, currentVersion)

                return@withContext UpdateInfo(tagName, htmlUrl, isUpdateAvailable)
            }
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Failed to check for updates", e)
        }
        return@withContext null
    }

    private fun isVersionGreater(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        val channelId = "app_updates"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("App Update Available")
            .setContentText("Version ${updateInfo.version} is available to download.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1001, notification)
            }
        } else {
            notificationManager.notify(1001, notification)
        }
    }
}
