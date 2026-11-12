package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.GameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GameCandidate(val info: AppInfo, val looksLikeGame: Boolean)

/**
 * Game Mode screen: pick the games. While one of them is open the whole app stops all its functions
 * for the best possible gameplay, and everything restarts by itself when the game is closed.
 */
@Composable
fun GameModeTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(GameMode.isEnabled(context)) }
    var selected by remember { mutableStateOf(GameMode.getPackages(context)) }
    var apps by remember { mutableStateOf<List<GameCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var gamesOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        apps = list
        loading = false
    }

    val shown = remember(apps, selected, query, gamesOnly) {
        apps.filter { c ->
            (query.isBlank() ||
                c.info.label.contains(query, ignoreCase = true) ||
                c.info.packageName.contains(query, ignoreCase = true)) &&
                (!gamesOnly || c.looksLikeGame || selected.contains(c.info.packageName))
        }.map { it.info.copy(isExcluded = selected.contains(it.info.packageName)) }
            // chosen games first, then A-Z
            .sortedWith(compareByDescending<AppInfo> { it.isExcluded }.thenBy { it.label.lowercase() })
    }

    Column(
        modifier = modifier.fillMaxSize().testTag("game_mode_screen").padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Game Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "When a game you choose below is open, this app stops everything: overlays, gestures, " +
                                "Auto Click, notification and SMS detection and wallpaper changes. It all restarts " +
                                "by itself when you leave the game.",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            GameMode.setEnabled(context, it)
                        },
                        modifier = Modifier.testTag("switch_game_mode")
                    )
                }
                Text(
                    "${selected.size} game(s) selected. The accessibility service must stay enabled.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
            FilterChip(
                selected = gamesOnly,
                onClick = { gamesOnly = !gamesOnly },
                label = { Text("Games only", fontSize = 12.sp) },
                leadingIcon = if (gamesOnly) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(44.dp)
            )
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(shown, key = { it.packageName }) { app ->
                    AppItemRow(
                        app = app,
                        onToggle = { checked ->
                            val next = selected.toMutableSet().apply {
                                if (checked) add(app.packageName) else remove(app.packageName)
                            }
                            selected = next
                            GameMode.setPackages(context, next)
                        }
                    )
                }
            }
        }
    }
}

private fun loadLaunchableApps(context: Context): List<GameCandidate> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
        val pkg = ri.activityInfo.packageName
        if (pkg.isEmpty() || pkg == context.packageName) return@mapNotNull null
        val ai = ri.activityInfo.applicationInfo
        val isGame = try {
            @Suppress("DEPRECATION")
            ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    ai.category == ApplicationInfo.CATEGORY_GAME))
        } catch (_: Exception) { false }
        GameCandidate(AppInfo(ri.loadLabel(pm).toString(), pkg, false), isGame)
    }.distinctBy { it.info.packageName }
}
