package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import org.xmlpull.v1.XmlPullParser

data class AppShortcutItem(
    val id: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val subtitle: String? = null,
    val iconBitmap: ImageBitmap? = null,
    val iconVector: ImageVector? = null,
    val intentUri: String,
    val isCreator: Boolean = false,
    val creatorClassName: String? = null,
    val category: String = "App Shortcuts" // "App Shortcuts", "Interactive", "System Utilities"
)

object AppShortcutHelper {

    @Volatile
    var cachedAppShortcuts: List<AppShortcutItem>? = null

    private fun Drawable.toSafeBitmap(): Bitmap? {
        return try {
            if (this is BitmapDrawable && this.bitmap != null) {
                return this.bitmap
            }
            val width = if (intrinsicWidth in 1..144) intrinsicWidth else 96
            val height = if (intrinsicHeight in 1..144) intrinsicHeight else 96
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getInstantShortcuts(context: Context): List<AppShortcutItem> {
        cachedAppShortcuts?.let { if (it.isNotEmpty()) return it }

        val pm = context.packageManager
        val items = mutableListOf<AppShortcutItem>()
        val seenKeys = mutableSetOf<String>()

        fun addIfUnique(item: AppShortcutItem) {
            val key = "${item.packageName.lowercase()}:${item.title.lowercase().trim()}"
            if (seenKeys.add(key)) {
                items.add(item)
            }
        }

        loadPopularAppShortcuts(context, pm).forEach { addIfUnique(it) }
        loadLegacyShortcutCreators(pm).forEach { addIfUnique(it) }
        getSystemUtilityShortcuts().forEach { addIfUnique(it) }
        return items
    }

    fun getOrLoadAllShortcuts(context: Context, forceRefresh: Boolean = false): List<AppShortcutItem> {
        if (!forceRefresh) {
            cachedAppShortcuts?.let { return it }
        }

        val pm = context.packageManager
        val items = mutableListOf<AppShortcutItem>()
        val seenKeys = mutableSetOf<String>()

        fun addIfUnique(item: AppShortcutItem) {
            val key = "${item.packageName.lowercase()}:${item.title.lowercase().trim()}"
            if (seenKeys.add(key)) {
                items.add(item)
            }
        }

        // ==========================================
        // 1. POPULAR APPS GUARANTEED FIRST-CLASS SHORTCUTS
        // (YouTube Shorts & Subscriptions, WhatsApp Chats, etc.)
        // ==========================================
        loadPopularAppShortcuts(context, pm).forEach { addIfUnique(it) }

        // ==========================================
        // 2. PARSE STATIC SHORTCUTS (android.app.shortcuts) FROM ALL INSTALLED APPS
        // ==========================================
        loadStaticShortcutsFromInstalledApps(pm).forEach { addIfUnique(it) }

        // ==========================================
        // 3. LAUNCHERAPPS DYNAMIC & MANIFEST SHORTCUTS (Android 7.1+)
        // ==========================================
        loadLauncherAppsShortcuts(context, pm).forEach { addIfUnique(it) }

        // ==========================================
        // 4. APPS SUPPORTING ACTION_CREATE_SHORTCUT (Interactive Pickers: WhatsApp Chats, Maps Routes, etc.)
        // ==========================================
        loadLegacyShortcutCreators(pm).forEach { addIfUnique(it) }

        // ==========================================
        // 5. BUILT-IN FAST SYSTEM SHORTCUTS
        // ==========================================
        getSystemUtilityShortcuts().forEach { addIfUnique(it) }

        // Sort: App Shortcuts (by app name then title) -> Interactive Pickers -> System Utilities
        val sorted = items.sortedWith(
            compareBy<AppShortcutItem> {
                when (it.category) {
                    "App Shortcuts" -> 0
                    "Custom Pickers" -> 1
                    else -> 2
                }
            }.thenBy { it.appName.lowercase() }
             .thenBy { it.title.lowercase() }
        )

        cachedAppShortcuts = sorted
        return sorted
    }

    private fun loadPopularAppShortcuts(context: Context, pm: PackageManager): List<AppShortcutItem> {
        val list = mutableListOf<AppShortcutItem>()

        fun getAppInfo(pkg: String): Pair<String, ImageBitmap?>? {
            return try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo).toSafeBitmap()?.asImageBitmap()
                Pair(label, icon)
            } catch (_: Exception) {
                null
            }
        }

        // --- YOUTUBE SHORTCUTS ---
        val youtubePkg = "com.google.android.youtube"
        val youtubeInfo = getAppInfo(youtubePkg)
        if (youtubeInfo != null) {
            val (ytLabel, ytIcon) = youtubeInfo

            // 1. YouTube Shorts
            val shortsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/shorts")).apply {
                setPackage(youtubePkg)
            }
            list.add(
                AppShortcutItem(
                    id = "yt_shorts",
                    appName = ytLabel,
                    packageName = youtubePkg,
                    title = "Shorts",
                    subtitle = "Open YouTube Shorts vertical video feed",
                    iconBitmap = ytIcon,
                    intentUri = shortsIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 2. YouTube Subscriptions
            val subsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/feed/subscriptions")).apply {
                setPackage(youtubePkg)
            }
            list.add(
                AppShortcutItem(
                    id = "yt_subscriptions",
                    appName = ytLabel,
                    packageName = youtubePkg,
                    title = "Subscriptions",
                    subtitle = "View feed of channels you subscribe to",
                    iconBitmap = ytIcon,
                    intentUri = subsIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 3. YouTube Search
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(youtubePkg)
                putExtra("query", "")
            }
            list.add(
                AppShortcutItem(
                    id = "yt_search",
                    appName = ytLabel,
                    packageName = youtubePkg,
                    title = "Search",
                    subtitle = "Directly search videos and music on YouTube",
                    iconBitmap = ytIcon,
                    intentUri = searchIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 4. YouTube Trending / Explore
            val trendingIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/feed/trending")).apply {
                setPackage(youtubePkg)
            }
            list.add(
                AppShortcutItem(
                    id = "yt_trending",
                    appName = ytLabel,
                    packageName = youtubePkg,
                    title = "Trending / Explore",
                    subtitle = "Discover popular trending videos & topics",
                    iconBitmap = ytIcon,
                    intentUri = trendingIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 5. YouTube Library
            val libraryIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/feed/library")).apply {
                setPackage(youtubePkg)
            }
            list.add(
                AppShortcutItem(
                    id = "yt_library",
                    appName = ytLabel,
                    packageName = youtubePkg,
                    title = "Library & History",
                    subtitle = "View your watch history and playlists",
                    iconBitmap = ytIcon,
                    intentUri = libraryIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- WHATSAPP SHORTCUTS ---
        val waPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (waPkg in waPackages) {
            val waInfo = getAppInfo(waPkg)
            if (waInfo != null) {
                val (waLabel, waIcon) = waInfo

                // 1. WhatsApp Chats & Groups Picker (Interactive 1-tap contact/chat picker)
                list.add(
                    AppShortcutItem(
                        id = "${waPkg}_pick_chat",
                        appName = waLabel,
                        packageName = waPkg,
                        title = "Chats & Groups (Pick Contact)",
                        subtitle = "Choose any WhatsApp chat or group to open with 1 gesture",
                        iconBitmap = waIcon,
                        intentUri = "whatsapp://pick_chat",
                        isCreator = true,
                        creatorClassName = "com.whatsapp.ContactPicker",
                        category = "Custom Pickers"
                    )
                )

                // 2. WhatsApp New Chat
                val newChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send")).apply {
                    setPackage(waPkg)
                }
                list.add(
                    AppShortcutItem(
                        id = "${waPkg}_new_chat",
                        appName = waLabel,
                        packageName = waPkg,
                        title = "New Chat",
                        subtitle = "Start a new conversation in WhatsApp",
                        iconBitmap = waIcon,
                        intentUri = newChatIntent.toUri(Intent.URI_INTENT_SCHEME),
                        category = "App Shortcuts"
                    )
                )

                // 3. WhatsApp Camera
                val camIntent = Intent("android.media.action.STILL_IMAGE_CAMERA").apply {
                    setPackage(waPkg)
                }
                list.add(
                    AppShortcutItem(
                        id = "${waPkg}_camera",
                        appName = waLabel,
                        packageName = waPkg,
                        title = "Camera",
                        subtitle = "Quick camera for status or direct photos",
                        iconBitmap = waIcon,
                        intentUri = camIntent.toUri(Intent.URI_INTENT_SCHEME),
                        category = "App Shortcuts"
                    )
                )
                break
            }
        }

        // --- GOOGLE MAPS SHORTCUTS ---
        val mapsPkg = "com.google.android.apps.maps"
        val mapsInfo = getAppInfo(mapsPkg)
        if (mapsInfo != null) {
            val (mapsLabel, mapsIcon) = mapsInfo

            // 1. Navigate Home
            val homeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Home")).apply {
                setPackage(mapsPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "maps_home",
                    appName = mapsLabel,
                    packageName = mapsPkg,
                    title = "Navigate Home",
                    subtitle = "One-touch turn-by-turn navigation to Home",
                    iconBitmap = mapsIcon,
                    intentUri = homeIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 2. Navigate to Work
            val workIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Work")).apply {
                setPackage(mapsPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "maps_work",
                    appName = mapsLabel,
                    packageName = mapsPkg,
                    title = "Navigate to Work",
                    subtitle = "Instant navigation directions to Work",
                    iconBitmap = mapsIcon,
                    intentUri = workIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 3. Live Traffic
            val trafficIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=traffic")).apply {
                setPackage(mapsPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "maps_traffic",
                    appName = mapsLabel,
                    packageName = mapsPkg,
                    title = "Live Traffic View",
                    subtitle = "View real-time road conditions and traffic",
                    iconBitmap = mapsIcon,
                    intentUri = trafficIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            // 4. Explore Nearby
            val nearbyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=restaurants")).apply {
                setPackage(mapsPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "maps_nearby",
                    appName = mapsLabel,
                    packageName = mapsPkg,
                    title = "Explore Nearby",
                    subtitle = "Discover nearby restaurants and food spots",
                    iconBitmap = mapsIcon,
                    intentUri = nearbyIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- GOOGLE CHROME SHORTCUTS ---
        val chromePkg = "com.android.chrome"
        val chromeInfo = getAppInfo(chromePkg)
        if (chromeInfo != null) {
            val (chromeLabel, chromeIcon) = chromeInfo

            val newTabIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                setPackage(chromePkg)
            }
            list.add(
                AppShortcutItem(
                    id = "chrome_new_tab",
                    appName = chromeLabel,
                    packageName = chromePkg,
                    title = "New Tab",
                    subtitle = "Open a fresh web search tab",
                    iconBitmap = chromeIcon,
                    intentUri = newTabIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            val incognitoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                setPackage(chromePkg)
                putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
            }
            list.add(
                AppShortcutItem(
                    id = "chrome_incognito",
                    appName = chromeLabel,
                    packageName = chromePkg,
                    title = "New Incognito Tab",
                    subtitle = "Open a private browsing session",
                    iconBitmap = chromeIcon,
                    intentUri = incognitoIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- SPOTIFY SHORTCUTS ---
        val spotifyPkg = "com.spotify.music"
        val spotifyInfo = getAppInfo(spotifyPkg)
        if (spotifyInfo != null) {
            val (spotifyLabel, spotifyIcon) = spotifyInfo

            val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search")).apply {
                setPackage(spotifyPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "spotify_search",
                    appName = spotifyLabel,
                    packageName = spotifyPkg,
                    title = "Search Music",
                    subtitle = "Instant search for artists, albums, or songs",
                    iconBitmap = spotifyIcon,
                    intentUri = searchIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            val libraryIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:collection")).apply {
                setPackage(spotifyPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "spotify_library",
                    appName = spotifyLabel,
                    packageName = spotifyPkg,
                    title = "Your Library",
                    subtitle = "Access your favorite playlists & podcasts",
                    iconBitmap = spotifyIcon,
                    intentUri = libraryIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- INSTAGRAM SHORTCUTS ---
        val instaPkg = "com.instagram.android"
        val instaInfo = getAppInfo(instaPkg)
        if (instaInfo != null) {
            val (instaLabel, instaIcon) = instaInfo

            val dmIntent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://direct")).apply {
                setPackage(instaPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "insta_direct",
                    appName = instaLabel,
                    packageName = instaPkg,
                    title = "Direct Messages",
                    subtitle = "Open Instagram chats inbox",
                    iconBitmap = instaIcon,
                    intentUri = dmIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            val reelsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://reels")).apply {
                setPackage(instaPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "insta_reels",
                    appName = instaLabel,
                    packageName = instaPkg,
                    title = "Reels",
                    subtitle = "Open Instagram Reels stream",
                    iconBitmap = instaIcon,
                    intentUri = reelsIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            val postIntent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://camera")).apply {
                setPackage(instaPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "insta_camera",
                    appName = instaLabel,
                    packageName = instaPkg,
                    title = "New Post / Story",
                    subtitle = "Open camera to create a new post or story",
                    iconBitmap = instaIcon,
                    intentUri = postIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- TELEGRAM SHORTCUTS ---
        val telegramPkgs = listOf("org.telegram.messenger", "org.telegram.messenger.web")
        for (tgPkg in telegramPkgs) {
            val tgInfo = getAppInfo(tgPkg)
            if (tgInfo != null) {
                val (tgLabel, tgIcon) = tgInfo

                val savedIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=telegram")).apply {
                    setPackage(tgPkg)
                }
                list.add(
                    AppShortcutItem(
                        id = "tg_saved",
                        appName = tgLabel,
                        packageName = tgPkg,
                        title = "Saved Messages",
                        subtitle = "Open your personal Saved Messages",
                        iconBitmap = tgIcon,
                        intentUri = savedIntent.toUri(Intent.URI_INTENT_SCHEME),
                        category = "App Shortcuts"
                    )
                )

                val contactsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://contacts")).apply {
                    setPackage(tgPkg)
                }
                list.add(
                    AppShortcutItem(
                        id = "tg_contacts",
                        appName = tgLabel,
                        packageName = tgPkg,
                        title = "New Chat / Contacts",
                        subtitle = "Start a new conversation in Telegram",
                        iconBitmap = tgIcon,
                        intentUri = contactsIntent.toUri(Intent.URI_INTENT_SCHEME),
                        category = "App Shortcuts"
                    )
                )
                break
            }
        }

        // --- GMAIL SHORTCUTS ---
        val gmailPkg = "com.google.android.gm"
        val gmailInfo = getAppInfo(gmailPkg)
        if (gmailInfo != null) {
            val (gmailLabel, gmailIcon) = gmailInfo

            val composeIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                setPackage(gmailPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "gmail_compose",
                    appName = gmailLabel,
                    packageName = gmailPkg,
                    title = "Compose Email",
                    subtitle = "Start writing a draft in Gmail",
                    iconBitmap = gmailIcon,
                    intentUri = composeIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- X / TWITTER SHORTCUTS ---
        val twitterPkg = "com.twitter.android"
        val twitterInfo = getAppInfo(twitterPkg)
        if (twitterInfo != null) {
            val (twitterLabel, twitterIcon) = twitterInfo

            val tweetIntent = Intent(Intent.ACTION_VIEW, Uri.parse("twitter://post")).apply {
                setPackage(twitterPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "twitter_compose",
                    appName = twitterLabel,
                    packageName = twitterPkg,
                    title = "New Post",
                    subtitle = "Draft and publish a new post",
                    iconBitmap = twitterIcon,
                    intentUri = tweetIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )

            val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("twitter://search")).apply {
                setPackage(twitterPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "twitter_search",
                    appName = twitterLabel,
                    packageName = twitterPkg,
                    title = "Explore & Search",
                    subtitle = "Explore top trends and news",
                    iconBitmap = twitterIcon,
                    intentUri = searchIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- GOOGLE PHOTOS SHORTCUTS ---
        val photosPkg = "com.google.android.apps.photos"
        val photosInfo = getAppInfo(photosPkg)
        if (photosInfo != null) {
            val (photosLabel, photosIcon) = photosInfo

            val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com/search")).apply {
                setPackage(photosPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "photos_search",
                    appName = photosLabel,
                    packageName = photosPkg,
                    title = "Search Photos",
                    subtitle = "Find faces, places, and screenshots",
                    iconBitmap = photosIcon,
                    intentUri = searchIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        // --- GOOGLE PLAY STORE SHORTCUTS ---
        val playPkg = "com.android.vending"
        val playInfo = getAppInfo(playPkg)
        if (playInfo != null) {
            val (playLabel, playIcon) = playInfo

            val updatesIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps")).apply {
                setPackage(playPkg)
            }
            list.add(
                AppShortcutItem(
                    id = "play_updates",
                    appName = playLabel,
                    packageName = playPkg,
                    title = "Manage Apps & Updates",
                    subtitle = "Check for pending app downloads and updates",
                    iconBitmap = playIcon,
                    intentUri = updatesIntent.toUri(Intent.URI_INTENT_SCHEME),
                    category = "App Shortcuts"
                )
            )
        }

        return list
    }

    /**
     * Parses the <shortcuts> XML resource from all installed apps that declare
     * <meta-data android:name="android.app.shortcuts" android:resource="@xml/..." />
     */
    private fun loadStaticShortcutsFromInstalledApps(pm: PackageManager): List<AppShortcutItem> {
        val list = mutableListOf<AppShortcutItem>()
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = try {
            pm.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }

        for (resolveInfo in activities) {
            val actInfo = resolveInfo.activityInfo ?: continue
            val appInfo = actInfo.applicationInfo ?: continue
            val pkg = actInfo.packageName

            val metaData = actInfo.metaData ?: appInfo.metaData ?: continue
            val xmlResId = metaData.getInt("android.app.shortcuts", 0)
            if (xmlResId == 0) continue

            val appLabel = resolveInfo.loadLabel(pm).toString()
            val appIconBitmap = resolveInfo.loadIcon(pm)?.toSafeBitmap()?.asImageBitmap()

            try {
                val appRes = pm.getResourcesForApplication(appInfo)
                val parser = appRes.getXml(xmlResId)

                var eventType = parser.eventType
                var currentShortcutId: String? = null
                var currentEnabled = true
                var currentShortLabel: String? = null
                var currentLongLabel: String? = null
                var currentIconResId = 0
                var currentIntentAction: String? = null
                var currentIntentPkg: String? = null
                var currentIntentCls: String? = null
                var currentIntentData: String? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val tagName = parser.name
                        if (tagName == "shortcut") {
                            currentShortcutId = null
                            currentEnabled = true
                            currentShortLabel = null
                            currentLongLabel = null
                            currentIconResId = 0
                            currentIntentAction = null
                            currentIntentPkg = null
                            currentIntentCls = null
                            currentIntentData = null

                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i)
                                when (attrName) {
                                    "shortcutId" -> currentShortcutId = parser.getAttributeValue(i)
                                    "enabled" -> currentEnabled = parser.getAttributeBooleanValue(i, true)
                                    "shortcutShortLabel" -> {
                                        val resId = parser.getAttributeResourceValue(i, 0)
                                        currentShortLabel = if (resId != 0) {
                                            try { appRes.getString(resId) } catch (_: Exception) { null }
                                        } else {
                                            parser.getAttributeValue(i)
                                        }
                                    }
                                    "shortcutLongLabel" -> {
                                        val resId = parser.getAttributeResourceValue(i, 0)
                                        currentLongLabel = if (resId != 0) {
                                            try { appRes.getString(resId) } catch (_: Exception) { null }
                                        } else {
                                            parser.getAttributeValue(i)
                                        }
                                    }
                                    "icon" -> {
                                        currentIconResId = parser.getAttributeResourceValue(i, 0)
                                    }
                                }
                            }
                        } else if (tagName == "intent") {
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i)
                                when (attrName) {
                                    "action" -> currentIntentAction = parser.getAttributeValue(i)
                                    "targetPackage" -> currentIntentPkg = parser.getAttributeValue(i)
                                    "targetClass" -> currentIntentCls = parser.getAttributeValue(i)
                                    "data" -> currentIntentData = parser.getAttributeValue(i)
                                }
                            }
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if (parser.name == "shortcut") {
                            val title = currentShortLabel?.takeIf { it.isNotBlank() }
                                ?: currentLongLabel?.takeIf { it.isNotBlank() }
                                ?: currentShortcutId?.replace('_', ' ')?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }

                            if (currentEnabled && !title.isNullOrBlank()) {
                                val targetPkg = currentIntentPkg?.takeIf { it.isNotBlank() } ?: pkg
                                val targetAction = currentIntentAction?.takeIf { it.isNotBlank() } ?: Intent.ACTION_VIEW
                                val shortcutIntent = Intent(targetAction).apply {
                                    if (!currentIntentData.isNullOrBlank()) {
                                        data = Uri.parse(currentIntentData)
                                    }
                                    if (!currentIntentCls.isNullOrBlank()) {
                                        setClassName(targetPkg, currentIntentCls!!)
                                    } else {
                                        setPackage(targetPkg)
                                    }
                                }

                                val shortcutIcon = if (currentIconResId != 0) {
                                    try {
                                        appRes.getDrawable(currentIconResId, null)?.toSafeBitmap()?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                } else null

                                list.add(
                                    AppShortcutItem(
                                        id = "${pkg}_${currentShortcutId ?: title}",
                                        appName = appLabel,
                                        packageName = pkg,
                                        title = title,
                                        subtitle = currentLongLabel ?: "$appLabel app shortcut",
                                        iconBitmap = shortcutIcon ?: appIconBitmap,
                                        intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME),
                                        category = "App Shortcuts"
                                    )
                                )
                            }
                        }
                    }
                    eventType = parser.next()
                }
            } catch (_: Throwable) {
                // Ignore XML errors for individual apps
            }
        }
        return list
    }

    /**
     * Attempts to query shortcuts via LauncherApps if available
     */
    private fun loadLauncherAppsShortcuts(context: Context, pm: PackageManager): List<AppShortcutItem> {
        val list = mutableListOf<AppShortcutItem>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                    val query = LauncherApps.ShortcutQuery().apply {
                        setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                        )
                    }
                    val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                    if (shortcuts != null) {
                        for (sc in shortcuts) {
                            val pkg = sc.`package`
                            val title = sc.shortLabel?.toString() ?: sc.longLabel?.toString() ?: sc.id
                            val appLabel = try {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            } catch (_: Exception) {
                                pkg
                            }
                            val intent = sc.intent ?: Intent(Intent.ACTION_VIEW).apply { setPackage(pkg) }
                            val iconDrawable = try {
                                launcherApps.getShortcutIconDrawable(sc, context.resources.displayMetrics.densityDpi)
                            } catch (_: Exception) {
                                null
                            }
                            list.add(
                                AppShortcutItem(
                                    id = "${pkg}_${sc.id}",
                                    appName = appLabel,
                                    packageName = pkg,
                                    title = title,
                                    subtitle = sc.longLabel?.toString() ?: "$appLabel shortcut",
                                    iconBitmap = iconDrawable?.toSafeBitmap()?.asImageBitmap(),
                                    intentUri = intent.toUri(Intent.URI_INTENT_SCHEME),
                                    category = "App Shortcuts"
                                )
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                // Not a shortcut host or permission denied; safely ignored
            }
        }
        return list
    }

    /**
     * Discovers all apps that support Intent.ACTION_CREATE_SHORTCUT
     * (e.g. WhatsApp conversation picker, Google Drive files, Chrome bookmarks)
     */
    private fun loadLegacyShortcutCreators(pm: PackageManager): List<AppShortcutItem> {
        val list = mutableListOf<AppShortcutItem>()
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val resolveList = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        for (resolveInfo in resolveList) {
            try {
                val appLabel = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                val cls = resolveInfo.activityInfo.name
                val icon = resolveInfo.loadIcon(pm).toSafeBitmap()?.asImageBitmap()

                val subtitle = when {
                    pkg.contains("whatsapp") -> "Select any chat or group to launch instantly"
                    pkg.contains("maps") -> "Pick any directions route or destination"
                    pkg.contains("chrome") -> "Pick a saved bookmark"
                    pkg.contains("docs") || pkg.contains("drive") -> "Pick a file or folder"
                    pkg.contains("contacts") || pkg.contains("dialer") -> "Pick a direct call or contact"
                    else -> "Create custom 1-tap shortcut with $appLabel"
                }

                val title = when {
                    pkg.contains("whatsapp") -> "WhatsApp Chats & Contacts Picker"
                    pkg.contains("maps") -> "Google Maps Route & Destination Picker"
                    else -> "$appLabel Shortcut Creator"
                }

                list.add(
                    AppShortcutItem(
                        id = "creator_${pkg}_$cls",
                        appName = appLabel,
                        packageName = pkg,
                        title = title,
                        subtitle = subtitle,
                        iconBitmap = icon,
                        intentUri = "creator://$pkg/$cls",
                        isCreator = true,
                        creatorClassName = cls,
                        category = "Custom Pickers"
                    )
                )
            } catch (_: Throwable) {}
        }
        return list
    }

    /**
     * Standard system utility shortcuts (always accessible)
     */
    private fun getSystemUtilityShortcuts(): List<AppShortcutItem> {
        return listOf(
            AppShortcutItem(
                id = "SHORTCUT_SELFIE",
                appName = "System Camera",
                packageName = "android.media",
                title = "Selfie Camera",
                subtitle = "Launch front-facing selfie camera directly",
                iconVector = Icons.Default.CameraAlt,
                intentUri = "SHORTCUT_SELFIE",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_SEARCH",
                appName = "Google",
                packageName = "com.google.android.googlequicksearchbox",
                title = "Web Search",
                subtitle = "Open Google Search immediately",
                iconVector = Icons.Default.Search,
                intentUri = "SHORTCUT_SEARCH",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_ALARM",
                appName = "Clock",
                packageName = "com.google.android.deskclock",
                title = "Clock & Alarms",
                subtitle = "Quick access to alarms and timers",
                iconVector = Icons.Default.Alarm,
                intentUri = "SHORTCUT_ALARM",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_EMAIL",
                appName = "Email",
                packageName = "com.google.android.gm",
                title = "Compose Email",
                subtitle = "Create a draft in default email app",
                iconVector = Icons.Default.Mail,
                intentUri = "SHORTCUT_EMAIL",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_BATTERY",
                appName = "System Settings",
                packageName = "com.android.settings",
                title = "Battery Saver & Usage",
                subtitle = "Quick toggle battery settings and stats",
                iconVector = Icons.Default.BatteryChargingFull,
                intentUri = "SHORTCUT_BATTERY",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_WIFI",
                appName = "System Settings",
                packageName = "com.android.settings",
                title = "Wi-Fi Settings",
                subtitle = "Manage wireless networks and connections",
                iconVector = Icons.Default.Wifi,
                intentUri = "SHORTCUT_WIFI",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_BLUETOOTH",
                appName = "System Settings",
                packageName = "com.android.settings",
                title = "Bluetooth Settings",
                subtitle = "Pair and switch Bluetooth accessories",
                iconVector = Icons.Default.Bluetooth,
                intentUri = "SHORTCUT_BLUETOOTH",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_SOUND",
                appName = "System Settings",
                packageName = "com.android.settings",
                title = "Sound & Vibration",
                subtitle = "Quick volume and sound profile settings",
                iconVector = Icons.Default.VolumeUp,
                intentUri = "SHORTCUT_SOUND",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_DISPLAY",
                appName = "System Settings",
                packageName = "com.android.settings",
                title = "Display & Timeout",
                subtitle = "Screen timeout and dark mode settings",
                iconVector = Icons.Default.Brightness6,
                intentUri = "SHORTCUT_DISPLAY",
                category = "System Utilities"
            ),
            AppShortcutItem(
                id = "SHORTCUT_APPS",
                appName = "System Settings",
                packageName = "com.android.settings",
                title = "Manage Apps",
                subtitle = "View installed applications and storage",
                iconVector = Icons.Default.Apps,
                intentUri = "SHORTCUT_APPS",
                category = "System Utilities"
            )
        )
    }
}
