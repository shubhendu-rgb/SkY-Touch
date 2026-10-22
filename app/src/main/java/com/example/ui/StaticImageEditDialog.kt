package com.example.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.service.wallpaper.LiveCropMode
import com.example.service.wallpaper.LiveMediaType
import com.example.service.wallpaper.LiveWallpaperConfig
import com.example.service.wallpaper.LiveWallpaperManager
import com.example.service.wallpaper.StaticImageEdit
import com.example.service.wallpaper.StaticImageEditStore
import java.io.File

private fun StaticImageEdit.toLiveConfig(id: String, path: String?) = LiveWallpaperConfig(
    id = id,
    title = "Wallpaper",
    mediaType = LiveMediaType.GIF,
    mediaPath = path,
    rotationDegrees = rotationDegrees,
    cropMode = cropMode,
    colorFilter = colorFilter,
    blurRadius = blurRadius,
    scaleFactor = scaleFactor,
    offsetX = offsetX,
    offsetY = offsetY
)

private fun LiveWallpaperConfig.toStaticEdit() = StaticImageEdit(
    rotationDegrees = rotationDegrees,
    cropMode = cropMode,
    colorFilter = colorFilter,
    blurRadius = blurRadius,
    scaleFactor = scaleFactor,
    offsetX = offsetX,
    offsetY = offsetY
)

/**
 * Edit one static wallpaper picture with the same options the live wallpapers have:
 * rotate, interactive crop & pinch, crop mode, blur and color filters.
 * Every change is saved at once and is used wherever the picture is applied.
 */
@Composable
fun StaticImageEditDialog(
    uriStr: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val context = LocalContext.current
    val path = remember(uriStr) { Uri.parse(uriStr).path }
    var edit by remember(uriStr) { mutableStateOf(StaticImageEditStore.get(context, uriStr) ?: StaticImageEdit()) }
    var showCrop by remember { mutableStateOf(false) }
    val imageLoader = remember { ImageLoader(context) }

    fun update(e: StaticImageEdit) {
        edit = e
        StaticImageEditStore.save(context, uriStr, e)
    }

    val liveConfig = remember(edit, uriStr, path) { edit.toLiveConfig(uriStr, path) }
    val colorFilter = remember(edit.colorFilter) {
        ColorFilter.colorMatrix(ColorMatrix(LiveWallpaperManager.getColorMatrix(edit.colorFilter)))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .testTag("static_image_edit_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1B1F),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            ForceDarkPanel {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Edit wallpaper", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Same options as live wallpapers. Changes are saved automatically.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Small phone-shaped live preview of every change
                    item(key = "preview") {
                        val screenW = context.resources.displayMetrics.widthPixels.toFloat()
                        val screenH = context.resources.displayMetrics.heightPixels.toFloat()
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .height(250.dp)
                                    .aspectRatio(screenW / screenH)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            ) {
                                val factor = constraints.maxWidth.toFloat() / screenW
                                if (path != null && File(path).exists()) {
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = "Edit preview",
                                        contentScale = when (edit.cropMode) {
                                            LiveCropMode.FIT -> ContentScale.Fit
                                            LiveCropMode.STRETCH -> ContentScale.FillBounds
                                            else -> ContentScale.Crop
                                        },
                                        alignment = when (edit.cropMode) {
                                            LiveCropMode.LEFT -> Alignment.CenterStart
                                            LiveCropMode.RIGHT -> Alignment.CenterEnd
                                            else -> Alignment.Center
                                        },
                                        colorFilter = colorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationZ = edit.rotationDegrees.toFloat()
                                                scaleX = edit.scaleFactor
                                                scaleY = edit.scaleFactor
                                                translationX = edit.offsetX * factor
                                                translationY = edit.offsetY * factor
                                            }
                                            .then(
                                                if (edit.blurRadius > 0) Modifier.blur((edit.blurRadius * factor).dp) else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }

                    item(key = "edit_options") {
                        EditAndCropCard(
                            config = liveConfig,
                            onConfigChange = { updated -> update(updated.toStaticEdit()) },
                            onOpenFullscreenCrop = { showCrop = true },
                            embedded = true,
                            showPlayback = false
                        )
                    }

                    item(key = "color_filters") {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))
                        ColorFiltersCard(
                            config = liveConfig,
                            onFilterSelect = { filter -> update(edit.copy(colorFilter = filter)) },
                            embedded = true
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            StaticImageEditStore.clear(context, uriStr)
                            edit = StaticImageEdit()
                        },
                        modifier = Modifier.weight(1f).testTag("btn_static_edit_reset"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset", fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onApply(uriStr) },
                        modifier = Modifier.weight(1f).testTag("btn_static_edit_apply"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            }
        }
    }

    if (showCrop) {
        Dialog(
            onDismissRequest = { showCrop = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditDialogFullBleedEffect()
            FullscreenCropPinchScreen(
                config = liveConfig,
                imageLoader = imageLoader,
                onDismiss = { showCrop = false },
                onSave = { updated ->
                    update(updated.toStaticEdit())
                    showCrop = false
                }
            )
        }
    }
}

/** Lets the fullscreen crop screen use the whole display, like it does on the live wallpaper screen. */
@Composable
private fun EditDialogFullBleedEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        var parent: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                window = parent.window
                break
            }
            parent = parent.parent
        }
        window?.let {
            it.statusBarColor = android.graphics.Color.TRANSPARENT
            it.navigationBarColor = android.graphics.Color.TRANSPARENT
            it.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false)
        }
        onDispose {}
    }
}
