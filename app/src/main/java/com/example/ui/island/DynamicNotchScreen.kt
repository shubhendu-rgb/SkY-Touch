package com.example.ui.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AirplanemodeInactive
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.NotchConfigEntity
import kotlin.math.roundToInt

enum class DynamicNotchTab(val title: String) {
    POSITION("Position & Size"),
    SKINS("Skins & Text"),
    BEHAVIOR("Events & Actions")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicNotchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: DynamicNotchTab = DynamicNotchTab.POSITION,
    config: NotchConfigEntity? = null,
    onConfigChange: ((NotchConfigEntity) -> Unit)? = null,
    onConfigChangeDebounced: ((NotchConfigEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedSkin by remember { mutableStateOf(PredefinedSkins.getSavedSkin(context)) }
    var behavior by remember { mutableStateOf(BehaviorPreferences.load(context)) }
    var isAccessibilityEnabled by remember {
        mutableStateOf(com.example.isAccessibilityServiceEnabled(context, com.example.service.NotchAccessibilityService::class.java))
    }
    var isServiceActive by remember { mutableStateOf(DynamicNotchManager.isDynamicNotchEnabled.value) }
    var skinBeingEdited by remember { mutableStateOf<DynamicIslandSkin?>(null) }
    var showGuideDialog by remember { mutableStateOf(false) }

    // Live position values if config is provided
    var localWidth by remember(config?.widthDp) { mutableFloatStateOf(config?.widthDp?.toFloat() ?: 100f) }
    var localHeight by remember(config?.heightDp) { mutableFloatStateOf(config?.heightDp?.toFloat() ?: 36f) }
    var localYOffset by remember(config?.yOffsetDp) { mutableFloatStateOf(config?.yOffsetDp?.toFloat() ?: 0f) }
    var localXOffset by remember(config?.xOffsetDp) { mutableFloatStateOf(config?.xOffsetDp?.toFloat() ?: 0f) }
    var localCornerRadius by remember(config?.cornerRadiusDp) { mutableFloatStateOf(config?.cornerRadiusDp?.toFloat() ?: 18f) }

    val liveConfig = remember(config, localWidth, localHeight, localYOffset, localXOffset, localCornerRadius) {
        config?.copy(
            widthDp = localWidth.roundToInt(),
            heightDp = localHeight.roundToInt(),
            yOffsetDp = localYOffset.roundToInt(),
            xOffsetDp = localXOffset.roundToInt(),
            cornerRadiusDp = localCornerRadius.roundToInt()
        )
    }

    val notifyConfigChange: (NotchConfigEntity, Boolean) -> Unit = { updated, finished ->
        if (finished) {
            onConfigChange?.invoke(updated)
        } else if (onConfigChangeDebounced != null) {
            onConfigChangeDebounced.invoke(updated)
        } else {
            onConfigChange?.invoke(updated)
        }
    }

    // Re-check accessibility service status on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = com.example.isAccessibilityServiceEnabled(
                    context,
                    com.example.service.NotchAccessibilityService::class.java
                )
                isServiceActive = DynamicNotchManager.isDynamicNotchEnabled.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // If a skin is being edited, show the full customization editor screen
    if (skinBeingEdited != null) {
        DynamicNotchEditScreen(
            initialSkin = skinBeingEdited!!,
            onBack = { skinBeingEdited = null },
            onApplySkin = { appliedSkin ->
                selectedSkin = appliedSkin
                DynamicNotchManager.updateSkin(context, appliedSkin)
                skinBeingEdited = null
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dynamic Notch",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "Skins, Customization & Capsule Behavior",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    if (selectedTab == DynamicNotchTab.BEHAVIOR) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { showGuideDialog = true }
                        ) {
                            Text(
                                text = "Guide",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Interactive Notch Live Sandbox (Always visible at top so user tests animations)
            InteractiveNotchPreview(
                skin = selectedSkin,
                behavior = behavior,
                state = IslandState.COLLAPSED,
                config = liveConfig,
                onConfigChange = { updated ->
                    localXOffset = updated.xOffsetDp.toFloat()
                    localYOffset = updated.yOffsetDp.toFloat()
                    notifyConfigChange(updated, false)
                },
                onConfigChangeFinished = { updated ->
                    notifyConfigChange(updated, true)
                },
                onStateToggle = {}
            )

            // Top Tab Switcher: Skins vs Behavior
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                DynamicNotchTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                DynamicNotchTab.POSITION -> {
                    val activeConfig = liveConfig ?: NotchConfigEntity()
                    PositionTabContent(
                        config = activeConfig,
                        onConfigChange = { updated ->
                            localWidth = updated.widthDp.toFloat()
                            localHeight = updated.heightDp.toFloat()
                            localYOffset = updated.yOffsetDp.toFloat()
                            localXOffset = updated.xOffsetDp.toFloat()
                            localCornerRadius = updated.cornerRadiusDp.toFloat()
                            notifyConfigChange(updated, false)
                        },
                        onConfigChangeFinished = { updated ->
                            notifyConfigChange(updated, true)
                        },
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        onOpenAccessibility = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
                DynamicNotchTab.SKINS -> {
                    SkinsTabContent(
                        selectedSkin = selectedSkin,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isServiceActive = isServiceActive,
                        behavior = behavior,
                        onSkinSelect = { skin ->
                            selectedSkin = skin
                            DynamicNotchManager.updateSkin(context, skin)
                        },
                        onEditSkin = { skin ->
                            skinBeingEdited = skin
                        },
                        onToggleService = { enable ->
                            if (!isAccessibilityEnabled) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                                Toast.makeText(
                                    context,
                                    "Please enable Sky Touch in Accessibility Settings to display Dynamic Notch",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                DynamicNotchManager.setEnabled(context, enable)
                                isServiceActive = enable
                                Toast.makeText(
                                    context,
                                    if (enable) "Dynamic Notch enabled" else "Dynamic Notch disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onBehaviorChange = { newBehavior ->
                            behavior = newBehavior
                            DynamicNotchManager.updateBehavior(context, newBehavior)
                        }
                    )
                }
                DynamicNotchTab.BEHAVIOR -> {
                    BehaviorTabContent(
                        behavior = behavior,
                        onBehaviorChange = { newBehavior ->
                            behavior = newBehavior
                            DynamicNotchManager.updateBehavior(context, newBehavior)
                        }
                    )
                }
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = {
                Text(
                    text = "Dynamic Notch & Capsule Events",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "The Dynamic Notch expands automatically in response to system changes and live activities:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Battery: Expands with charging indicator when plugged in, unplugged, or battery is low.\n" +
                               "• Wi-Fi & Bluetooth: Pops up connection banners when networks or headphones connect.\n" +
                               "• Airplane Mode & Volume: Alerts you instantly to state toggles without pulling down the shade.\n" +
                               "• Live Activities: Keeps music scrubber, timers, and notifications accessible at the top of your screen.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showGuideDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

/**
 * Interactive Notch Live Preview Sandbox & Calibration Simulator
 */
@Composable
private fun InteractiveNotchPreview(
    skin: DynamicIslandSkin,
    behavior: DynamicNotchBehavior,
    state: IslandState,
    config: NotchConfigEntity? = null,
    onConfigChange: ((NotchConfigEntity) -> Unit)? = null,
    onConfigChangeFinished: ((NotchConfigEntity) -> Unit)? = null,
    onStateToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE NOTCH CALIBRATION PREVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = if (config != null) "${config.widthDp}x${config.heightDp} dp" else "Live Notch",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phone Shell Simulator Mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0E1117))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            ) {
                // Status Bar header mockup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("09:41", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = "wifi", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                        Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = "battery", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                    }
                }

                // Dynamic Notch representation inside mockup
                val alignedSkin = if (config != null) {
                    skin.copy(
                        customWidthDp = config.widthDp,
                        customHeightDp = config.heightDp,
                        cornerRadiusDp = config.cornerRadiusDp
                    )
                } else skin

                val mockupX = ((config?.xOffsetDp ?: 0) * 0.7f).dp
                val mockupY = ((config?.yOffsetDp ?: 0) * 0.7f).dp

                val dragModifier: Modifier = if (config != null && onConfigChange != null) {
                    Modifier.pointerInput(config) {
                        detectDragGestures(
                            onDragEnd = { onConfigChangeFinished?.invoke(config) },
                            onDragCancel = { onConfigChangeFinished?.invoke(config) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val scaleFactor = 1.4f
                                val newX = (config.xOffsetDp + dragAmount.x / scaleFactor).coerceIn(-150f, 150f)
                                val newY = (config.yOffsetDp + dragAmount.y / scaleFactor).coerceIn(0f, 120f)
                                onConfigChange(
                                    config.copy(
                                        xOffsetDp = newX.roundToInt(),
                                        yOffsetDp = newY.roundToInt()
                                    )
                                )
                            }
                        )
                    }
                } else Modifier

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(mockupX.roundToPx(), mockupY.roundToPx()) }
                        .then(dragModifier)
                ) {
                    DynamicIslandPill(
                        skin = alignedSkin,
                        state = state,
                        behavior = behavior,
                        onStateChange = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Drag the pill in preview or use sliders below to calibrate exact alignment",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Skins Tab: Custom shapes, materials, halos, and glowing edge palettes
 */
@Composable
private fun SkinsTabContent(
    selectedSkin: DynamicIslandSkin,
    isAccessibilityEnabled: Boolean,
    isServiceActive: Boolean,
    behavior: DynamicNotchBehavior,
    onSkinSelect: (DynamicIslandSkin) -> Unit,
    onEditSkin: (DynamicIslandSkin) -> Unit,
    onToggleService: (Boolean) -> Unit,
    onBehaviorChange: (DynamicNotchBehavior) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<SkinCategory?>(null) }

    val filteredSkins = remember(selectedCategory) {
        if (selectedCategory == null) {
            PredefinedSkins.allSkins
        } else {
            PredefinedSkins.allSkins.filter { it.category == selectedCategory }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Floating Service Status & Overlay Banner
        item {
            ServiceStatusCard(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isServiceActive = isServiceActive,
                onToggleService = onToggleService
            )
        }

        // Dynamic Notch Typography & Text Adjustment (USER REQUEST)
        item {
            DynamicNotchTextAdjustmentCard(
                behavior = behavior,
                onBehaviorChange = onBehaviorChange
            )
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${PredefinedSkins.allSkins.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                SkinCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Skin Cards
        items(filteredSkins, key = { it.id }) { skin ->
            val isSelected = skin.id == selectedSkin.id
            SkinCardItem(
                skin = if (isSelected) selectedSkin else skin,
                isSelected = isSelected,
                onSelect = { onSkinSelect(skin) },
                onEdit = { onEditSkin(if (isSelected) selectedSkin else skin) }
            )
        }
    }
}

/**
 * Behavior Tab: Faithfully replicates the exact behavior, timing, visibility,
 * notification filter, live activities, and mini capsule events from the images.
 */
@Composable
private fun BehaviorTabContent(
    behavior: DynamicNotchBehavior,
    onBehaviorChange: (DynamicNotchBehavior) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 0. TOUCH & INTERACTION
        item {
            SectionHeader(title = "TOUCH & INTERACTION")
            Spacer(modifier = Modifier.height(8.dp))
            BehaviorGroupCard {
                BehaviorSwitchItem(
                    icon = Icons.Default.TouchApp,
                    title = "Untouchable",
                    subtitle = "Pass all touches through notch to apps underneath",
                    checked = behavior.isUntouchable,
                    onCheckedChange = { onBehaviorChange(behavior.copy(isUntouchable = it)) }
                )
            }
        }

        // 1. TIMING (Image 5)
        item {
            SectionHeader(title = "TIMING")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Collapse after + Stepped Slider + Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Collapse after",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF6750A4),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "${behavior.collapseAfterSeconds}s",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = behavior.collapseAfterSeconds.toFloat(),
                        onValueChange = { onBehaviorChange(behavior.copy(collapseAfterSeconds = it.toInt())) },
                        valueRange = 1f..15f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8E77F7),
                            activeTrackColor = Color(0xFF8E77F7),
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animation Speed Segmented Choice
                    Text(
                        text = "Animation speed",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SegmentedButtonsRow(
                        options = listOf(
                            AnimationSpeed.CALM.label,
                            AnimationSpeed.NORMAL.label,
                            AnimationSpeed.SNAPPY.label
                        ),
                        selectedIndex = when (behavior.animationSpeed) {
                            AnimationSpeed.CALM -> 0
                            AnimationSpeed.NORMAL -> 1
                            AnimationSpeed.SNAPPY -> 2
                        },
                        onSelect = { index ->
                            val newSpeed = when (index) {
                                0 -> AnimationSpeed.CALM
                                1 -> AnimationSpeed.NORMAL
                                else -> AnimationSpeed.SNAPPY
                            }
                            onBehaviorChange(behavior.copy(animationSpeed = newSpeed))
                        }
                    )
                }
            }
        }

        // 3. VISIBILITY (Image 3)
        item {
            SectionHeader(title = "VISIBILITY")
            Spacer(modifier = Modifier.height(8.dp))
            BehaviorGroupCard {
                BehaviorSwitchItem(
                    icon = Icons.Default.Lock,
                    title = "Show on the lock screen",
                    checked = behavior.showOnLockScreen,
                    onCheckedChange = { onBehaviorChange(behavior.copy(showOnLockScreen = it)) }
                )
                ItemDivider()
                BehaviorSwitchItem(
                    icon = Icons.Default.ScreenRotation,
                    title = "Show in landscape",
                    checked = behavior.showInLandscape,
                    onCheckedChange = { onBehaviorChange(behavior.copy(showInLandscape = it)) }
                )
                ItemDivider()
                BehaviorSwitchItem(
                    icon = Icons.Default.RestartAlt,
                    title = "Start after reboot",
                    checked = behavior.startAfterReboot,
                    onCheckedChange = { onBehaviorChange(behavior.copy(startAfterReboot = it)) }
                )
            }
        }

        // 4. NOTIFICATION FILTER (Image 3)
        item {
            SectionHeader(title = "NOTIFICATION FILTER")
            Spacer(modifier = Modifier.height(8.dp))
            BehaviorGroupCard {
                BehaviorSwitchItem(
                    icon = Icons.Default.FilterList,
                    title = "Include ongoing notifications",
                    subtitle = "Downloads, navigation and the like",
                    checked = behavior.includeOngoingNotifications,
                    onCheckedChange = { onBehaviorChange(behavior.copy(includeOngoingNotifications = it)) }
                )
                ItemDivider()
                BehaviorSwitchItem(
                    icon = Icons.Default.VolumeMute,
                    title = "Include silent notifications",
                    subtitle = "Low priority ones that arrive quietly",
                    checked = behavior.includeSilentNotifications,
                    onCheckedChange = { onBehaviorChange(behavior.copy(includeSilentNotifications = it)) }
                )
            }
        }

        // 5. ACTIONS AND LIVE ACTIVITIES (Image 4)
        item {
            SectionHeader(title = "ACTIONS AND LIVE ACTIVITIES")
            Spacer(modifier = Modifier.height(8.dp))
            BehaviorGroupCard {
                BehaviorSwitchItem(
                    icon = Icons.Default.Tune,
                    title = "Live activities",
                    subtitle = "Downloads and timers, for as long as they run",
                    checked = behavior.liveActivities,
                    onCheckedChange = { onBehaviorChange(behavior.copy(liveActivities = it)) }
                )
                ItemDivider()
                BehaviorSwitchItem(
                    icon = Icons.Default.OpenInNew,
                    title = "Show notification buttons",
                    subtitle = "Reply, archive and the rest, on the expanded island",
                    checked = behavior.showNotificationButtons,
                    onCheckedChange = { onBehaviorChange(behavior.copy(showNotificationButtons = it)) }
                )
                ItemDivider()
                BehaviorSwitchItem(
                    icon = Icons.Default.Send,
                    title = "Quick reply",
                    subtitle = "Type an answer without leaving the app you are in",
                    checked = behavior.quickReply,
                    onCheckedChange = { onBehaviorChange(behavior.copy(quickReply = it)) }
                )
            }
        }

        // 6. MINI CAPSULE EVENTS (Images 1 & 2)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mini Capsule Events",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Battery Events (Image 2)
        item {
            CapsuleSubHeader(title = "Battery")
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleEventCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Charging Started",
                    subtitle = "When you connect the charger",
                    checked = behavior.batteryChargingStarted,
                    onCheckedChange = { onBehaviorChange(behavior.copy(batteryChargingStarted = it)) }
                )
                CapsuleEventCard(
                    icon = Icons.Default.PowerOff,
                    title = "Charging Stopped",
                    subtitle = "When you disconnect the charger",
                    checked = behavior.batteryChargingStopped,
                    onCheckedChange = { onBehaviorChange(behavior.copy(batteryChargingStopped = it)) }
                )
                CapsuleEventCard(
                    icon = Icons.Default.BatteryAlert,
                    title = "Low Battery Warning",
                    subtitle = "When the battery level drops to 5 percent",
                    checked = behavior.batteryLowWarning,
                    onCheckedChange = { onBehaviorChange(behavior.copy(batteryLowWarning = it)) }
                )
            }
        }

        // Wi-Fi Events (Image 2)
        item {
            CapsuleSubHeader(title = "Wi-Fi")
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleEventCard(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi Connected",
                    subtitle = "When Wi-Fi network is connected",
                    checked = behavior.wifiConnected,
                    onCheckedChange = { onBehaviorChange(behavior.copy(wifiConnected = it)) }
                )
                CapsuleEventCard(
                    icon = Icons.Default.WifiOff,
                    title = "Wi-Fi Disconnected",
                    subtitle = "When Wi-Fi network is disconnected",
                    checked = behavior.wifiDisconnected,
                    onCheckedChange = { onBehaviorChange(behavior.copy(wifiDisconnected = it)) }
                )
            }
        }

        // Headphones Events (Image 1)
        item {
            CapsuleSubHeader(title = "Headphones")
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleEventCard(
                    icon = Icons.Default.Bluetooth,
                    title = "Headphones Connected",
                    subtitle = "When you connect them via Bluetooth",
                    badge = "Required Permission",
                    checked = behavior.headphonesConnected,
                    onCheckedChange = { onBehaviorChange(behavior.copy(headphonesConnected = it)) }
                )
                CapsuleEventCard(
                    icon = Icons.Default.BluetoothDisabled,
                    title = "Headphones Disconnected",
                    subtitle = "When you disconnect them via Bluetooth",
                    badge = "Required Permission",
                    checked = behavior.headphonesDisconnected,
                    onCheckedChange = { onBehaviorChange(behavior.copy(headphonesDisconnected = it)) }
                )
            }
        }

        // Airplane Mode Events (Image 1)
        item {
            CapsuleSubHeader(title = "Airplane mode")
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleEventCard(
                    icon = Icons.Default.AirplanemodeActive,
                    title = "Airplane mode turned on",
                    subtitle = "Shown when Airplane mode is turned on",
                    checked = behavior.airplaneModeOn,
                    onCheckedChange = { onBehaviorChange(behavior.copy(airplaneModeOn = it)) }
                )
                CapsuleEventCard(
                    icon = Icons.Default.AirplanemodeInactive,
                    title = "Airplane mode turned off",
                    subtitle = "Shown when Airplane mode is turned off",
                    checked = behavior.airplaneModeOff,
                    onCheckedChange = { onBehaviorChange(behavior.copy(airplaneModeOff = it)) }
                )
            }
        }

        // Volume Events (Image 1)
        item {
            CapsuleSubHeader(title = "Volume")
            Spacer(modifier = Modifier.height(6.dp))
            CapsuleEventCard(
                icon = Icons.Default.VolumeUp,
                title = "Volume Mode",
                subtitle = "When the volume mode changes",
                checked = behavior.volumeModeChanged,
                onCheckedChange = { onBehaviorChange(behavior.copy(volumeModeChanged = it)) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// UI Subcomponents & Building Blocks
// -------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    )
}

@Composable
private fun CapsuleSubHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFFFB4A9)
    )
}

@Composable
private fun SegmentedButtonsRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF6750A4) else Color.Transparent,
                    label = "SegmentBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "SegmentText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorGroupCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun BehaviorSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF6750A4).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6750A4),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun CapsuleEventCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF2C2422), // Matching the rich warm dark capsule background from the images
        border = BorderStroke(1.dp, Color(0xFF3E3330)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3E3330)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFFFB4A9),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEDE0DD)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFFD0C4C1)
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFB4A9)
                    )
                }
            }

            // Subtle vertical separator matching image
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .width(1.dp)
                    .background(Color(0xFF4E413E))
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFFF897D),
                    uncheckedThumbColor = Color(0xFF8A7E7B),
                    uncheckedTrackColor = Color(0xFF382E2B)
                )
            )
        }
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    )
}

