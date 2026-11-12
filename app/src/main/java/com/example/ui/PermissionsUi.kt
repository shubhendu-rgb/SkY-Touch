package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.service.NotchAccessibilityService
import kotlinx.coroutines.delay

/** One permission the app can use, with the reason it is needed and whether it is granted right now. */
data class AppPermission(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val why: String,
    val usedFor: String,
    val required: Boolean,
    val granted: Boolean
)

private fun runtimeGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Reads the current state of every permission (cheap enough to call every few seconds). */
fun readAppPermissions(context: Context): List<AppPermission> {
    val pkg = context.packageName
    val list = ArrayList<AppPermission>()

    list.add(
        AppPermission(
            id = "ACCESSIBILITY",
            title = "Accessibility Service",
            icon = Icons.Filled.Accessibility,
            why = "Lets SkY Touch draw the invisible notch gesture areas and the Side Deck over other apps, and perform actions such as Back, Home, Recents, screenshots and taps for you.",
            usedFor = "Notch Gestures, Side Deck, AutoClick Studio, Text Assistant, Quick Notes, widget actions",
            required = true,
            granted = com.example.isAccessibilityServiceEnabled(context, NotchAccessibilityService::class.java)
        )
    )

    val listenerGranted = try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
    } catch (_: Exception) { false }
    list.add(
        AppPermission(
            id = "NOTIFICATION_ACCESS",
            title = "Notification Access",
            icon = Icons.Filled.NotificationsActive,
            why = "Lets the app read incoming notifications to find verification codes (OTPs). Nothing is sent anywhere.",
            usedFor = "Code Detector",
            required = false,
            granted = listenerGranted
        )
    )

    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runtimeGranted(context, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    list.add(
        AppPermission(
            id = "POST_NOTIFICATIONS",
            title = "Show Notifications",
            icon = Icons.Filled.Notifications,
            why = "Needed to show the detected-code notification with its copy button and the status notifications of running features.",
            usedFor = "Code Detector, recordings, background features",
            required = false,
            granted = notificationsGranted
        )
    )

    list.add(
        AppPermission(
            id = "RECEIVE_SMS",
            title = "Receive SMS",
            icon = Icons.Filled.Sms,
            why = "Lets the app notice verification codes in incoming text messages the moment they arrive.",
            usedFor = "Code Detector",
            required = false,
            granted = runtimeGranted(context, Manifest.permission.RECEIVE_SMS)
        )
    )

    list.add(
        AppPermission(
            id = "CAMERA",
            title = "Camera",
            icon = Icons.Filled.PhotoCamera,
            why = "Needed to record silent background videos and to control the flashlight from gestures.",
            usedFor = "Video Recordings, flashlight gesture",
            required = false,
            granted = runtimeGranted(context, Manifest.permission.CAMERA)
        )
    )

    list.add(
        AppPermission(
            id = "CALL_PHONE",
            title = "Phone (make calls)",
            icon = Icons.Filled.Call,
            why = "Lets the Quick Dial gesture start the call directly. Without it, the dialer opens with the number ready.",
            usedFor = "Quick Dial gesture",
            required = false,
            granted = runtimeGranted(context, Manifest.permission.CALL_PHONE)
        )
    )

    list.add(
        AppPermission(
            id = "WRITE_SETTINGS",
            title = "Modify System Settings",
            icon = Icons.Filled.BrightnessMedium,
            why = "Needed to change the screen brightness from gestures.",
            usedFor = "Brightness gestures",
            required = false,
            granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true
        )
    )

    val ignoringBattery = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(pkg) == true
        } else true
    } catch (_: Exception) { true }
    list.add(
        AppPermission(
            id = "BATTERY",
            title = "Unrestricted Battery",
            icon = Icons.Filled.BatteryChargingFull,
            why = "Stops the system from putting the app to sleep, so gestures, timers, the wallpaper changer and the 2 minute lock wallpaper restore keep working in the background.",
            usedFor = "All background features",
            required = true,
            granted = ignoringBattery
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(
            AppPermission(
                id = "BLUETOOTH_CONNECT",
                title = "Nearby Devices (Bluetooth)",
                icon = Icons.Filled.Bluetooth,
                why = "Lets the equalizer tell Bluetooth speakers from headphones so each can have its own preset.",
                usedFor = "Equalizer (optional)",
                required = false,
                granted = runtimeGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
            )
        )
    }
    return list
}

