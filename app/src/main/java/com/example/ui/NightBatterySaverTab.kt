package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.QuietHours

/**
 * Whole-app battery saver for sleeping time. The user picks a From / To time (AM/PM).
 * Between those times the overlays are hidden and the automatic wallpaper changers rest;
 * everything comes back by itself at the end time.
 */
@Composable
fun NightBatterySaverTab() {
    val context = LocalContext.current
    // Re-read the "active now" status whenever a setting changes
    var refresh by remember { mutableIntStateOf(0) }
    val activeNow = remember(refresh) { QuietHours.isActiveNow(context, QuietHours.NIGHT_SAVER) }
    val enabled = remember(refresh) { QuietHours.isEnabled(context, QuietHours.NIGHT_SAVER) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp)
            .testTag("night_battery_saver_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Lowers the battery use of the whole app while you sleep. Set the time you go to sleep and the time you wake up.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_night_saver"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Night Battery Saver", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                QuietHoursControls(
                    key = QuietHours.NIGHT_SAVER,
                    title = "Save battery at night",
                    description = "Active between the two times below, every day.",
                    textColor = MaterialTheme.colorScheme.onSurface,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onChanged = {
                        refresh++
                        com.example.util.NightSaver.sync(context, force = true)
                    }
                )
                if (enabled) {
                    Text(
                        if (activeNow) "Saving battery right now" else "Waiting for sleeping time",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("While it is active", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                listOf(
                    "Side Deck handle is removed",
                    "Notch gesture areas are hidden",
                    "Wallpaper changers (Home, Lock and Live) are fully stopped: no timers, no wake-ups",
                    "The live wallpaper freezes on one frame instead of animating",
                    "Auto Click stops and the accessibility service receives no events",
                    "Everything returns by itself at the end time"
                ).forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    "Everything restarts at the end time, even if the app is closed. Keep the accessibility service enabled so the overlays can come back.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
