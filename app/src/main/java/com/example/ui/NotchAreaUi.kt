package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NotchAreaGeometry
import com.example.data.NotchConfigEntity
import com.example.data.toAreaGeometry
import com.example.service.NotchAccessibilityService
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * While [enabled] and the app is in the foreground, shows colored markers on the real screen at the exact
 * position of every notch gesture area (the selected one highlighted), via the accessibility service.
 */
@Composable
fun NotchLivePreviewEffect(enabled: Boolean, selectedAreaId: Int) {
    val context = LocalContext.current
    var resumed by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed = true
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) resumed = false
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val active = enabled && resumed

    // Keep the markers alive (the service hides them by itself if this stops)
    LaunchedEffect(active, selectedAreaId) {
        if (active) {
            while (true) {
                NotchAccessibilityService.setPositionPreview(true, selectedAreaId)
                delay(30_000L)
            }
        }
    }
    DisposableEffect(active) {
        onDispose { NotchAccessibilityService.setPositionPreview(false) }
    }
}

/** Row of "Notch 1 / Notch 2 / Notch 3" selector chips. */
@Composable
fun NotchAreaChipsRow(
    areaIds: List<Int>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        areaIds.forEach { id ->
            FilterChip(
                selected = id == selectedId,
                onClick = { onSelect(id) },
                label = {
                    Text(
                        text = "Notch $id",
                        fontSize = 13.sp,
                        fontWeight = if (id == selectedId) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

/**
 * Position & size editor for one notch gesture area: optional live preview of all areas
 * (the selected one can be dragged) plus width / height / corner radius / X / Y sliders.
 *
 * [onChange] is called while a slider or the preview is being dragged, [onCommit] when it is released.
 */
@Composable
fun NotchAreaEditor(
    allAreas: List<NotchAreaGeometry>,
    selectedId: Int,
    showPreview: Boolean,
    onChange: (NotchAreaGeometry) -> Unit,
    onCommit: (NotchAreaGeometry) -> Unit,
    modifier: Modifier = Modifier
) {
    val persisted = allAreas.firstOrNull { it.id == selectedId } ?: allAreas.first()

    // Value being edited right now. The saved value comes back a moment later, so it must not
    // overwrite the slider while the finger is still down.
    var local by remember(persisted.id) { mutableStateOf(persisted) }
    var interacting by remember { mutableStateOf(false) }
    LaunchedEffect(persisted) {
        if (!interacting) local = persisted
    }

    val shownAreas = allAreas.map { if (it.id == local.id) local else it }

    fun edit(updated: NotchAreaGeometry) {
        interacting = true
        local = updated
        onChange(updated)
    }

    fun finish() {
        interacting = false
        onCommit(local)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showPreview) {
            NotchAreasPreview(
                areas = shownAreas,
                selectedId = local.id,
                onMove = { x, y -> edit(local.copy(xOffsetDp = x, yOffsetDp = y)) },
                onMoveFinished = { finish() }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        AreaSliderRow(
            label = "Width",
            value = local.widthDp.toFloat(),
            valueRange = 30f..260f,
            suffix = "dp",
            onValueChange = { edit(local.copy(widthDp = it.roundToInt())) },
            onValueChangeFinished = { finish() }
        )
        AreaSliderRow(
            label = "Height",
            value = local.heightDp.toFloat(),
            valueRange = 10f..100f,
            suffix = "dp",
            onValueChange = { edit(local.copy(heightDp = it.roundToInt())) },
            onValueChangeFinished = { finish() }
        )
        AreaSliderRow(
            label = "Corner Radius",
            value = local.cornerRadiusDp.toFloat(),
            valueRange = 0f..40f,
            suffix = "dp",
            onValueChange = { edit(local.copy(cornerRadiusDp = it.roundToInt())) },
            onValueChangeFinished = { finish() }
        )
        AreaSliderRow(
            label = "Y Position (distance from top)",
            value = local.yOffsetDp.toFloat(),
            valueRange = 0f..120f,
            suffix = "dp",
            onValueChange = { edit(local.copy(yOffsetDp = it.roundToInt())) },
            onValueChangeFinished = { finish() }
        )
        AreaSliderRow(
            label = "X Position (center / left / right)",
            value = local.xOffsetDp.toFloat(),
            valueRange = -180f..180f,
            suffix = "dp",
            onValueChange = { edit(local.copy(xOffsetDp = it.roundToInt())) },
            onValueChangeFinished = { finish() }
        )
    }
}

@Composable
private fun AreaSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${value.roundToInt()} $suffix",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Small phone mock-up showing every notch area; the selected area can be dragged around. */
@Composable
private fun NotchAreasPreview(
    areas: List<NotchAreaGeometry>,
    selectedId: Int,
    onMove: (Int, Int) -> Unit,
    onMoveFinished: () -> Unit
) {
    val scale = 0.7f
    val density = LocalDensity.current.density
    val selected = areas.firstOrNull { it.id == selectedId } ?: areas.first()
    val currentSelected by rememberUpdatedState(selected)
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .width(264.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0E1117))
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
        ) {
            // Status bar mock-up
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("09:41", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                }
            }

            // Unselected areas first so the selected one stays on top and draggable
            val ordered = areas.sortedBy { if (it.id == selectedId) 1 else 0 }
            ordered.forEach { area ->
                val isSelected = area.id == selectedId
                val shape = RoundedCornerShape((area.cornerRadiusDp * scale).dp)
                val dragModifier = if (isSelected) {
                    Modifier.pointerInput(area.id) {
                        detectDragGestures(
                            onDragStart = {
                                accX = currentSelected.xOffsetDp.toFloat()
                                accY = currentSelected.yOffsetDp.toFloat()
                            },
                            onDragEnd = { onMoveFinished() },
                            onDragCancel = { onMoveFinished() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accX = (accX + dragAmount.x / (scale * density)).coerceIn(-180f, 180f)
                                accY = (accY + dragAmount.y / (scale * density)).coerceIn(0f, 120f)
                                onMove(accX.roundToInt(), accY.roundToInt())
                            }
                        )
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                (area.xOffsetDp * scale * density).roundToInt(),
                                (area.yOffsetDp * scale * density).roundToInt()
                            )
                        }
                        .width((area.widthDp * scale).dp)
                        .height((area.heightDp * scale).dp)
                        .clip(shape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape
                        )
                        .then(dragModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${area.id}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Drag the highlighted area or use the sliders below to place it exactly",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/**
 * "Notch Position" screen: position, size and presets for every notch gesture area, plus the settings
 * all areas share (haptics and fullscreen behaviour).
 */
@Composable
fun NotchPositionScreen(
    config: NotchConfigEntity,
    extraAreas: List<NotchAreaGeometry>,
    onConfigChange: (NotchConfigEntity) -> Unit,
    onAreaSave: (NotchAreaGeometry, Boolean) -> Unit
) {
    val context = LocalContext.current

    var isAccessibilityEnabled by remember {
        mutableStateOf(com.example.isAccessibilityServiceEnabled(context, NotchAccessibilityService::class.java))
    }
    DisposableEffect(context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = com.example.isAccessibilityServiceEnabled(
                    context,
                    NotchAccessibilityService::class.java
                )
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val allAreas = remember(config, extraAreas) { listOf(config.toAreaGeometry()) + extraAreas }
    var selectedId by rememberSaveable { mutableStateOf(1) }
    val activeId = if (allAreas.any { it.id == selectedId }) selectedId else 1
    val activeArea = allAreas.first { it.id == activeId }
    var showScreenPreview by rememberSaveable { mutableStateOf(true) }
    NotchLivePreviewEffect(enabled = showScreenPreview && isAccessibilityEnabled, selectedAreaId = activeId)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isAccessibilityEnabled) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sky Touch Accessibility Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Tap here to enable the Accessibility Service so the notch gesture areas work over apps.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Area selector
        item {
            Column {
                Text(
                    text = "Notch gesture areas (${allAreas.size}/3)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pick an area to set its position and size. Add or remove areas with the + button in Notch Gestures.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                NotchAreaChipsRow(
                    areaIds = allAreas.map { it.id },
                    selectedId = activeId,
                    onSelect = { selectedId = it }
                )
            }
        }

        // Preview + sliders
        item {
            SettingsCard(
                title = "Position & Size - Notch $activeId",
                subtitle = "Fine-tune where this gesture area sits and how big it is."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Show on my screen",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Draws colored markers on your real screen where the gesture areas are (orange = selected)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = showScreenPreview, onCheckedChange = { showScreenPreview = it })
                }
                Spacer(modifier = Modifier.height(14.dp))
                NotchAreaEditor(
                    allAreas = allAreas,
                    selectedId = activeId,
                    showPreview = true,
                    onChange = { onAreaSave(it, false) },
                    onCommit = { onAreaSave(it, true) }
                )
            }
        }

        // Quick presets for the selected area
        item {
            SettingsCard(
                title = "Quick Presets",
                subtitle = "Common front camera cutout sizes (applies to Notch $activeId)"
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        Triple("Hole Punch", 34, 34),
                        Triple("Pill", 100, 34),
                        Triple("Island", 140, 36),
                        Triple("Dot", 26, 26)
                    )
                    presets.forEach { (name, w, h) ->
                        val isCurrent = activeArea.widthDp == w && activeArea.heightDp == h
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onAreaSave(
                                        activeArea.copy(widthDp = w, heightDp = h, cornerRadiusDp = h / 2),
                                        true
                                    )
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${w}x$h",
                                    fontSize = 9.sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings shared by every area
        item {
            SettingsCard(
                title = "All Notch Areas",
                subtitle = "These settings apply to every gesture area"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Disable in Fullscreen Apps",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Turns the gesture areas off when games or videos enter immersive fullscreen",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.disableInFullScreen,
                        onCheckedChange = { onConfigChange(config.copy(disableInFullScreen = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Haptic Vibration Strength",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 1 to "Light", 2 to "Medium", 3 to "Heavy").forEach { (strength, name) ->
                        val isSelected = config.vibrationStrength == strength
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onConfigChange(config.copy(vibrationStrength = strength)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
