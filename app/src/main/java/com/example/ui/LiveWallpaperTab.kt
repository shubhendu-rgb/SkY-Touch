package com.example.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.TextureView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.service.wallpaper.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWallpaperTab(
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeConfig by remember { mutableStateOf(LiveWallpaperManager.getActiveConfig(context)) }
    var savedList by remember { mutableStateOf(LiveWallpaperManager.getSavedList(context)) }
    var systemWallpapers by remember { mutableStateOf<List<SystemLiveWallpaperInfo>>(emptyList()) }
    var isLoadingSystem by remember { mutableStateOf(true) }
    var showFullscreenPreview by remember { mutableStateOf(false) }
    var croppingConfig by remember { mutableStateOf<LiveWallpaperConfig?>(null) }

    var selectedEditTab by remember { mutableIntStateOf(0) }
    val editTabs = listOf("Wallpapers & Presets", "Auto Change", "Edit & Crop", "Color Filters")

    val colorMatrixArray = remember(activeConfig.colorFilter) {
        LiveWallpaperManager.getColorMatrix(activeConfig.colorFilter)
    }
    val composeColorFilter = remember(colorMatrixArray) {
        ColorFilter.colorMatrix(ColorMatrix(colorMatrixArray))
    }

    // ImageLoader with GIF support
    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .allowHardware(true)
            .build()
    }

    // Load system wallpapers in background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = LiveWallpaperManager.querySystemLiveWallpapers(context)
            withContext(Dispatchers.Main) {
                systemWallpapers = list
                isLoadingSystem = false
            }
        }
    }

    // Picker for GIF images
    val gifPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: ""
                val isRealVideo = mime.startsWith("video/")
                val file = withContext(Dispatchers.IO) {
                    LiveWallpaperManager.importMediaFile(context, uri, isVideo = isRealVideo)
                }
                if (file != null) {
                    val actualType = if (isRealVideo) LiveMediaType.VIDEO else LiveMediaType.GIF
                    val namePrefix = if (isRealVideo) "Custom Video" else "Custom GIF"
                    val updated = activeConfig.copy(
                        id = UUID.randomUUID().toString(),
                        title = "$namePrefix ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}",
                        mediaType = actualType,
                        mediaPath = file.absolutePath,
                        scaleFactor = 1.0f,
                        offsetX = 0f,
                        offsetY = 0f,
                        dateAdded = System.currentTimeMillis()
                    )
                    activeConfig = updated
                    LiveWallpaperManager.saveActiveConfig(context, updated)
                    savedList = LiveWallpaperManager.getSavedList(context)
                    Toast.makeText(context, "$namePrefix imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to import media file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Picker for Small Videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: ""
                val isRealVideo = mime.startsWith("video/") || !mime.contains("gif")
                val file = withContext(Dispatchers.IO) {
                    LiveWallpaperManager.importMediaFile(context, uri, isVideo = isRealVideo)
                }
                if (file != null) {
                    val actualType = if (isRealVideo) LiveMediaType.VIDEO else LiveMediaType.GIF
                    val namePrefix = if (isRealVideo) "Custom Video" else "Custom GIF"
                    val updated = activeConfig.copy(
                        id = UUID.randomUUID().toString(),
                        title = "$namePrefix ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}",
                        mediaType = actualType,
                        mediaPath = file.absolutePath,
                        scaleFactor = 1.0f,
                        offsetX = 0f,
                        offsetY = 0f,
                        dateAdded = System.currentTimeMillis()
                    )
                    activeConfig = updated
                    LiveWallpaperManager.saveActiveConfig(context, updated)
                    savedList = LiveWallpaperManager.getSavedList(context)
                    Toast.makeText(context, "$namePrefix imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to import media file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_wallpaper_screen")
    ) {
        // --- 1. FULLSCREEN REAL-TIME LIVE WALLPAPER BACKGROUND PREVIEW ---
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeConfig.mediaType) {
                LiveMediaType.VIDEO -> {
                    val path = activeConfig.mediaPath
                    if (!path.isNullOrEmpty() && File(path).exists()) {
                        VideoTexturePreview(
                            videoPath = path,
                            isMuted = activeConfig.isMuted,
                            playbackSpeed = activeConfig.playbackSpeed,
                            colorFilter = composeColorFilter,
                            contentScale = when (activeConfig.cropMode) {
                                LiveCropMode.FILL -> ContentScale.Crop
                                LiveCropMode.FIT -> ContentScale.Fit
                                LiveCropMode.STRETCH -> ContentScale.FillBounds
                                else -> ContentScale.Crop
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = activeConfig.rotationDegrees.toFloat()
                                    scaleX = activeConfig.scaleFactor
                                    scaleY = activeConfig.scaleFactor
                                    translationX = activeConfig.offsetX
                                    translationY = activeConfig.offsetY
                                }
                        )
                    } else {
                        PresetCanvasPreview(
                            presetType = LiveMediaType.PRESET_AURORA,
                            colorMatrixArray = colorMatrixArray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                LiveMediaType.GIF -> {
                    val path = activeConfig.mediaPath
                    if (!path.isNullOrEmpty() && File(path).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(path))
                                .build(),
                            imageLoader = gifImageLoader,
                            contentDescription = "Live Wallpaper Background Preview",
                            colorFilter = composeColorFilter,
                            contentScale = when (activeConfig.cropMode) {
                                LiveCropMode.FILL -> ContentScale.Crop
                                LiveCropMode.FIT -> ContentScale.Fit
                                LiveCropMode.STRETCH -> ContentScale.FillBounds
                                else -> ContentScale.Crop
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = activeConfig.rotationDegrees.toFloat()
                                    scaleX = activeConfig.scaleFactor
                                    scaleY = activeConfig.scaleFactor
                                    translationX = activeConfig.offsetX
                                    translationY = activeConfig.offsetY
                                }
                        )
                    } else {
                        PresetCanvasPreview(
                            presetType = LiveMediaType.PRESET_AURORA,
                            colorMatrixArray = colorMatrixArray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                LiveMediaType.PRESET_AURORA,
                LiveMediaType.PRESET_NEBULA,
                LiveMediaType.PRESET_MATRIX -> {
                    PresetCanvasPreview(
                        presetType = activeConfig.mediaType,
                        colorMatrixArray = colorMatrixArray,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // --- 2. AMBIENT SCRIM OVERLAY ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.60f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // --- 3. FOREGROUND OVERLAY UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Glass Top Navigation Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.50f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Live Wallpaper Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            val isOurLiveActive = remember(activeConfig) {
                                LiveWallpaperManager.isOurLiveWallpaperActive(context)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isOurLiveActive) Color(0xFF00E676) else Color(0xFFFFB74D),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (isOurLiveActive) "Active on Phone • ${activeConfig.title}" else "Preview: ${activeConfig.title}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { showFullscreenPreview = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen preview",
                                tint = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                LiveWallpaperManager.saveActiveConfig(context, activeConfig)
                                LiveWallpaperManager.applyLiveWallpaper(context)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            // Scrollable Options Content
            LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp)
                ) {
                    // Category Tab Row (90% transparent glass)
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                        ) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = selectedEditTab,
                                edgePadding = 4.dp,
                                containerColor = Color.Transparent,
                                contentColor = Color.White,
                                divider = {}
                            ) {
                                editTabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedEditTab == index,
                                        onClick = { selectedEditTab = index },
                                        text = {
                                            Text(
                                                title,
                                                fontWeight = if (selectedEditTab == index) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (selectedEditTab == index) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.75f)
                                            )
                                        },
                                        modifier = Modifier.testTag("live_tab_$index")
                                    )
                                }
                            }
                        }
                    }

                    // Content based on selected tab
                    when (selectedEditTab) {
                        0 -> {
                            // Wallpapers & Presets: Unified 3 Defaults + System Wallpapers + Custom Media
                            item {
                                UnifiedWallpapersCard(
                                    config = activeConfig,
                                    isLoadingSystem = isLoadingSystem,
                                    systemWallpapers = systemWallpapers,
                                    onSelectPreset = { presetType, presetTitle ->
                                        val updated = activeConfig.copy(
                                            id = UUID.randomUUID().toString(),
                                            title = presetTitle,
                                            mediaType = presetType,
                                            mediaPath = null,
                                            dateAdded = System.currentTimeMillis()
                                        )
                                        activeConfig = updated
                                        LiveWallpaperManager.saveActiveConfig(context, updated)
                                        savedList = LiveWallpaperManager.getSavedList(context)
                                        Toast.makeText(context, "$presetTitle selected!", Toast.LENGTH_SHORT).show()
                                    },
                                    onApplySystem = { wp ->
                                        LiveWallpaperManager.applyLiveWallpaper(
                                            context = context,
                                            packageName = wp.packageName,
                                            serviceName = wp.serviceName
                                        )
                                    },
                                    onOpenSystemChooser = {
                                        LiveWallpaperManager.applyLiveWallpaper(context)
                                    },
                                    onPickGif = {
                                        gifPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onPickVideo = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    savedList = savedList,
                                    onSelectSaved = { saved ->
                                        activeConfig = saved
                                        LiveWallpaperManager.saveActiveConfig(context, saved)
                                        LiveWallpaperManager.applyLiveWallpaper(context)
                                    },
                                    onDeleteSaved = { savedId ->
                                        LiveWallpaperManager.removeFromSavedList(context, savedId)
                                        savedList = LiveWallpaperManager.getSavedList(context)
                                        Toast.makeText(context, "Wallpaper removed", Toast.LENGTH_SHORT).show()
                                    },
                                    onRotate = { item ->
                                        val newRot = (item.rotationDegrees + 90) % 360
                                        val updated = item.copy(rotationDegrees = newRot)
                                        LiveWallpaperManager.addToSavedList(context, updated)
                                        if (activeConfig.id == updated.id || (activeConfig.mediaPath != null && activeConfig.mediaPath == updated.mediaPath)) {
                                            activeConfig = updated
                                            LiveWallpaperManager.saveActiveConfig(context, updated)
                                        }
                                        savedList = LiveWallpaperManager.getSavedList(context)
                                        Toast.makeText(context, "Rotated to ${newRot}°", Toast.LENGTH_SHORT).show()
                                    },
                                    onCrop = { item ->
                                        croppingConfig = item
                                    }
                                )
                            }
                        }
                        1 -> {
                            // Auto Change Live Wallpaper
                            item {
                                AutoChangeLiveWallpaperCard(
                                    context = context,
                                    onActiveConfigChanged = { updated ->
                                        activeConfig = updated
                                    }
                                )
                            }
                        }
                        2 -> {
                            // Edit & Crop
                            item {
                                EditAndCropCard(
                                    config = activeConfig,
                                    onConfigChange = { updated ->
                                        activeConfig = updated
                                        LiveWallpaperManager.saveActiveConfig(context, updated)
                                    },
                                    onOpenFullscreenCrop = {
                                        croppingConfig = activeConfig
                                    }
                                )
                            }
                        }
                        3 -> {
                            // Color Filters
                            item {
                                ColorFiltersCard(
                                    config = activeConfig,
                                    onFilterSelect = { filter ->
                                        val updated = activeConfig.copy(colorFilter = filter)
                                        activeConfig = updated
                                        LiveWallpaperManager.saveActiveConfig(context, updated)
                                    }
                                )
                            }
                        }
                    }

                    // Floating Apply Button
                    item {
                        Button(
                            onClick = {
                                LiveWallpaperManager.saveActiveConfig(context, activeConfig)
                                LiveWallpaperManager.applyLiveWallpaper(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("apply_live_wallpaper_bottom_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply as Live Wallpaper", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
        }

        // Fullscreen Interactive Crop & Pinch Screen (Hides status bar and navigation bar completely)
        if (croppingConfig != null) {
            FullscreenCropPinchScreen(
                config = croppingConfig!!,
                imageLoader = gifImageLoader,
                onDismiss = { croppingConfig = null },
                onSave = { updated ->
                    LiveWallpaperManager.addToSavedList(context, updated)
                    if (activeConfig.id == updated.id || (activeConfig.mediaPath != null && activeConfig.mediaPath == updated.mediaPath)) {
                        activeConfig = updated
                        LiveWallpaperManager.saveActiveConfig(context, updated)
                    }
                    savedList = LiveWallpaperManager.getSavedList(context)
                    croppingConfig = null
                    Toast.makeText(context, "Wallpaper size & position saved!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showFullscreenPreview) {
            FullscreenLiveWallpaperDialog(
                config = activeConfig,
                imageLoader = gifImageLoader,
                onDismiss = { showFullscreenPreview = false },
                onApply = { updatedConfig ->
                    activeConfig = updatedConfig
                    LiveWallpaperManager.saveActiveConfig(context, updatedConfig)
                    LiveWallpaperManager.applyLiveWallpaper(context)
                    showFullscreenPreview = false
                    Toast.makeText(context, "Tap 'Set Wallpaper' to confirm!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/**
 * Realistic Phone Mockup Preview that runs the GIF, Video, or Procedural Canvas
 * with real-time Rotation, Crop, and Color Filter!
 */
@Composable
fun LiveWallpaperPreviewCard(
    config: LiveWallpaperConfig,
    imageLoader: ImageLoader,
    onRotate90: () -> Unit,
    onOpenFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val colorMatrixArray = remember(config.colorFilter) {
        LiveWallpaperManager.getColorMatrix(config.colorFilter)
    }
    val composeColorFilter = remember(colorMatrixArray) {
        ColorFilter.colorMatrix(ColorMatrix(colorMatrixArray))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_wallpaper_preview_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with status pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = config.mediaType.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (config.rotationDegrees > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "${config.rotationDegrees}°",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (config.scaleFactor != 1.0f || config.offsetX != 0f || config.offsetY != 0f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Scale: ${(config.scaleFactor * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (config.blurRadius > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Blur: ${config.blurRadius}dp",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (config.colorFilter != LiveColorFilter.NONE) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = config.colorFilter.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onRotate90) {
                    Icon(
                        Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Rotate 90 degrees",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phone Mockup Frame with rotation-aware dimensions
            val frameW = 220.dp
            val frameH = 390.dp
            val isSideways = config.rotationDegrees == 90 || config.rotationDegrees == 270
            val childW = if (isSideways) frameH else frameW
            val childH = if (isSideways) frameW else frameH

            Box(
                modifier = Modifier
                    .width(frameW)
                    .height(frameH)
                    .shadow(12.dp, RoundedCornerShape(32.dp))
                    .border(4.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black)
                    .clickable { onOpenFullscreen() },
                contentAlignment = Alignment.Center
            ) {
                // Media Content inside phone frame
                val scale = when (config.cropMode) {
                    LiveCropMode.FILL -> ContentScale.Crop
                    LiveCropMode.FIT -> ContentScale.Fit
                    LiveCropMode.LEFT -> ContentScale.FillHeight
                    LiveCropMode.RIGHT -> ContentScale.FillHeight
                    LiveCropMode.STRETCH -> ContentScale.FillBounds
                }

                Box(
                    modifier = Modifier
                        .requiredSize(childW, childH)
                        .graphicsLayer {
                            rotationZ = config.rotationDegrees.toFloat()
                            scaleX = config.scaleFactor
                            scaleY = config.scaleFactor
                            translationX = config.offsetX * 0.22f
                            translationY = config.offsetY * 0.22f
                        }
                        .then(
                            if (config.blurRadius > 0) Modifier.blur(config.blurRadius.dp) else Modifier
                        ),
                    contentAlignment = when (config.cropMode) {
                        LiveCropMode.LEFT -> Alignment.CenterStart
                        LiveCropMode.RIGHT -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ) {
                    when (config.mediaType) {
                        LiveMediaType.VIDEO -> {
                            val path = config.mediaPath
                            if (path != null && File(path).exists()) {
                                VideoTexturePreview(
                                    videoPath = path,
                                    isMuted = config.isMuted,
                                    playbackSpeed = config.playbackSpeed,
                                    colorFilter = composeColorFilter,
                                    contentScale = scale,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                PresetCanvasPreview(
                                    presetType = LiveMediaType.PRESET_AURORA,
                                    colorMatrixArray = colorMatrixArray,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        LiveMediaType.GIF -> {
                            val path = config.mediaPath
                            if (path != null && File(path).exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(path))
                                        .size(coil.size.Size.ORIGINAL)
                                        .crossfade(true)
                                        .build(),
                                    imageLoader = imageLoader,
                                    contentDescription = "Live GIF Preview",
                                    contentScale = scale,
                                    colorFilter = composeColorFilter,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                PresetCanvasPreview(
                                    presetType = LiveMediaType.PRESET_AURORA,
                                    colorMatrixArray = colorMatrixArray,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        LiveMediaType.PRESET_AURORA,
                        LiveMediaType.PRESET_NEBULA,
                        LiveMediaType.PRESET_MATRIX -> {
                            PresetCanvasPreview(
                                presetType = config.mediaType,
                                colorMatrixArray = colorMatrixArray,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Top Notch & Speaker Cutout overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .width(70.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Black.copy(alpha = 0.9f))
                )

                // Bottom Home Bar Pill overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .width(60.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.6f))
                )

                // Fullscreen Tap Affordance Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("Tap for Fullscreen", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = config.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Mode: ${config.cropMode.name} • Filter: ${config.colorFilter.displayName}" +
                        if (config.blurRadius > 0) " • Blur: ${config.blurRadius}dp" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * AndroidView TextureView playback for video preview.
 */
@Composable
fun VideoTexturePreview(
    videoPath: String,
    isMuted: Boolean,
    playbackSpeed: Float,
    colorFilter: ColorFilter?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    var mp: MediaPlayer? = null
                    var videoW = 0
                    var videoH = 0
                    var videoRot = 0

                    private fun adjustTransform(w: Int, h: Int) {
                        if (w <= 0 || h <= 0 || videoW <= 0 || videoH <= 0) return
                        val matrix = android.graphics.Matrix()
                        val isSideways = videoRot == 90 || videoRot == 270
                        val visualW = if (isSideways) videoH else videoW
                        val visualH = if (isSideways) videoW else videoH
                        val sx = w.toFloat() / visualW
                        val sy = h.toFloat() / visualH
                        val scale = if (contentScale == ContentScale.Fit) {
                            minOf(sx, sy)
                        } else {
                            maxOf(sx, sy)
                        }
                        val scaledW = visualW * scale
                        val scaledH = visualH * scale
                        matrix.setScale(scaledW / w, scaledH / h, w / 2f, h / 2f)
                        setTransform(matrix)
                    }

                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        try {
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(videoPath)
                                videoRot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                                retriever.release()
                            } catch (_: Exception) {}

                            val player = MediaPlayer().apply {
                                setDataSource(videoPath)
                                setSurface(android.view.Surface(surface))
                                isLooping = true
                                val vol = if (isMuted) 0f else 0.5f
                                setVolume(vol, vol)
                                setOnVideoSizeChangedListener { _, vw, vh ->
                                    videoW = vw
                                    videoH = vh
                                    adjustTransform(width, height)
                                }
                                setOnErrorListener { _, what, extra ->
                                    android.util.Log.w("VideoTexturePreview", "MediaPlayer preview error $what, $extra")
                                    true
                                }
                                setOnPreparedListener {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kotlin.math.abs(playbackSpeed - 1.0f) >= 0.05f) {
                                        try {
                                            val params = it.playbackParams.apply {
                                                speed = playbackSpeed
                                                pitch = 1.0f
                                                audioFallbackMode = android.media.PlaybackParams.AUDIO_FALLBACK_MODE_MUTE
                                            }
                                            it.playbackParams = params
                                        } catch (_: Exception) {}
                                    }
                                    it.start()
                                    if (it.videoWidth > 0 && it.videoHeight > 0) {
                                        videoW = it.videoWidth
                                        videoH = it.videoHeight
                                        try {
                                            surface.setDefaultBufferSize(videoW, videoH)
                                        } catch (_: Exception) {}
                                        adjustTransform(width, height)
                                    }
                                }
                                prepareAsync()
                            }
                            mp = player
                            setTag(player)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoTexturePreview", "Error creating MediaPlayer: ${e.message}", e)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        adjustTransform(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        try {
                            setTag(null)
                            mp?.stop()
                            mp?.release()
                            mp = null
                        } catch (_: Exception) {}
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
        },
        update = { view ->
            val player = view.getTag() as? MediaPlayer
            if (player != null) {
                val vol = if (isMuted) 0f else 0.5f
                player.setVolume(vol, vol)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val currentSpeed = player.playbackParams.speed
                        if (kotlin.math.abs(currentSpeed - playbackSpeed) > 0.05f) {
                            val params = player.playbackParams.apply {
                                speed = playbackSpeed
                                pitch = 1.0f
                                audioFallbackMode = android.media.PlaybackParams.AUDIO_FALLBACK_MODE_MUTE
                            }
                            player.playbackParams = params
                        }
                    } catch (_: Exception) {}
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Procedural interactive canvas preview for built-in presets.
 */
@Composable
fun PresetCanvasPreview(
    presetType: LiveMediaType,
    colorMatrixArray: FloatArray,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableFloatStateOf(0f) }

    // Throttled to ~30fps (time-based so the speed is unchanged); halves the GPU/CPU work
    // that was competing with scrolling and popups.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = now - last
                if (dt >= 33_000_000L) {
                    tick += (dt / 1_000_000_000f) * 3f
                    last = now
                }
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (presetType) {
            LiveMediaType.PRESET_AURORA -> {
                drawRect(Color(0xFF0A0F1E))
                for (i in 0 until 4) {
                    val path = androidx.compose.ui.graphics.Path()
                    val baseY = h * (0.35f + i * 0.15f)
                    path.moveTo(0f, h)
                    path.lineTo(0f, baseY)
                    var x = 0f
                    val step = w / 16f
                    while (x <= w + step) {
                        val nx = x / w
                        val y = baseY + sin(nx * 4f + tick + i * 1.2f) * 40f + cos(nx * 6f - tick * 0.7f) * 20f
                        path.lineTo(x, y)
                        x += step
                    }
                    path.lineTo(w, h)
                    path.close()

                    val color = when (i % 4) {
                        0 -> Color(0xFF1EDEAA).copy(alpha = 0.6f)
                        1 -> Color(0xFF1E8CFF).copy(alpha = 0.5f)
                        2 -> Color(0xFFB432FF).copy(alpha = 0.45f)
                        else -> Color(0xFF00E6C8).copy(alpha = 0.55f)
                    }
                    drawPath(
                        path,
                        brush = Brush.verticalGradient(
                            listOf(color, Color.Transparent),
                            startY = baseY - 40f,
                            endY = baseY + 120f
                        )
                    )
                }
            }
            LiveMediaType.PRESET_NEBULA -> {
                drawRect(Color(0xFF080614))
                val cx = w / 2f
                val cy = h / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDC32B4).copy(alpha = 0.65f), Color(0xFF3214A0).copy(alpha = 0.35f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(cx + sin(tick * 0.8f) * 30f, cy + cos(tick * 0.6f) * 40f),
                        radius = w * 0.8f
                    ),
                    radius = w * 0.8f,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF14B4FF).copy(alpha = 0.6f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(cx - cos(tick * 0.5f) * 40f, cy - sin(tick * 0.7f) * 35f),
                        radius = w * 0.7f
                    ),
                    radius = w * 0.7f,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
            }
            LiveMediaType.PRESET_MATRIX -> {
                drawRect(Color(0xFF050C08))
                val cols = 8
                for (col in 0 until cols) {
                    val colX = col * (w / cols) + 10f
                    val headY = ((tick * 40f + col * 50f) % (h + 100f)) - 50f
                    for (row in 0 until 10) {
                        val charY = headY - row * 24f
                        if (charY in 0f..h) {
                            val alpha = ((10 - row) * 0.09f).coerceIn(0.1f, 1f)
                            drawCircle(
                                color = if (row == 0) Color.White else Color(0xFF00FF78).copy(alpha = alpha),
                                radius = 4f,
                                center = androidx.compose.ui.geometry.Offset(colX, charY)
                            )
                        }
                    }
                }
            }
            else -> {
                drawRect(Color.DarkGray)
            }
        }
    }
}

/**
 * Tab 1: Edit & Crop (Rotation, Crop Mode, Playback Speed, Mute)
 */
@Composable
fun EditAndCropCard(
    config: LiveWallpaperConfig,
    onConfigChange: (LiveWallpaperConfig) -> Unit,
    onOpenFullscreenCrop: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_live_edit_crop"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onOpenFullscreenCrop != null) {
                Button(
                    onClick = onOpenFullscreenCrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_interactive_crop_pinch"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Interactive Fullscreen Crop & Pinch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            }

            // Rotation Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rotate Wallpaper", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text("Select display angle or tap to rotate 90° clockwise.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rotations = listOf(0, 90, 180, 270)
                    rotations.forEach { rot ->
                        val isSelected = config.rotationDegrees == rot
                        OutlinedButton(
                            onClick = { onConfigChange(config.copy(rotationDegrees = rot)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text("${rot}°", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Crop Mode Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crop & Scaling Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text("Choose how wide or tall media is fitted onto your screen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val cropModes = listOf(
                    LiveCropMode.FILL to "Fill (Center Crop)",
                    LiveCropMode.FIT to "Fit (Letterbox)",
                    LiveCropMode.LEFT to "Left Align",
                    LiveCropMode.RIGHT to "Right Align",
                    LiveCropMode.STRETCH to "Stretch to Screen"
                )

                cropModes.forEach { (mode, label) ->
                    val isSelected = config.cropMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onConfigChange(config.copy(cropMode = mode)) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onConfigChange(config.copy(cropMode = mode)) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Playback Speed Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Playback Speed", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { speed ->
                        val isSelected = config.playbackSpeed == speed
                        OutlinedButton(
                            onClick = { onConfigChange(config.copy(playbackSpeed = speed)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("${speed}x", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Blur Effect Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BlurOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wallpaper Blur Effect", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(
                        text = if (config.blurRadius == 0) "Off" else "${config.blurRadius} dp",
                        fontWeight = FontWeight.Bold,
                        color = if (config.blurRadius > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Text("Frosted glass blur effect applied to your live wallpaper in real time.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Slider(
                    value = config.blurRadius.toFloat(),
                    onValueChange = { onConfigChange(config.copy(blurRadius = it.toInt())) },
                    valueRange = 0f..30f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth().testTag("live_wallpaper_blur_slider")
                )

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(0 to "Off", 6 to "Soft", 14 to "Medium", 24 to "Heavy")
                    presets.forEach { (radius, label) ->
                        val isSelected = config.blurRadius == radius
                        OutlinedButton(
                            onClick = { onConfigChange(config.copy(blurRadius = radius)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            // Mute Switch for Video
            if (config.mediaType == LiveMediaType.VIDEO) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mute Video Audio", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Recommended so video audio does not play on home screen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.isMuted,
                        onCheckedChange = { onConfigChange(config.copy(isMuted = it)) }
                    )
                }
            }
        }
    }
}

/**
 * Tab 2: Color Filters (Monochrome, Sepia, Invert, Vivid, Cool, Contrast, Sunset)
 */
@Composable
fun ColorFiltersCard(
    config: LiveWallpaperConfig,
    onFilterSelect: (LiveColorFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_live_color_filters"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Color Filters & Tones", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text("Transform the mood and palette of your live wallpaper in real time.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val filters = LiveColorFilter.values()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    val isSelected = config.colorFilter == filter
                    val swatchColors = when (filter) {
                        LiveColorFilter.NONE -> listOf(Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05), Color(0xFFEA4335))
                        LiveColorFilter.GRAYSCALE -> listOf(Color(0xFFEEEEEE), Color(0xFF999999), Color(0xFF444444), Color(0xFF111111))
                        LiveColorFilter.SEPIA -> listOf(Color(0xFFF4ECD8), Color(0xFFD2B48C), Color(0xFF8B5A2B), Color(0xFF3E2723))
                        LiveColorFilter.INVERT -> listOf(Color(0xFF00FFCC), Color(0xFFFF007F), Color(0xFF222222), Color(0xFFFFFFFF))
                        LiveColorFilter.VIVID -> listOf(Color(0xFFFF0055), Color(0xFFFF7700), Color(0xFF00E5FF), Color(0xFF76FF03))
                        LiveColorFilter.COOL -> listOf(Color(0xFF00F0FF), Color(0xFF0072FF), Color(0xFF001133), Color(0xFFB0E0E6))
                        LiveColorFilter.CONTRAST -> listOf(Color(0xFFFFFFFF), Color(0xFF777777), Color(0xFF000000), Color(0xFFFFCC00))
                        LiveColorFilter.SUNSET -> listOf(Color(0xFFFF512F), Color(0xFFDD2476), Color(0xFFF09819), Color(0xFFFF5E62))
                    }

                    Surface(
                        onClick = { onFilterSelect(filter) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Swatch palette bar
                                Row(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                ) {
                                    swatchColors.forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(color)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = filter.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (filter) {
                                            LiveColorFilter.NONE -> "Natural colors without adjustment"
                                            LiveColorFilter.GRAYSCALE -> "Classic black and white tones"
                                            LiveColorFilter.SEPIA -> "Warm vintage retro photography look"
                                            LiveColorFilter.INVERT -> "Futuristic inverted color tones"
                                            LiveColorFilter.VIVID -> "Deep saturation and punchy contrast"
                                            LiveColorFilter.COOL -> "Glacier cyan and deep blue mood"
                                            LiveColorFilter.CONTRAST -> "High-key dramatic separation"
                                            LiveColorFilter.SUNSET -> "Golden hour warm crimson glow"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Auto Change Live Wallpaper
 */
@Composable
fun AutoChangeLiveWallpaperCard(
    context: Context,
    onActiveConfigChanged: (LiveWallpaperConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEnabled by remember { mutableStateOf(LiveWallpaperManager.isAutoChangeEnabled(context)) }
    var intervalMins by remember { mutableIntStateOf(LiveWallpaperManager.getAutoChangeInterval(context)) }
    var useSchedule by remember { mutableStateOf(LiveWallpaperManager.isAutoChangeUseSchedule(context)) }
    var scheduledTime by remember { mutableStateOf(LiveWallpaperManager.getAutoChangeScheduledTime(context)) }
    var isRandom by remember { mutableStateOf(LiveWallpaperManager.isAutoChangeRandom(context)) }

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val formatted = String.format("%02d:%02d", hourOfDay, minute)
            scheduledTime = formatted
            LiveWallpaperManager.setAutoChangeScheduledTime(context, formatted)
            if (isEnabled) {
                LiveWallpaperWorker.scheduleWork(context)
            }
        },
        9, 0, true
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_live_auto_change"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                    Text("Auto Change Live Wallpaper", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        LiveWallpaperManager.setAutoChangeEnabled(context, checked)
                        LiveWallpaperWorker.scheduleWork(context)
                        Toast.makeText(
                            context,
                            if (checked) "Auto change live wallpaper enabled!" else "Auto change disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.testTag("switch_live_auto_change")
                )
            }

            Text(
                text = "Automatically cycles between built-in dynamic presets, system live wallpapers, and imported videos/GIFs in the background.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            if (isEnabled) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                // Mode switch: Interval vs Exact Schedule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Trigger Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (useSchedule) "Exact Time" else "Interval", fontSize = 13.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                        Switch(
                            checked = useSchedule,
                            onCheckedChange = { sched ->
                                useSchedule = sched
                                LiveWallpaperManager.setAutoChangeUseSchedule(context, sched)
                                LiveWallpaperWorker.scheduleWork(context)
                            }
                        )
                    }
                }

                if (!useSchedule) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Change Interval", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                            Text("$intervalMins minutes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                        }

                        val intervalOptions = listOf(15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h", 360 to "6h", 720 to "12h", 1440 to "24h")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            intervalOptions.forEach { (mins, label) ->
                                val selected = intervalMins == mins
                                Surface(
                                    modifier = Modifier.weight(1f).clickable {
                                        intervalMins = mins
                                        LiveWallpaperManager.setAutoChangeInterval(context, mins)
                                        LiveWallpaperWorker.scheduleWork(context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.20f))
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Scheduled Time of Day", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                            Text(scheduledTime, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
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

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                // Order selector: Sequential vs Random
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shuffle Order", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(if (isRandom) "Random selection each cycle" else "Sequential ordered cycle", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                    Switch(
                        checked = isRandom,
                        onCheckedChange = { rand ->
                            isRandom = rand
                            LiveWallpaperManager.setAutoChangeRandom(context, rand)
                        }
                    )
                }

                // Instant Cycle Test Button
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val next = LiveWallpaperManager.cycleNextLiveWallpaper(context, isRandom = isRandom)
                            onActiveConfigChanged(next)
                            Toast.makeText(context, "Cycled to next live wallpaper: ${next.title}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("btn_cycle_live_wallpaper_now"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change to Next Live Wallpaper Now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Tab 0: Unified Wallpapers & Presets Card
 * Unifies the three default dynamic presets, system live wallpapers, and custom imported media.
 */
@Composable
fun UnifiedWallpapersCard(
    config: LiveWallpaperConfig,
    isLoadingSystem: Boolean,
    systemWallpapers: List<SystemLiveWallpaperInfo>,
    onSelectPreset: (LiveMediaType, String) -> Unit,
    onApplySystem: (SystemLiveWallpaperInfo) -> Unit,
    onOpenSystemChooser: () -> Unit,
    onPickGif: () -> Unit,
    onPickVideo: () -> Unit,
    savedList: List<LiveWallpaperConfig>,
    onSelectSaved: (LiveWallpaperConfig) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onRotate: ((LiveWallpaperConfig) -> Unit)? = null,
    onCrop: ((LiveWallpaperConfig) -> Unit)? = null
) {
    var expandedEditId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Unified Default Live Wallpapers & System Live Wallpapers Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_unified_wallpapers"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                        Text("System & Default Live Wallpapers", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }

                    FilledTonalButton(
                        onClick = onOpenSystemChooser,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("System Chooser", fontSize = 11.sp)
                    }
                }

                Text(
                    text = "Three default dynamic procedural live wallpapers unified with your Android device's installed live wallpapers.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                // Subheader: Three Default Live Wallpapers
                Text("Default Live Wallpapers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF90CAF9))

                val presets = listOf(
                    Triple(LiveMediaType.PRESET_AURORA, "Neon Aurora", "Dynamic flowing green/cyan northern lights & starlight"),
                    Triple(LiveMediaType.PRESET_NEBULA, "Deep Nebula", "Cosmic purple/magenta dust clouds with orbital gravity"),
                    Triple(LiveMediaType.PRESET_MATRIX, "Digital Matrix", "Glowing matrix rain of streaming digital characters")
                )

                presets.forEach { (type, title, desc) ->
                    val isSelected = config.mediaType == type && config.mediaPath.isNullOrEmpty()
                    Surface(
                        onClick = { onSelectPreset(type, title) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1976D2).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Thumbnail badge
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when (type) {
                                        LiveMediaType.PRESET_AURORA -> Color(0xFF00E6C8).copy(alpha = 0.3f)
                                        LiveMediaType.PRESET_NEBULA -> Color(0xFFDC32B4).copy(alpha = 0.3f)
                                        else -> Color(0xFF00FF78).copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(44.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (type) {
                                                LiveMediaType.PRESET_AURORA -> Icons.Default.WbTwilight
                                                LiveMediaType.PRESET_NEBULA -> Icons.Default.BlurOn
                                                else -> Icons.Default.Code
                                            },
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                                }
                            }

                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF64B5F6), modifier = Modifier.size(24.dp))
                            } else {
                                FilledTonalButton(
                                    onClick = { onSelectPreset(type, title) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.20f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Select", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                // Subheader: Device System Live Wallpapers
                Text("Device System Live Wallpapers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF90CAF9))

                if (isLoadingSystem) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                    }
                } else if (systemWallpapers.isEmpty()) {
                    Text(
                        "No additional third-party live wallpapers installed on this device.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    systemWallpapers.forEach { wp ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color.White)
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = wp.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = wp.packageName,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onApplySystem(wp) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Custom Media & Library Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_custom_live_media"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                    Text("Add Custom Live Wallpapers", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPickGif,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Gif, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add GIF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = onPickVideo,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (savedList.isNotEmpty()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Text("My Saved Live Wallpapers (${savedList.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF90CAF9))

                    savedList.forEach { item ->
                        val isSelected = config.id == item.id
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { onSelectSaved(item) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF1976D2).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.18f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (item.mediaType == LiveMediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.Gif,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                item.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${item.mediaType.name} • ${item.rotationDegrees}° • Scale ${(item.scaleFactor * 100).toInt()}% • ${item.cropMode.name}",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.65f)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Active",
                                                tint = Color(0xFF64B5F6),
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }
                                        // Edit icon on every custom live wallpaper
                                        IconButton(
                                            onClick = {
                                                expandedEditId = if (expandedEditId == item.id) null else item.id
                                            },
                                            modifier = Modifier.size(34.dp).testTag("btn_edit_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit live wallpaper",
                                                tint = if (expandedEditId == item.id) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteSaved(item.id) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFFF8A80),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Slide down options: Rotate option icon and Crop option icon
                            AnimatedVisibility(
                                visible = expandedEditId == item.id,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, start = 6.dp, end = 6.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.50f),
                                    border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Rotate option icon
                                        OutlinedButton(
                                            onClick = { onRotate?.invoke(item) },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RotateRight,
                                                contentDescription = "Rotate",
                                                tint = Color(0xFF81D4FA),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Rotate (${item.rotationDegrees}°)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Crop option icon
                                        Button(
                                            onClick = { onCrop?.invoke(item) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1976D2),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Crop,
                                                contentDescription = "Crop",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Crop / Pinch",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adapter functions for backwards compatibility
 */
@Composable
fun MediaPickerAndPresetsCard(
    config: LiveWallpaperConfig,
    onPickGif: () -> Unit,
    onPickVideo: () -> Unit,
    onSelectPreset: (LiveMediaType, String) -> Unit,
    savedList: List<LiveWallpaperConfig>,
    onSelectSaved: (LiveWallpaperConfig) -> Unit,
    onDeleteSaved: (String) -> Unit
) {
    UnifiedWallpapersCard(
        config = config,
        isLoadingSystem = false,
        systemWallpapers = emptyList(),
        onSelectPreset = onSelectPreset,
        onApplySystem = {},
        onOpenSystemChooser = {},
        onPickGif = onPickGif,
        onPickVideo = onPickVideo,
        savedList = savedList,
        onSelectSaved = onSelectSaved,
        onDeleteSaved = onDeleteSaved
    )
}

@Composable
fun SystemLiveWallpapersCard(
    isLoading: Boolean,
    wallpapers: List<SystemLiveWallpaperInfo>,
    onApply: (SystemLiveWallpaperInfo) -> Unit,
    onOpenSystemChooser: () -> Unit
) {
    UnifiedWallpapersCard(
        config = LiveWallpaperConfig(),
        isLoadingSystem = isLoading,
        systemWallpapers = wallpapers,
        onSelectPreset = { _, _ -> },
        onApplySystem = onApply,
        onOpenSystemChooser = onOpenSystemChooser,
        onPickGif = {},
        onPickVideo = {},
        savedList = emptyList(),
        onSelectSaved = {},
        onDeleteSaved = {}
    )
}

/**
 * Fullscreen Interactive Preview Dialog for Live Wallpapers.
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
fun FullscreenCropPinchScreen(
    config: LiveWallpaperConfig,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onSave: (LiveWallpaperConfig) -> Unit
) {
    val context = LocalContext.current
    val activity = (context as? Activity)
        ?: ((context as? ContextWrapper)?.baseContext as? Activity)

    // Completely hides the whole UI including status bar and navigation bar (Immersive Mode)
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = if (window != null) WindowCompat.getInsetsController(window, window.decorView) else null
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(onBack = onDismiss)

    var currentScale by remember(config.id) { mutableFloatStateOf(config.scaleFactor) }
    var currentOffsetX by remember(config.id) { mutableFloatStateOf(config.offsetX) }
    var currentOffsetY by remember(config.id) { mutableFloatStateOf(config.offsetY) }
    var currentRotation by remember(config.id) { mutableIntStateOf(config.rotationDegrees) }

    val colorMatrixArray = remember(config.colorFilter) {
        LiveWallpaperManager.getColorMatrix(config.colorFilter)
    }
    val composeColorFilter = remember(colorMatrixArray) {
        ColorFilter.colorMatrix(ColorMatrix(colorMatrixArray))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen_crop_screen")
    ) {
        // 1. Gesture detector for two-finger pinch-to-resize and pan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        currentScale = (currentScale * zoom).coerceIn(0.2f, 6.0f)
                        currentOffsetX += pan.x
                        currentOffsetY += pan.y
                    }
                }
        ) {
            // Live wallpaper media container with graphics transformation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = currentRotation.toFloat()
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = currentOffsetX
                        translationY = currentOffsetY
                    }
                    .then(
                        if (config.blurRadius > 0) Modifier.blur(config.blurRadius.dp) else Modifier
                    )
            ) {
                when (config.mediaType) {
                    LiveMediaType.VIDEO -> {
                        val path = config.mediaPath
                        if (!path.isNullOrEmpty() && File(path).exists()) {
                            VideoTexturePreview(
                                videoPath = path,
                                isMuted = config.isMuted,
                                playbackSpeed = config.playbackSpeed,
                                colorFilter = composeColorFilter,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PresetCanvasPreview(
                                presetType = LiveMediaType.PRESET_AURORA,
                                colorMatrixArray = colorMatrixArray,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    LiveMediaType.GIF -> {
                        val path = config.mediaPath
                        if (!path.isNullOrEmpty() && File(path).exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(path))
                                    .size(coil.size.Size.ORIGINAL)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = "Fullscreen GIF Crop",
                                contentScale = ContentScale.Crop,
                                colorFilter = composeColorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PresetCanvasPreview(
                                presetType = LiveMediaType.PRESET_AURORA,
                                colorMatrixArray = colorMatrixArray,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> {
                        PresetCanvasPreview(
                            presetType = config.mediaType,
                            colorMatrixArray = colorMatrixArray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // 2. Subtle Photographic 3x3 Grid Overlay (Crop Guideline)
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val gridColor = Color.White.copy(alpha = 0.16f)
            // Vertical lines
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(w / 3f, 0f), end = androidx.compose.ui.geometry.Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(2f * w / 3f, 0f), end = androidx.compose.ui.geometry.Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
            // Horizontal lines
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h / 3f), end = androidx.compose.ui.geometry.Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, 2f * h / 3f), end = androidx.compose.ui.geometry.Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
        }

        // 3. Top Floating Hint Pill
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Pinch with 2 fingers to zoom • Drag to position",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Bottom Floating Glass Toolbar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Cancel
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }

                // Reset
                OutlinedButton(
                    onClick = {
                        currentScale = 1.0f
                        currentOffsetX = 0f
                        currentOffsetY = 0f
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFCC80)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset", fontSize = 12.sp)
                }

                // Rotate 90°
                OutlinedButton(
                    onClick = {
                        currentRotation = (currentRotation + 90) % 360
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${currentRotation}°", fontSize = 12.sp)
                }

                // Done / Save Button
                Button(
                    onClick = {
                        val updated = config.copy(
                            scaleFactor = currentScale,
                            offsetX = currentOffsetX,
                            offsetY = currentOffsetY,
                            rotationDegrees = currentRotation
                        )
                        onSave(updated)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FullscreenLiveWallpaperDialog(
    config: LiveWallpaperConfig,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onApply: (LiveWallpaperConfig) -> Unit
) {
    FullscreenCropPinchScreen(
        config = config,
        imageLoader = imageLoader,
        onDismiss = onDismiss,
        onSave = onApply
    )
}
