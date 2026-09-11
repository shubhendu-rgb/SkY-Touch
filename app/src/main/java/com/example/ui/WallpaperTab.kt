package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppDatabase
import com.example.data.WallpaperConfigEntity
import com.example.service.WallpaperWorker
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun WallpaperTab() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Home Screen", "Lock Screen")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        val screenType = if (selectedTabIndex == 0) "HOME" else "LOCK"
        WallpaperConfigScreen(screenType = screenType)
    }
}

@Composable
fun WallpaperConfigScreen(screenType: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).wallpaperConfigDao() }
    val configState by dao.getConfigFlow(screenType).collectAsState(initial = null)
    
    val config = configState ?: WallpaperConfigEntity(screenType = screenType)
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            scope.launch {
                dao.saveConfig(config.copy(folderUri = uri.toString()))
            }
        }
    }

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val formattedTime = String.format("%02d:%02d", hourOfDay, minute)
            scope.launch {
                val updated = config.copy(scheduledTime = formattedTime)
                dao.saveConfig(updated)
                WallpaperWorker.scheduleWallpaperWork(context, updated)
            }
        },
        12, 0, true
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Selected Folder", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = config.folderUri ?: "No folder selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (config.folderUri == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Wallpaper Folder")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Auto-Wallpaper", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = config.isEnabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        val updated = config.copy(isEnabled = checked)
                        dao.saveConfig(updated)
                        WallpaperWorker.scheduleWallpaperWork(context, updated)
                    }
                },
                enabled = config.folderUri != null
            )
        }

        if (config.isEnabled) {
            Divider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Update Mode: ", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("Interval")
                Switch(
                    checked = config.useSchedule,
                    onCheckedChange = { useSchedule ->
                        scope.launch {
                            val updated = config.copy(useSchedule = useSchedule)
                            dao.saveConfig(updated)
                            WallpaperWorker.scheduleWallpaperWork(context, updated)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text("Exact Time")
            }

            if (!config.useSchedule) {
                Column {
                    Text("Interval: ${config.intervalMinutes} minutes", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.intervalMinutes.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                dao.saveConfig(config.copy(intervalMinutes = value.toInt()))
                            }
                        },
                        onValueChangeFinished = {
                            WallpaperWorker.scheduleWallpaperWork(context, config)
                        },
                        valueRange = 1f..60f,
                        steps = 59
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scheduled Time: ${config.scheduledTime ?: "Not Set"}")
                    OutlinedButton(onClick = { timePickerDialog.show() }) {
                        Text("Set Time")
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Corner Gradient", style = MaterialTheme.typography.titleMedium)
                    Text("Adds a dark gradient to top and bottom for readability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = config.applyGradient,
                    onCheckedChange = { checked ->
                        scope.launch {
                            dao.saveConfig(config.copy(applyGradient = checked))
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Black & White Filter", style = MaterialTheme.typography.titleMedium)
                    Text("Applies a grayscale effect to the wallpaper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = config.applyGrayscale,
                    onCheckedChange = { checked ->
                        scope.launch {
                            dao.saveConfig(config.copy(applyGrayscale = checked))
                        }
                    }
                )
            }
        }
    }
}
