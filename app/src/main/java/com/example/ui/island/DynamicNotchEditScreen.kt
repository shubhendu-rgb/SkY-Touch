package com.example.ui.island

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicNotchEditScreen(
    initialSkin: DynamicIslandSkin,
    onBack: () -> Unit,
    onApplySkin: (DynamicIslandSkin) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentSkin by remember { mutableStateOf(initialSkin) }
    var previewState by remember { mutableStateOf("Alert") } // "Idle", "Alert", "Expanded"

    var colorPickerTitle by remember { mutableStateOf<String?>(null) }
    var colorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentSkin.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showResetConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Preset"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. LIVE PREVIEW CARD WITH STATE SELECTOR
            item {
                LivePreviewCard(
                    skin = currentSkin,
                    previewState = previewState,
                    onPreviewStateChange = { previewState = it }
                )
            }

            // 2. APPLY BUTTON
            item {
                Button(
                    onClick = {
                        PredefinedSkins.saveCustomizedSkin(context, currentSkin)
                        onApplySkin(currentSkin)
                        Toast.makeText(context, "Look applied successfully", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply this look",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 3. SHAPE SECTION
            item {
                CustomSectionHeader(title = "SHAPE")
                Spacer(modifier = Modifier.height(6.dp))
                ShapeSegmentedRow(
                    selectedShape = currentSkin.customShape,
                    onShapeSelect = { shape ->
                        currentSkin = currentSkin.copy(customShape = shape)
                    }
                )
            }

            // 4. SIZE SECTION
            item {
                CustomSectionHeader(title = "SIZE")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SliderControlRow(
                            label = "Width",
                            value = currentSkin.customWidthDp.toFloat(),
                            valueRange = 70f..240f,
                            badgeText = "${currentSkin.customWidthDp}dp",
                            onValueChange = { currentSkin = currentSkin.copy(customWidthDp = it.toInt()) }
                        )

                        SliderControlRow(
                            label = "Height",
                            value = currentSkin.customHeightDp.toFloat(),
                            valueRange = 20f..56f,
                            badgeText = "${currentSkin.customHeightDp}dp",
                            onValueChange = { currentSkin = currentSkin.copy(customHeightDp = it.toInt()) }
                        )

                        SliderControlRow(
                            label = "Expanded width",
                            value = currentSkin.expandedWidthPercent.toFloat(),
                            valueRange = 60f..98f,
                            badgeText = "${currentSkin.expandedWidthPercent}%",
                            onValueChange = { currentSkin = currentSkin.copy(expandedWidthPercent = it.toInt()) }
                        )
                    }
                }
            }

            // 5. COLORS SECTION
            item {
                CustomSectionHeader(title = "COLORS")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ColorPickerRow(
                            icon = Icons.Default.FormatPaint,
                            label = "Island color",
                            color = currentSkin.islandColor,
                            onClick = {
                                colorPickerTitle = "Choose Island Color"
                                colorPickerTarget = ColorPickerTarget.ISLAND_COLOR
                            }
                        )

                        ColorPickerRow(
                            icon = Icons.Default.TextFields,
                            label = "Text and icons",
                            color = currentSkin.textAndIconsColor,
                            onClick = {
                                colorPickerTitle = "Choose Text & Icons Color"
                                colorPickerTarget = ColorPickerTarget.TEXT_COLOR
                            }
                        )

                        SliderControlRow(
                            label = "Opacity",
                            value = currentSkin.opacityPercent.toFloat(),
                            valueRange = 20f..100f,
                            badgeText = "${currentSkin.opacityPercent}%",
                            onValueChange = { currentSkin = currentSkin.copy(opacityPercent = it.toInt()) }
                        )
                    }
                }
            }

            // 6. BORDER, GLOW AND SHINE SECTION
            item {
                CustomSectionHeader(title = "BORDER, GLOW AND SHINE")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ColorPickerRow(
                            icon = Icons.Default.BorderStyle,
                            label = "Border color",
                            color = currentSkin.borderColor,
                            onClick = {
                                colorPickerTitle = "Choose Border Color"
                                colorPickerTarget = ColorPickerTarget.BORDER_COLOR
                            }
                        )

                        SliderControlRow(
                            label = "Border width",
                            value = currentSkin.borderWidthDp.toFloat(),
                            valueRange = 0f..6f,
                            steps = 5,
                            badgeText = "${currentSkin.borderWidthDp}dp",
                            onValueChange = { currentSkin = currentSkin.copy(borderWidthDp = it.toInt()) }
                        )

                        ColorPickerRow(
                            icon = Icons.Default.AutoAwesome,
                            label = "Glow color",
                            color = currentSkin.glowColor,
                            onClick = {
                                colorPickerTitle = "Choose Glow Color"
                                colorPickerTarget = ColorPickerTarget.GLOW_COLOR
                            }
                        )

                        SliderControlRow(
                            label = "Glow size",
                            value = currentSkin.glowSizeDp.toFloat(),
                            valueRange = 0f..24f,
                            steps = 11,
                            badgeText = "${currentSkin.glowSizeDp}dp",
                            onValueChange = { currentSkin = currentSkin.copy(glowSizeDp = it.toInt()) }
                        )

                        ColorPickerRow(
                            icon = Icons.Default.Lightbulb,
                            label = "Shine color",
                            color = currentSkin.shineColor,
                            onClick = {
                                colorPickerTitle = "Choose Shine Color"
                                colorPickerTarget = ColorPickerTarget.SHINE_COLOR
                            }
                        )

                        SliderControlRow(
                            label = "Shine",
                            value = currentSkin.shinePercent.toFloat(),
                            valueRange = 0f..100f,
                            badgeText = "${currentSkin.shinePercent}%",
                            onValueChange = { currentSkin = currentSkin.copy(shinePercent = it.toInt()) }
                        )
                    }
                }
            }

            // 7. EXTRAS SECTION
            item {
                CustomSectionHeader(title = "EXTRAS")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Accent outline",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = currentSkin.accentOutline,
                                onCheckedChange = { currentSkin = currentSkin.copy(accentOutline = it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Drop shadow",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = currentSkin.dropShadow,
                                onCheckedChange = { currentSkin = currentSkin.copy(dropShadow = it) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Untouchable",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Pass through all touches to apps underneath",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val currentBehavior = DynamicNotchManager.activeBehavior.value
                            Switch(
                                checked = currentBehavior.isUntouchable,
                                onCheckedChange = {
                                    DynamicNotchManager.updateBehavior(context, currentBehavior.copy(isUntouchable = it))
                                }
                            )
                        }
                    }
                }
            }

            // 8. THIS PRESET SECTION
            item {
                CustomSectionHeader(title = "THIS PRESET")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Send a test event
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DynamicNotchManager.sendTestEvent(context)
                                    Toast.makeText(context, "Test event sent to on-screen notch", Toast.LENGTH_SHORT).show()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Send a test event",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Puts this look on screen for a few seconds",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Reset this preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showResetConfirmDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Reset this preset",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Undo every change you made here",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset preset?") },
            text = { Text("All your custom sizes, colors, and borders for this preset will be restored to their defaults.") },
            confirmButton = {
                Button(
                    onClick = {
                        val reset = PredefinedSkins.resetPreset(context, currentSkin.id)
                        currentSkin = reset
                        onApplySkin(reset)
                        showResetConfirmDialog = false
                        Toast.makeText(context, "Preset reset to defaults", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Color picker dialog
    if (colorPickerTitle != null && colorPickerTarget != null) {
        ColorPaletteDialog(
            title = colorPickerTitle!!,
            onColorSelect = { selectedColor ->
                when (colorPickerTarget) {
                    ColorPickerTarget.ISLAND_COLOR -> currentSkin = currentSkin.copy(islandColor = selectedColor)
                    ColorPickerTarget.TEXT_COLOR -> currentSkin = currentSkin.copy(textAndIconsColor = selectedColor)
                    ColorPickerTarget.BORDER_COLOR -> currentSkin = currentSkin.copy(borderColor = selectedColor)
                    ColorPickerTarget.GLOW_COLOR -> currentSkin = currentSkin.copy(glowColor = selectedColor)
                    ColorPickerTarget.SHINE_COLOR -> currentSkin = currentSkin.copy(shineColor = selectedColor)
                    null -> {}
                }
                colorPickerTitle = null
                colorPickerTarget = null
            },
            onDismiss = {
                colorPickerTitle = null
                colorPickerTarget = null
            }
        )
    }
}

private enum class ColorPickerTarget {
    ISLAND_COLOR,
    TEXT_COLOR,
    BORDER_COLOR,
    GLOW_COLOR,
    SHINE_COLOR
}

@Composable
private fun CustomSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LivePreviewCard(
    skin: DynamicIslandSkin,
    previewState: String,
    onPreviewStateChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF14141A),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dynamic Island Pill Live Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                DynamicIslandPill(
                    skin = skin,
                    state = IslandState.COLLAPSED,
                    previewLookState = previewState
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State Selector: [ Idle | Alert ]
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF22222E))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Idle", "Alert").forEach { tab ->
                    val isSelected = previewState == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onPreviewStateChange(tab) }
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeSegmentedRow(
    selectedShape: String,
    onShapeSelect: (String) -> Unit
) {
    val shapes = listOf("Pill", "Rounded", "Sharp", "Notch")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        shapes.forEach { shape ->
            val isSelected = selectedShape.equals(shape, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onShapeSelect(shape) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shape,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SliderControlRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    badgeText: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ColorPickerRow(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Circular color preview swatch
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

@Composable
private fun ColorPaletteDialog(
    title: String,
    onColorSelect: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = listOf(
        Color(0xFF000000), // Pure Black
        Color(0xFF1E1E26), // Charcoal
        Color(0xFF2C3440), // Slate
        Color(0xFF1B2A4A), // Navy
        Color(0xFF0D3B36), // Deep Teal
        Color(0xFF00E5FF), // Cyan Neon
        Color(0xFF2979FF), // Electric Blue
        Color(0xFF7C4DFF), // Purple
        Color(0xFFFF0055), // Neon Crimson
        Color(0xFFFF5252), // Coral Red
        Color(0xFFFF9100), // Amber
        Color(0xFFFFD700), // Gold
        Color(0xFF00E676), // Mint
        Color(0xFF69F0AE), // Soft Green
        Color(0xFFE0E0E0), // Platinum
        Color(0xFFFFFFFF)  // Pure White
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            val firstPaletteRow = remember(palette) { palette.take(8) }
            val secondPaletteRow = remember(palette) { palette.drop(8) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select a color swatch:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(firstPaletteRow, key = { it.value.toLong() }) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                                .clickable { onColorSelect(color) }
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(secondPaletteRow, key = { it.value.toLong() }) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                                .clickable { onColorSelect(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