/**
 * Service Status Card
 */
@Composable
private fun ServiceStatusCard(
    isAccessibilityEnabled: Boolean,
    isServiceActive: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceActive && isAccessibilityEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isServiceActive && isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!isAccessibilityEnabled) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!isAccessibilityEnabled) "Accessibility Service Required"
                    else if (isServiceActive) "Floating Notch Overlay Active"
                    else "Floating Notch Overlay Disabled",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (!isAccessibilityEnabled) {
                        "Tap to enable Sky Touch in Accessibility Settings (no 'display over other apps' needed)"
                    } else if (isServiceActive) {
                        "Dynamic Notch is floating using accessibility overlay at your notch position"
                    } else {
                        "Enable to show notch over your screen & apps"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isServiceActive && isAccessibilityEnabled,
                onCheckedChange = { onToggleService(it) },
                thumbContent = if (isServiceActive && isAccessibilityEnabled) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                } else null
            )
        }
    }
}

/**
 * Skin Card Item with mini graphical badge & glow preview
 */
@Composable
private fun SkinCardItem(
    skin: DynamicIslandSkin,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "SkinBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Graphical miniature pill icon
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .clip(
                        when (skin.shape) {
                            IslandShape.SLAB -> RoundedCornerShape(6.dp)
                            IslandShape.NOTCH -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                            else -> RoundedCornerShape(16.dp)
                        }
                    )
                    .background(
                        when (skin.effectType) {
                            IslandEffectType.FROST -> Color.White.copy(alpha = 0.2f)
                            IslandEffectType.MATERIAL_YOU -> MaterialTheme.colorScheme.surfaceVariant
                            else -> skin.baseColor
                        }
                    )
                    .then(
                        if (skin.glowColors.isNotEmpty()) {
                            Modifier.border(
                                width = 2.dp,
                                brush = Brush.horizontalGradient(skin.glowColors),
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else if (skin.outlineColor != Color.Transparent) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = skin.outlineColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.4f))
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = skin.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = skin.category.title,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = skin.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Notch Typography & Text Adjustment Card (USER REQUEST)
 * Provides text scale (70% - 140%), font weight (Regular vs Bold), and color tint override options.
 */
@Composable
fun DynamicNotchTextAdjustmentCard(
    behavior: DynamicNotchBehavior,
    onBehaviorChange: (DynamicNotchBehavior) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Dynamic Notch Typography & Text",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scale, weight & tint for clock, battery %, and capsules",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${(behavior.textScale * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Text Scale Slider
            Text(
                text = "Text Size Scale",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = behavior.textScale,
                onValueChange = { onBehaviorChange(behavior.copy(textScale = (it * 100).roundToInt() / 100f)) },
                valueRange = 0.70f..1.40f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Font Weight (Regular vs Bold)
            Text(
                text = "Font Weight",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(false to "Regular", true to "Bold Emphasis").forEach { (isBold, label) ->
                    val isSelected = behavior.textBold == isBold
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onBehaviorChange(behavior.copy(textBold = isBold)) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Text Color Tint
            Text(
                text = "Text Color Tint",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            val colorOptions = listOf(
                "" to ("Auto (Skin)" to Color.Transparent),
                "#FFFFFFFF" to ("Pure White" to Color.White),
                "#FF00E5FF" to ("Cyber Cyan" to Color(0xFF00E5FF)),
                "#FF00E676" to ("Emerald" to Color(0xFF00E676)),
                "#FFD500F9" to ("Violet" to Color(0xFFD500F9)),
                "#FFFFD600" to ("Amber" to Color(0xFFFFD600)),
                "#FFFF4081" to ("Pink" to Color(0xFFFF4081))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorOptions.forEach { (hex, pair) ->
                    val (label, color) = pair
                    val isSelected = (hex.isEmpty() && behavior.textColorOverrideHex.isEmpty()) ||
                            (hex.isNotEmpty() && behavior.textColorOverrideHex.equals(hex, ignoreCase = true))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onBehaviorChange(behavior.copy(textColorOverrideHex = hex)) }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (hex.isEmpty()) MaterialTheme.colorScheme.secondaryContainer else color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hex.isEmpty()) {
                                Text("A", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "selected",
                                    tint = if (hex == "#FFFFFFFF" || hex == "#FFFFD600") Color.Black else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Position & Sizing Tab: Calibrate camera cutout dimensions, offsets, presets, and display feedback.
 */
@Composable
private fun PositionTabContent(
    config: NotchConfigEntity,
    onConfigChange: (NotchConfigEntity) -> Unit,
    onConfigChangeFinished: ((NotchConfigEntity) -> Unit)?,
    isAccessibilityEnabled: Boolean,
    onOpenAccessibility: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Accessibility alert if not enabled
        if (!isAccessibilityEnabled) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAccessibility() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sky Touch Accessibility Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Tap here to enable Accessibility Service so the dynamic notch overlay appears over apps.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // Notch Dimensions & Geometry Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Camera Cutout Sizing & Offsets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fine-tune dimensions to align the dynamic notch around your device's camera.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Width slider
                    SliderItemRow(
                        label = "Width",
                        value = config.widthDp.toFloat(),
                        valueRange = 30f..260f,
                        suffix = "dp",
                        onValueChange = { onConfigChange(config.copy(widthDp = it.roundToInt())) },
                        onValueChangeFinished = { onConfigChangeFinished?.invoke(config.copy(widthDp = config.widthDp)) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Height slider
                    SliderItemRow(
                        label = "Height",
                        value = config.heightDp.toFloat(),
                        valueRange = 10f..100f,
                        suffix = "dp",
                        onValueChange = { onConfigChange(config.copy(heightDp = it.roundToInt())) },
                        onValueChangeFinished = { onConfigChangeFinished?.invoke(config.copy(heightDp = config.heightDp)) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Corner Radius slider
                    SliderItemRow(
                        label = "Corner Radius",
                        value = config.cornerRadiusDp.toFloat(),
                        valueRange = 0f..40f,
                        suffix = "dp",
                        onValueChange = { onConfigChange(config.copy(cornerRadiusDp = it.roundToInt())) },
                        onValueChangeFinished = { onConfigChangeFinished?.invoke(config.copy(cornerRadiusDp = config.cornerRadiusDp)) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Y Offset slider
                    SliderItemRow(
                        label = "Y Offset (Distance from top)",
                        value = config.yOffsetDp.toFloat(),
                        valueRange = 0f..120f,
                        suffix = "dp",
                        onValueChange = { onConfigChange(config.copy(yOffsetDp = it.roundToInt())) },
                        onValueChangeFinished = { onConfigChangeFinished?.invoke(config.copy(yOffsetDp = config.yOffsetDp)) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // X Offset slider
                    SliderItemRow(
                        label = "X Offset (Center / Left / Right)",
                        value = config.xOffsetDp.toFloat(),
                        valueRange = -160f..160f,
                        suffix = "dp",
                        onValueChange = { onConfigChange(config.copy(xOffsetDp = it.roundToInt())) },
                        onValueChangeFinished = { onConfigChangeFinished?.invoke(config.copy(xOffsetDp = config.xOffsetDp)) }
                    )
                }
            }
        }

        // Quick Cutout Presets Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Presets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Common front camera cutout dimensions",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            Triple("Hole Punch", 34, 34),
                            Triple("Pill", 100, 34),
                            Triple("Island", 140, 36),
                            Triple("Dot", 26, 26)
                        )
                        presets.forEach { (name, w, h) ->
                            val isCurrent = config.widthDp == w && config.heightDp == h
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
                                        val updated = config.copy(
                                            widthDp = w,
                                            heightDp = h,
                                            cornerRadiusDp = h / 2
                                        )
                                        onConfigChange(updated)
                                        onConfigChangeFinished?.invoke(updated)
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
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notch Overlay Toggles & Feedback Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Overlay Visibility & Haptics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Draw Dynamic Notch Overlay", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Renders the styled dynamic notch directly over your camera notch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.showVisualOverlay,
                            onCheckedChange = {
                                val updated = config.copy(showVisualOverlay = it)
                                onConfigChange(updated)
                                onConfigChangeFinished?.invoke(updated)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disable in Fullscreen Apps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Hides overlay when games or videos enter immersive fullscreen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.disableInFullScreen,
                            onCheckedChange = {
                                val updated = config.copy(disableInFullScreen = it)
                                onConfigChange(updated)
                                onConfigChangeFinished?.invoke(updated)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Haptic Vibration Strength", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                    .clickable {
                                        val updated = config.copy(vibrationStrength = strength)
                                        onConfigChange(updated)
                                        onConfigChangeFinished?.invoke(updated)
                                    }
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
}

@Composable
private fun SliderItemRow(
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
            Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = "${value.roundToInt()} $suffix", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
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
