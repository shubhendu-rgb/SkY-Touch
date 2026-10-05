package com.example.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.WallpaperConfigEntity
import com.example.service.WallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WallpaperTab(
    initialType: Int = 0, // 0 = Static Wallpaper, 1 = Live Wallpaper
    onBack: (() -> Unit)? = null
) {
    var selectedWallpaperType by remember { mutableIntStateOf(initialType) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wallpaper_changer_root")
    ) {
        // LAYER 1: Full-Screen Live/Static Background Preview touching ALL 4 SIDES!
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedWallpaperType == 1) {
                LiveWallpaperTab(onBack = onBack ?: { selectedWallpaperType = 0 })
            } else {
                StaticWallpaperScreen(
                    onOpenLiveWallpaper = { selectedWallpaperType = 1 }
                )
            }
        }

        // LAYER 2: Floating Header with Back Button & High-End Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.50f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.50f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Static Option
                    Surface(
                        onClick = { selectedWallpaperType = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_type_static"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedWallpaperType == 0) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = if (selectedWallpaperType == 0) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Static Wallpaper",
                                fontWeight = if (selectedWallpaperType == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedWallpaperType == 0) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }

                    // Live Option
                    Surface(
                        onClick = { selectedWallpaperType = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_type_live"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedWallpaperType == 1) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (selectedWallpaperType == 1) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Live Wallpaper",
                                fontWeight = if (selectedWallpaperType == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedWallpaperType == 1) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaticWallpaperScreen(
    onOpenLiveWallpaper: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screenType by remember { mutableStateOf("HOME") }
    val dao = remember { AppDatabase.getDatabase(context).wallpaperConfigDao() }
    val configState by dao.getConfigFlow(screenType).collectAsState(initial = null)

    val config = configState ?: WallpaperConfigEntity(screenType = screenType)
    val individualImages = remember(config.imageUris) { config.getImageUriList() }

    var currentBlurRadius by remember(config.blurRadius) { mutableIntStateOf(config.blurRadius) }
    var previewUriString by remember(individualImages) {
        mutableStateOf(individualImages.firstOrNull())
    }
    var showControls by remember { mutableStateOf(true) }
    var showFullscreenPreview by remember { mutableStateOf(false) }

    LaunchedEffect(individualImages) {
        if (previewUriString == null || !individualImages.contains(previewUriString)) {
            previewUriString = individualImages.firstOrNull()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            scope.launch {
                WallpaperWorker.invalidateFolderCache()
                val updated = config.copy(folderUri = uri.toString())
                dao.saveConfig(updated)
                if (updated.isEnabled) {
                    WallpaperWorker.scheduleWallpaperWork(context, updated)
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val savedUris = mutableListOf<String>()
                val wallpaperDir = File(context.filesDir, "wallpaper_images").apply { mkdirs() }
                uris.forEach { uri ->
                    try {
                        val destFile = File(wallpaperDir, "wp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        savedUris.add(Uri.fromFile(destFile).toString())
                    } catch (e: Exception) {
                        Log.e("WallpaperTab", "Error saving picked image: ${e.message}", e)
                    }
                }
                if (savedUris.isNotEmpty()) {
                    val existing = config.getImageUriList().toMutableSet()
                    existing.addAll(savedUris)
                    val updated = config.copy(imageUris = existing.joinToString("|"))
                    dao.saveConfig(updated)
                    if (updated.isEnabled) {
                        WallpaperWorker.scheduleWallpaperWork(context, updated)
                    }
                    withContext(Dispatchers.Main) {
                        previewUriString = savedUris.firstOrNull() ?: previewUriString
                        Toast.makeText(context, "Added ${savedUris.size} image(s)", Toast.LENGTH_SHORT).show()
                    }
                }
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

    val hasAnyWallpapers = !config.folderUri.isNullOrEmpty() || individualImages.isNotEmpty()

    val align = when (config.cropAlignment.uppercase()) {
        "LEFT" -> Alignment.CenterStart
        "RIGHT" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("static_wallpaper_screen")
    ) {
        // LAYER 1: Full Background Preview (No separate preview card)
        if (previewUriString != null) {
            AsyncImage(
                model = Uri.parse(previewUriString),
                contentDescription = "Static Wallpaper Background Preview",
                contentScale = ContentScale.Crop,
                alignment = align,
                colorFilter = if (config.applyGrayscale) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentBlurRadius > 0) Modifier.blur(currentBlurRadius.dp) else Modifier
                    )
            )
        } else {
            // Elegant gradient mesh preview when no image selected
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0D1B2A),
                                Color(0xFF1B263B),
                                Color(0xFF2C3E50)
                            )
                        )
                    )
                    .then(
                        if (currentBlurRadius > 0) Modifier.blur(currentBlurRadius.dp) else Modifier
                    )
            )
        }

        // Top & Bottom Gradient Scrims if enabled
        if (config.applyGradient) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                        )
                    )
            )
        }

        // Subtle dark ambient scrim for high-contrast readable options with 90% transparent cards
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // LAYER 2: UI Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 58.dp)
        ) {
            // Top Bar with Screen Type Selector, Eye Toggle, and Apply Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Target Screen Pill: Home vs Lock
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            onClick = { screenType = "HOME" },
                            shape = RoundedCornerShape(10.dp),
                            color = if (screenType == "HOME") Color(0xFF1976D2) else Color.Transparent
                        ) {
                            Text(
                                "Home Screen",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 11.sp,
                                fontWeight = if (screenType == "HOME") FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                        Surface(
                            onClick = { screenType = "LOCK" },
                            shape = RoundedCornerShape(10.dp),
                            color = if (screenType == "LOCK") Color(0xFF1976D2) else Color.Transparent
                        ) {
                            Text(
                                "Lock Screen",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 11.sp,
                                fontWeight = if (screenType == "LOCK") FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    }

                    // Action buttons: Eye Toggle + Apply
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showControls = !showControls },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (showControls) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showControls) "Hide options" else "Show options",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val currentCfg = config.copy(blurRadius = currentBlurRadius)
                                    dao.saveConfig(currentCfg)
                                    val success = if (previewUriString != null) {
                                        WallpaperWorker.applySpecificUriWallpaper(context, currentCfg, Uri.parse(previewUriString))
                                    } else {
                                        WallpaperWorker.applyWallpaperForConfig(context, currentCfg)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(context, "$screenType wallpaper applied successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please add wallpaper images first to apply.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("btn_top_apply_static")
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showControls) {
                // Scrollable 90% transparent cards
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp)
                ) {
                    // CARD 1: Add Wallpaper Images
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_add_images"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = Color(0xFF64B5F6)
                                        )
                                        Text(
                                            text = "Add Wallpaper Images",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    if (individualImages.isNotEmpty()) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.20f)
                                        ) {
                                            Text(
                                                text = "${individualImages.size} added",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (individualImages.isEmpty())
                                        "No individual images added yet. Pick photos from your gallery to rotate as wallpapers."
                                    else
                                        "${individualImages.size} image(s) saved for fast wallpaper rotation. Tap any thumbnail to preview it as the background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                if (individualImages.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(individualImages, key = { it }) { uriStr ->
                                            val isSelected = uriStr == previewUriString
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 72.dp, height = 100.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        previewUriString = uriStr
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = Uri.parse(uriStr),
                                                    contentDescription = "Wallpaper thumbnail",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                if (isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(bottomStart = 8.dp),
                                                        color = Color(0xFF1976D2),
                                                        modifier = Modifier.align(Alignment.TopEnd)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "Active preview",
                                                            tint = Color.White,
                                                            modifier = Modifier.padding(3.dp).size(14.dp)
                                                        )
                                                    }
                                                }

                                                // Delete badge
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.70f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .clickable {
                                                            scope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val uri = Uri.parse(uriStr)
                                                                    if (uri.scheme == "file") {
                                                                        File(uri.path ?: "").delete()
                                                                    }
                                                                } catch (_: Exception) {}
                                                                val remaining = individualImages.filter { it != uriStr }
                                                                val updated = config.copy(
                                                                    imageUris = if (remaining.isEmpty()) null else remaining.joinToString("|")
                                                                )
                                                                dao.saveConfig(updated)
                                                                if (updated.isEnabled) {
                                                                    WallpaperWorker.scheduleWallpaperWork(context, updated)
                                                                }
                                                            }
                                                        }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove image",
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            imagePickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.weight(1f).testTag("btn_pick_images"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (individualImages.isEmpty()) "Add Images" else "Add More Images")
                                    }

                                    if (individualImages.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    individualImages.forEach { uriStr ->
                                                        try {
                                                            val uri = Uri.parse(uriStr)
                                                            if (uri.scheme == "file") {
                                                                File(uri.path ?: "").delete()
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                    val updated = config.copy(imageUris = null)
                                                    dao.saveConfig(updated)
                                                    if (updated.isEnabled) {
                                                        WallpaperWorker.scheduleWallpaperWork(context, updated)
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CARD 2: Wallpaper Folder Selection
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_folder_selection"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6)
                                    )
                                    Text("Wallpaper Folder (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Text(
                                    text = config.folderUri ?: "No folder selected. You can add individual images above or choose an entire folder of pictures.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (config.folderUri == null) Color.White.copy(alpha = 0.75f) else Color(0xFF90CAF9)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { folderPickerLauncher.launch(null) },
                                        modifier = Modifier.weight(1f).testTag("btn_select_folder"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.20f))
                                    ) {
                                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (config.folderUri == null) "Select Folder" else "Change Folder", color = Color.White)
                                    }

                                    if (config.folderUri != null) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    WallpaperWorker.invalidateFolderCache()
                                                    val updated = config.copy(folderUri = null)
                                                    dao.saveConfig(updated)
                                                    if (updated.isEnabled) {
                                                        WallpaperWorker.scheduleWallpaperWork(context, updated)
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CARD 3: Visual Effects & Blur
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_wallpaper_effects"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = Color(0xFF64B5F6))
                                    Text("Visual Effects & Blur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text(
                                    text = "Effects are reflected live on the background preview and applied immediately to current and scheduled auto-wallpapers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                // Blur Slider & Presets
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.BlurOn, contentDescription = null, tint = Color(0xFF90CAF9), modifier = Modifier.size(18.dp))
                                            Text("Wallpaper Blur Effect", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (currentBlurRadius > 0) Color(0xFF1976D2) else Color.White.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (currentBlurRadius == 0) "Off" else "$currentBlurRadius dp",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Slider(
                                        value = currentBlurRadius.toFloat(),
                                        onValueChange = { value ->
                                            currentBlurRadius = value.toInt()
                                        },
                                        onValueChangeFinished = {
                                            scope.launch {
                                                dao.saveConfig(config.copy(blurRadius = currentBlurRadius))
                                            }
                                        },
                                        valueRange = 0f..30f,
                                        steps = 29,
                                        modifier = Modifier.fillMaxWidth().testTag("slider_wallpaper_blur")
                                    )

                                    // Quick Preset Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val blurPresets = listOf(0 to "Off", 6 to "Soft", 14 to "Medium", 24 to "Heavy")
                                        blurPresets.forEach { (radius, label) ->
                                            val isSelected = currentBlurRadius == radius
                                            Surface(
                                                modifier = Modifier.weight(1f).clickable {
                                                    currentBlurRadius = radius
                                                    scope.launch {
                                                        dao.saveConfig(config.copy(blurRadius = radius))
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.10f),
                                                border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.20f))
                                            ) {
                                                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                                    Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                // Corner Gradient
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Corner Gradient", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color.White)
                                        Text("Adds a dark gradient to top and bottom for readability", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
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

                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                // Black & White Filter
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Black & White Filter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color.White)
                                        Text("Applies a grayscale effect to the wallpaper", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
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

                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                // Wallpaper Set Area / Crop Alignment (Left, Middle, Right)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Wallpaper Display Area", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color.White)
                                    Text("Choose which part of wide images is centered on your display:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))

                                    val alignments = listOf(
                                        Triple("LEFT", "Left Side", Icons.Default.AlignHorizontalLeft),
                                        Triple("CENTER", "Middle", Icons.Default.AlignHorizontalCenter),
                                        Triple("RIGHT", "Right Side", Icons.Default.AlignHorizontalRight)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        alignments.forEach { (key, label, icon) ->
                                            val isSelected = config.cropAlignment.equals(key, ignoreCase = true)
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        scope.launch {
                                                            val updated = config.copy(cropAlignment = key)
                                                            dao.saveConfig(updated)
                                                        }
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.10f),
                                                border = BorderStroke(
                                                    1.5.dp,
                                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.20f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                // Scrollable Wallpaper
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Scrollable Wallpaper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color.White)
                                        Text("Expands wallpaper so it pans smoothly when swiping across home screen pages", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                                    }
                                    Switch(
                                        checked = config.isScrollable,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                val updated = config.copy(isScrollable = checked)
                                                dao.saveConfig(updated)
                                            }
                                        },
                                        modifier = Modifier.testTag("switch_scrollable_wallpaper")
                                    )
                                }
                            }
                        }
                    }

                    // CARD 4: Instant Wallpaper Change
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_instant_switch"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF64B5F6))
                                    Text("Instant Wallpaper Change", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text(
                                    text = "Changes wallpaper immediately in milliseconds. Two Quick Settings tiles are available in your status bar:\n• 'Wallpaper: Images' for selected photos\n• 'Wallpaper: Folder' for folder images\nBoth tiles work instantly even when wallpaper changer is turned off!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val success = WallpaperWorker.applyNextWallpaperNow(context, screenType, "IMAGES_ONLY")
                                                withContext(Dispatchers.Main) {
                                                    if (success) {
                                                        Toast.makeText(context, "$screenType wallpaper changed from images!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Please add images above first.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("btn_next_images"),
                                        enabled = individualImages.isNotEmpty(),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.18f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("From Images")
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val success = WallpaperWorker.applyNextWallpaperNow(context, screenType, "FOLDER_ONLY")
                                                withContext(Dispatchers.Main) {
                                                    if (success) {
                                                        Toast.makeText(context, "$screenType wallpaper changed from folder!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Please select a folder above first.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("btn_next_folder"),
                                        enabled = !config.folderUri.isNullOrEmpty(),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.18f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("From Folder")
                                    }
                                }
                            }
                        }
                    }

                    // CARD 5: Automatic Rotation Schedule Settings
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_schedule_settings"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF64B5F6))
                                        Text("Enable Wallpaper Changer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Switch(
                                        checked = config.isEnabled,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                val updated = config.copy(isEnabled = checked)
                                                dao.saveConfig(updated)
                                                WallpaperWorker.scheduleWallpaperWork(context, updated)
                                            }
                                        },
                                        enabled = hasAnyWallpapers,
                                        modifier = Modifier.testTag("switch_enable_auto_wallpaper")
                                    )
                                }

                                Text(
                                    text = "Automatically cycle wallpapers in the background on interval or schedule",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                if (config.isEnabled) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Update Mode: ", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(if (config.useSchedule) "Exact Time" else "Interval", fontSize = 13.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                                            Switch(
                                                checked = config.useSchedule,
                                                onCheckedChange = { useSchedule ->
                                                    scope.launch {
                                                        val updated = config.copy(useSchedule = useSchedule)
                                                        dao.saveConfig(updated)
                                                        WallpaperWorker.scheduleWallpaperWork(context, updated)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    if (!config.useSchedule) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Change Interval", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                                                Text("${config.intervalMinutes} minutes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                                            }
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
                                            Column {
                                                Text("Scheduled Time of Day", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                                                Text(config.scheduledTime ?: "Not Set", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                                            }
                                            OutlinedButton(
                                                onClick = { timePickerDialog.show() },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                                            ) {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Set Time")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Apply Button at bottom of cards
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    val currentCfg = config.copy(blurRadius = currentBlurRadius)
                                    dao.saveConfig(currentCfg)
                                    val success = if (previewUriString != null) {
                                        WallpaperWorker.applySpecificUriWallpaper(context, currentCfg, Uri.parse(previewUriString))
                                    } else {
                                        WallpaperWorker.applyWallpaperForConfig(context, currentCfg)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(context, "$screenType wallpaper applied successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please add wallpaper images first to apply.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("apply_static_wallpaper_bottom_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply to $screenType Wallpaper", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // When controls are hidden: clean immersive wallpaper preview with clock overlay and bottom unhide pill
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.shadow(8.dp)
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.shadow(4.dp)
                        )
                    }

                    Surface(
                        onClick = { showControls = true },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .shadow(8.dp, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Tap to Show Options", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenPreview) {
        FullscreenAutoWallpaperDialog(
            screenType = screenType,
            config = config.copy(blurRadius = currentBlurRadius),
            previewUriString = previewUriString,
            onDismiss = { showFullscreenPreview = false },
            onUpdateBlur = { newBlur ->
                currentBlurRadius = newBlur
                scope.launch {
                    dao.saveConfig(config.copy(blurRadius = newBlur))
                }
            },
            onApply = { appliedBlur, scale, offX, offY ->
                currentBlurRadius = appliedBlur
                scope.launch {
                    val updated = config.copy(blurRadius = appliedBlur)
                    dao.saveConfig(updated)
                    val success = if (previewUriString != null) {
                        WallpaperWorker.applySpecificUriWallpaper(context, updated, Uri.parse(previewUriString), scale, offX, offY)
                    } else {
                        WallpaperWorker.applyWallpaperForConfig(context, updated)
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "$screenType wallpaper applied successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please add wallpaper images first.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}

/**
 * Fullscreen Interactive Wallpaper Preview Dialog.
 * Shows true edge-to-edge wallpaper with real-time blur and lock/home system UI toggles.
 */
@Composable
private fun TransparentSystemBarsEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = findDialogWindow(view) ?: (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {}
    }
}

private fun findDialogWindow(view: android.view.View): android.view.Window? {
    var current: android.view.ViewParent? = view.parent
    while (current != null) {
        if (current is androidx.compose.ui.window.DialogWindowProvider) {
            return current.window
        }
        current = current.parent
    }
    return null
}

@Composable
fun FullscreenAutoWallpaperDialog(
    screenType: String,
    config: WallpaperConfigEntity,
    previewUriString: String?,
    onDismiss: () -> Unit,
    onUpdateBlur: (Int) -> Unit,
    onApply: (appliedBlur: Int, scale: Float, offsetX: Float, offsetY: Float) -> Unit
) {
    TransparentSystemBarsEffect()
    // 0 = Lock Screen, 1 = Home Screen, 2 = Clean View
    var previewMode by remember { mutableStateOf(if (screenType == "LOCK") 0 else 1) }
    var currentBlur by remember(config.blurRadius) { mutableIntStateOf(config.blurRadius) }

    // User Interactive Size & Position State
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffsetX by remember { mutableFloatStateOf(0f) }
    var userOffsetY by remember { mutableFloatStateOf(0f) }
    var showSizePosControl by remember { mutableStateOf(false) }

    val align = when (config.cropAlignment.uppercase()) {
        "LEFT" -> Alignment.CenterStart
        "RIGHT" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val transformModifier = Modifier
                .graphicsLayer {
                    scaleX = userScale
                    scaleY = userScale
                    translationX = userOffsetX
                    translationY = userOffsetY
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        userScale = (userScale * zoom).coerceIn(0.5f, 4.0f)
                        userOffsetX += pan.x
                        userOffsetY += pan.y
                    }
                }

            // Fullscreen Wallpaper Image / Fallback
            if (previewUriString != null) {
                AsyncImage(
                    model = Uri.parse(previewUriString),
                    contentDescription = "Fullscreen Wallpaper Preview",
                    contentScale = ContentScale.Crop,
                    alignment = align,
                    colorFilter = if (config.applyGrayscale) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(transformModifier)
                        .then(
                            if (currentBlur > 0) Modifier.blur(currentBlur.dp) else Modifier
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(transformModifier)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0F2027),
                                    Color(0xFF203A43),
                                    Color(0xFF2C5364)
                                )
                            )
                        )
                        .then(
                            if (currentBlur > 0) Modifier.blur(currentBlur.dp) else Modifier
                        )
                )
            }

            // Top & Bottom Gradient Scrims if enabled
            if (config.applyGradient) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
            }

            // Interactive System Mockup Overlays
            when (previewMode) {
                0 -> {
                    // Lock Screen UI Mockup
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Clock & Date
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 40.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.shadow(8.dp)
                            )
                            Text(
                                text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.shadow(4.dp)
                            )
                        }

                        // Bottom Flashlight / Camera shortcuts
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 110.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.FlashlightOn, contentDescription = null, tint = Color.White)
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Home Screen UI Mockup
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 56.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Widget
                        Column(modifier = Modifier.padding(top = 36.dp, start = 8.dp)) {
                            Text(
                                text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.shadow(4.dp)
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) + " • 72°F Mostly Sunny",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.shadow(3.dp)
                            )
                        }

                        // App Icons Grid Mockup
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Search bar mockup
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.White.copy(alpha = 0.28f),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                                    Text("Search apps & web", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                                }
                            }

                            // 4 Mockup App Icons in a row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val apps = listOf("Phone" to Icons.Default.Phone, "Messages" to Icons.Default.Message, "Browser" to Icons.Default.Public, "Settings" to Icons.Default.Settings)
                                apps.forEach { (name, icon) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(50.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = name, tint = Color(0xFF1E293B), modifier = Modifier.size(26.dp))
                                            }
                                        }
                                        Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Clean View: No Mockup Overlays
                }
            }

            // Top Floating Navigation Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 42.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Fullscreen", tint = Color.White)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Size & Position Toggle
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (showSizePosControl) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.clickable { showSizePosControl = !showSizePosControl }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Size & Pos", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Segmented Toggle for Overlay View
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf("Lock", "Home", "Clean")
                            modes.forEachIndexed { index, title ->
                                val isSel = previewMode == index
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier.clickable { previewMode = index }
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Size & Position Adjustment Floating Panel
            if (showSizePosControl) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xF2161622),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 185.dp)
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Size / Scale", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "${(userScale * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = userScale,
                            onValueChange = { userScale = it },
                            valueRange = 0.5f..3.0f,
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pinch to zoom or drag to reposition",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        userOffsetX = 0f
                                        userOffsetY = 0f
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Center", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(
                                    onClick = {
                                        userScale = 1f
                                        userOffsetX = 0f
                                        userOffsetY = 0f
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Reset", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Floating Controls Card: Live Blur slider & Apply Button
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Blur Adjustment Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.BlurOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Adjust Blur Radius", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = if (currentBlur == 0) "Off" else "$currentBlur dp",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }

                    Slider(
                        value = currentBlur.toFloat(),
                        onValueChange = {
                            currentBlur = it.toInt()
                            onUpdateBlur(it.toInt())
                        },
                        valueRange = 0f..30f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )

                    // Apply Button
                    Button(
                        onClick = {
                            onApply(currentBlur, userScale, userOffsetX, userOffsetY)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Apply as $screenType Wallpaper",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