/** Permission states that refresh when the app returns to the foreground and every few seconds. */
@Composable
fun rememberAppPermissions(pollMs: Long = 2500L): List<AppPermission> {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(readAppPermissions(context)) }
    var resumeTick by remember { mutableLongStateOf(0L) }

    DisposableEffect(context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(resumeTick) {
        while (true) {
            val fresh = readAppPermissions(context)
            if (fresh != permissions) permissions = fresh
            delay(pollMs)
        }
    }
    return permissions
}

/** Top bar shield icon with a red badge showing how many permissions are still missing. */
@Composable
fun PermissionsIconButton(permissions: List<AppPermission>, tint: Color, onClick: () -> Unit) {
    val missing = permissions.count { !it.granted }
    IconButton(onClick = onClick) {
        if (missing > 0) {
            BadgedBox(badge = { Badge { Text("$missing") } }) {
                Icon(Icons.Filled.Security, contentDescription = "Permissions ($missing missing)", tint = tint)
            }
        } else {
            Icon(Icons.Filled.Security, contentDescription = "Permissions", tint = tint)
        }
    }
}

/** Opens the right system screen (or asks) for one permission. */
private fun openGrantScreen(context: Context, id: String) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val intent = when (id) {
        "ACCESSIBILITY" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "NOTIFICATION_ACCESS" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        "WRITE_SETTINGS" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
        "BATTERY" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
        "POST_NOTIFICATIONS" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(permissions: List<AppPermission>, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Runtime permissions: if Android answers "denied" instantly (no dialog shown) the permission was
    // denied permanently, so the app settings page is opened instead.
    var pendingId by remember { mutableStateOf<String?>(null) }
    var requestedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingId
        pendingId = null
        if (!granted && id != null && SystemClock.elapsedRealtime() - requestedAt < 350L) {
            openGrantScreen(context, "APP_DETAILS")
        }
    }

    fun grant(id: String) {
        val runtimePermission = when (id) {
            "RECEIVE_SMS" -> Manifest.permission.RECEIVE_SMS
            "CAMERA" -> Manifest.permission.CAMERA
            "CALL_PHONE" -> Manifest.permission.CALL_PHONE
            "BLUETOOTH_CONNECT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
            "POST_NOTIFICATIONS" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            else -> null
        }
        if (runtimePermission != null) {
            pendingId = id
            requestedAt = SystemClock.elapsedRealtime()
            launcher.launch(runtimePermission)
        } else {
            openGrantScreen(context, id)
        }
    }

    val missing = permissions.count { !it.granted }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column {
                    Text(
                        "Permissions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (missing == 0) "Everything is granted. All features can run."
                        else "$missing of ${permissions.size} not granted yet. Grant what you need for the features you use.",
                        fontSize = 12.sp,
                        color = if (missing == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                }
            }
            items(permissions, key = { it.id }) { permission ->
                PermissionCard(permission = permission, onGrant = { grant(permission.id) })
            }
            item {
                Text(
                    "Granted automatically, no action needed: Internet (AI text tools and update check), Set wallpaper, " +
                        "Vibrate, Run in the background (foreground service), Start after reboot, See installed apps " +
                        "(app pickers and exclusions) and Audio settings (equalizer, volume gestures).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(permission: AppPermission, onGrant: () -> Unit) {
    val okColor = Color(0xFF2E9E5B)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (permission.granted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            1.dp,
            if (permission.granted) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(permission.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        permission.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (permission.required) "REQUIRED" else "OPTIONAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (permission.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    permission.why,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    "Used for: ${permission.usedFor}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!permission.granted) {
                    Button(
                        onClick = onGrant,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (permission.granted) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Granted",
                    tint = okColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
