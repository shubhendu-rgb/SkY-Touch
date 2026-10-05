package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutoClickConfigEntity
import com.example.service.NotchAccessibilityService
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class AutoClickSubScreen {
    SETTING,
    CUSTOM_SIZE,
    SKINS,
    GUIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickTab(
    viewModel: MainViewModel,
    currentSubScreen: AutoClickSubScreen? = null,
    onSubScreenChange: (AutoClickSubScreen?) -> Unit = {}
) {
    val config by viewModel.repository.autoClickConfigFlow.collectAsState(initial = AutoClickConfigEntity())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var internalSubScreen by remember { mutableStateOf<AutoClickSubScreen?>(null) }
    val activeSubScreen = currentSubScreen ?: internalSubScreen
    val setSubScreen: (AutoClickSubScreen?) -> Unit = { sub ->
        internalSubScreen = sub
        onSubScreenChange(sub)
    }

    LaunchedEffect(currentSubScreen) {
        internalSubScreen = currentSubScreen
    }

    BackHandler(enabled = activeSubScreen != null) {
        setSubScreen(null)
    }

    LaunchedEffect(config.isMenuVisible) {
        if (config.isMenuVisible && NotchAccessibilityService.instance != null) {
            NotchAccessibilityService.instance?.autoClickEngine?.ensureOverlayVisible()
        }
    }

    AnimatedContent(
        targetState = activeSubScreen,
        transitionSpec = {
            if (targetState == null) {
                (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)) { -it / 3 } + fadeIn(animationSpec = tween(220)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { it } + fadeOut(animationSpec = tween(180))
                    )
            } else {
                (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)) { it } + fadeIn(animationSpec = tween(220)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) { -it / 3 } + fadeOut(animationSpec = tween(180))
                    )
            }
        },
        label = "autoclick_subscreen_anim",
        modifier = Modifier.fillMaxSize()
    ) { currentSubScreenState ->
        when (currentSubScreenState) {
            AutoClickSubScreen.SETTING -> {
                SettingSubScreen(
                    config = config,
                    onBack = { setSubScreen(null) },
                    onUpdateConfig = { updated ->
                        scope.launch { viewModel.repository.updateAutoClickConfig(updated) }
                    }
                )
            }
            AutoClickSubScreen.CUSTOM_SIZE -> {
                CustomSizeSubScreen(
                    config = config,
                    onBack = { setSubScreen(null) },
                    onUpdateConfig = { updated ->
                        scope.launch { viewModel.repository.updateAutoClickConfig(updated) }
                    }
                )
            }
            AutoClickSubScreen.SKINS -> {
                SkinsSubScreen(
                    config = config,
                    onBack = { setSubScreen(null) },
                    onSelectSkin = { skin ->
                        scope.launch { viewModel.repository.updateAutoClickConfig(config.copy(selectedSkin = skin)) }
                    }
                )
            }
            AutoClickSubScreen.GUIDE -> {
                GuideSubScreen(
                    onBack = { setSubScreen(null) }
                )
            }
            null -> {
                MainAutoClickScreen(
                    config = config,
                    onToggleStart = {
                        if (NotchAccessibilityService.instance == null) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else {
                            val isActuallyShowing = NotchAccessibilityService.instance?.autoClickEngine?.isOverlayShowing == true
                            val newMenuVis = if (config.isMenuVisible && !isActuallyShowing) {
                                true
                            } else {
                                !config.isMenuVisible
                            }
                            scope.launch {
                                viewModel.repository.updateAutoClickConfig(config.copy(isMenuVisible = newMenuVis))
                                if (newMenuVis) {
                                    NotchAccessibilityService.instance?.autoClickEngine?.ensureOverlayVisible()
                                }
                            }
                        }
                    },
                    onNavigate = { sub -> setSubScreen(sub) },
                    onUpdateConfig = { updated ->
                        scope.launch { viewModel.repository.updateAutoClickConfig(updated) }
                    }
                )
            }
        }
    }
}

// =========================================================================
// MAIN AUTOCLICK SCREEN (Big Start Button, 4 Icons, Switches, Guide)
// =========================================================================
@Composable
private fun MainAutoClickScreen(
    config: AutoClickConfigEntity,
    onToggleStart: () -> Unit,
    onNavigate: (AutoClickSubScreen) -> Unit,
    onUpdateConfig: (AutoClickConfigEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Mode Selector: Single Area | Multi Area | Swipe
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "AutoClick Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Triple("SINGLE", "Single", Icons.Default.TouchApp),
                            Triple("MULTI", "Multi", Icons.Default.List),
                            Triple("SWIPE", "Swipe", Icons.AutoMirrored.Filled.ArrowForward)
                        )
                        for ((mCode, mLabel, mIcon) in modes) {
                            val isSel = config.clickMode == mCode
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    onUpdateConfig(
                                        config.copy(
                                            clickMode = mCode,
                                            isMultiMode = (mCode != "SINGLE")
                                        )
                                    )
                                },
                                label = { Text(mLabel, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    Icon(mIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 1. Big Rounded Start Button
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onToggleStart,
                        shape = CircleShape,
                        modifier = Modifier.size(136.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (config.isMenuVisible)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (config.isMenuVisible) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (config.isMenuVisible) "Stop" else "Start",
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (config.isMenuVisible) "STOP" else "START",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (config.isMenuVisible) "Floating menu bar is active on screen" else "Tap to show floating menu bar",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Four Action Icons (Settings, Custom Size, Skins, Guide)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Settings,
                    label = "Setting",
                    onClick = { onNavigate(AutoClickSubScreen.SETTING) }
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Tune,
                    label = "Custom size",
                    onClick = { onNavigate(AutoClickSubScreen.CUSTOM_SIZE) }
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Palette,
                    label = "Skins",
                    onClick = { onNavigate(AutoClickSubScreen.SKINS) }
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.MenuBook,
                    label = "Guide",
                    onClick = { onNavigate(AutoClickSubScreen.GUIDE) }
                )
            }
        }

        // 3. Feature Options Switches Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Fold menu option
                    SwitchRow(
                        title = "Fold menu",
                        subtitle = "Runtime menu auto collapse",
                        checked = config.foldMenu,
                        onCheckedChange = { onUpdateConfig(config.copy(foldMenu = it)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Landscape option
                    SwitchRow(
                        title = "Show in landscape",
                        subtitle = "Show menu bar in landscape orientation",
                        checked = config.showInLandscape,
                        onCheckedChange = { onUpdateConfig(config.copy(showInLandscape = it)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Minimize floating menu bar option
                    SwitchRow(
                        title = "Minimize floating menu bar",
                        subtitle = "Hide the floating window to the screen edge",
                        checked = config.minimizeToEdge,
                        onCheckedChange = { onUpdateConfig(config.copy(minimizeToEdge = it)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Countdown option
                    SwitchRow(
                        title = "Countdown",
                        subtitle = "Show 3-second countdown before auto-click starts",
                        checked = config.countdownEnabled,
                        onCheckedChange = { onUpdateConfig(config.copy(countdownEnabled = it)) }
                    )
                }
            }
        }

        // 4. Quick Guide Banner
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(AutoClickSubScreen.GUIDE) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "How to use AutoClick Features",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "Learn about target reticles, swipe gestures, and floating menu controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open Guide",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// =========================================================================
// 1. SETTING SUB-SCREEN (Image 3: Delay, Sliding Time, Stop Conditions)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSubScreen(
    config: AutoClickConfigEntity,
    onBack: () -> Unit,
    onUpdateConfig: (AutoClickConfigEntity) -> Unit
) {
    var delayInput by remember(config.delayMs) { mutableStateOf(config.delayMs.toString()) }
    var slidingInput by remember(config.slidingTimeMs) { mutableStateOf(config.slidingTimeMs.toString()) }
    var stopHours by remember(config.stopHours) { mutableStateOf(config.stopHours.toString()) }
    var stopMinutes by remember(config.stopMinutes) { mutableStateOf(config.stopMinutes.toString()) }
    var stopSeconds by remember(config.stopSeconds) { mutableStateOf(config.stopSeconds.toString()) }
    var loopCount by remember(config.stopLoopCount) { mutableStateOf(config.stopLoopCount.toString()) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Setting", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Default setting notification card (light blue tinted)
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Default Setting",
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Only newly created configuration settings are valid, saved configurations need to be edited in the hover menu",
                            color = Color(0xFF0D47A1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Delay card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Delay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "The interval between the next click",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = delayInput,
                                onValueChange = {
                                    delayInput = it
                                    val v = it.toLongOrNull()
                                    if (v != null && v >= 100L) {
                                        onUpdateConfig(config.copy(delayMs = v, intervalMs = v))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("ms", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp)) }
                            )
                            Button(
                                onClick = {
                                    val current = config.delayMs
                                    val next = (current - 50L).coerceAtLeast(100L)
                                    delayInput = next.toString()
                                    onUpdateConfig(config.copy(delayMs = next, intervalMs = next))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text("-50")
                            }
                            Button(
                                onClick = {
                                    val current = config.delayMs
                                    val next = current + 50L
                                    delayInput = next.toString()
                                    onUpdateConfig(config.copy(delayMs = next, intervalMs = next))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text("+50")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Safety Rule: Click intervals less than 100 milliseconds are restricted to prevent screen touches from locking up and ensure you can stop auto-click anytime",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFA000)
                        )
                    }
                }
            }

            // Continuous Sliding Time card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Continuous Sliding Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "The interval time needs to be greater than 300 milliseconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = slidingInput,
                                onValueChange = {
                                    slidingInput = it
                                    val v = it.toLongOrNull()
                                    if (v != null && v >= 300L) {
                                        onUpdateConfig(config.copy(slidingTimeMs = v))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("ms", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp)) }
                            )
                            Button(
                                onClick = {
                                    val current = config.slidingTimeMs
                                    val next = (current - 100L).coerceAtLeast(300L)
                                    slidingInput = next.toString()
                                    onUpdateConfig(config.copy(slidingTimeMs = next))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text("-100")
                            }
                            Button(
                                onClick = {
                                    val current = config.slidingTimeMs
                                    val next = current + 100L
                                    slidingInput = next.toString()
                                    onUpdateConfig(config.copy(slidingTimeMs = next))
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text("+100")
                            }
                        }
                    }
                }
            }

            // Stop Configuration Conditions card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Stop Configuration Conditions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))

                        // 1. Infinite loop
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateConfig(config.copy(stopConditionType = "INFINITE")) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = config.stopConditionType == "INFINITE",
                                onClick = { onUpdateConfig(config.copy(stopConditionType = "INFINITE")) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Infinite Loop", style = MaterialTheme.typography.bodyLarge)
                        }

                        // 2. Time Stop
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateConfig(config.copy(stopConditionType = "TIME")) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = config.stopConditionType == "TIME",
                                onClick = { onUpdateConfig(config.copy(stopConditionType = "TIME")) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = stopHours,
                                    onValueChange = {
                                        stopHours = it
                                        val h = it.toIntOrNull() ?: 0
                                        onUpdateConfig(config.copy(stopHours = h, stopConditionType = "TIME"))
                                    },
                                    modifier = Modifier.width(68.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    trailingIcon = { Text("h", style = MaterialTheme.typography.labelMedium) }
                                )
                                OutlinedTextField(
                                    value = stopMinutes,
                                    onValueChange = {
                                        stopMinutes = it
                                        val m = it.toIntOrNull() ?: 5
                                        onUpdateConfig(config.copy(stopMinutes = m, stopConditionType = "TIME"))
                                    },
                                    modifier = Modifier.width(68.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    trailingIcon = { Text("m", style = MaterialTheme.typography.labelMedium) }
                                )
                                OutlinedTextField(
                                    value = stopSeconds,
                                    onValueChange = {
                                        stopSeconds = it
                                        val s = it.toIntOrNull() ?: 0
                                        onUpdateConfig(config.copy(stopSeconds = s, stopConditionType = "TIME"))
                                    },
                                    modifier = Modifier.width(68.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    trailingIcon = { Text("s", style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }

                        // 3. Loop times
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateConfig(config.copy(stopConditionType = "LOOPS")) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = config.stopConditionType == "LOOPS",
                                onClick = { onUpdateConfig(config.copy(stopConditionType = "LOOPS")) }
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = loopCount,
                                onValueChange = {
                                    loopCount = it
                                    val l = it.toIntOrNull() ?: 10
                                    onUpdateConfig(config.copy(stopLoopCount = l, stopConditionType = "LOOPS"))
                                },
                                modifier = Modifier.width(90.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Loop Times", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // Countdown Option Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SwitchRow(
                            title = "Countdown",
                            subtitle = "Show visual number countdown and vibration before starting auto-click",
                            checked = config.countdownEnabled,
                            onCheckedChange = { onUpdateConfig(config.copy(countdownEnabled = it)) }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =========================================================================
// 2. CUSTOM SIZE SUB-SCREEN (Image 2: Target Size, Transparency, Menu Size)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomSizeSubScreen(
    config: AutoClickConfigEntity,
    onBack: () -> Unit,
    onUpdateConfig: (AutoClickConfigEntity) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Custom size", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Target size section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Target size",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(Modifier.height(16.dp))

                        // Target live preview
                        val previewDp = when (config.targetSize) {
                            "SMALL" -> 44.dp
                            "BIG" -> 72.dp
                            else -> 56.dp
                        }
                        Box(
                            modifier = Modifier
                                .height(80.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TargetSkinCanvas(
                                skin = config.selectedSkin,
                                size = previewDp,
                                alpha = config.targetTransparency,
                                label = "1"
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        // 3-step slider: Small, Middle, Big
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Small", style = MaterialTheme.typography.bodySmall)
                            Text("Middle", style = MaterialTheme.typography.bodySmall)
                            Text("Big", style = MaterialTheme.typography.bodySmall)
                        }
                        val sliderPos = when (config.targetSize) {
                            "SMALL" -> 0f
                            "BIG" -> 2f
                            else -> 1f
                        }
                        Slider(
                            value = sliderPos,
                            onValueChange = { pos ->
                                val newSize = when (pos.toInt()) {
                                    0 -> "SMALL"
                                    2 -> "BIG"
                                    else -> "MIDDLE"
                                }
                                onUpdateConfig(config.copy(targetSize = newSize))
                            },
                            steps = 1,
                            valueRange = 0f..2f
                        )
                    }
                }
            }

            // 2. Target transparency section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Target transparency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${(config.targetTransparency * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .height(80.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TargetSkinCanvas(
                                skin = config.selectedSkin,
                                size = 56.dp,
                                alpha = config.targetTransparency,
                                label = "1"
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        Slider(
                            value = config.targetTransparency,
                            onValueChange = { onUpdateConfig(config.copy(targetTransparency = it)) },
                            valueRange = 0.2f..1.0f
                        )
                    }
                }
            }

            // 3. Menu size section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Menu size",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(Modifier.height(16.dp))

                        // Menu pill preview (matches Image 2)
                        FloatingMenuPreview(menuSize = config.menuSize)
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Small", style = MaterialTheme.typography.bodySmall)
                            Text("Middle", style = MaterialTheme.typography.bodySmall)
                            Text("Big", style = MaterialTheme.typography.bodySmall)
                        }
                        val menuSliderPos = when (config.menuSize) {
                            "SMALL" -> 0f
                            "BIG" -> 2f
                            else -> 1f
                        }
                        Slider(
                            value = menuSliderPos,
                            onValueChange = { pos ->
                                val newSize = when (pos.toInt()) {
                                    0 -> "SMALL"
                                    2 -> "BIG"
                                    else -> "MIDDLE"
                                }
                                onUpdateConfig(config.copy(menuSize = newSize))
                            },
                            steps = 1,
                            valueRange = 0f..2f
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =========================================================================
// 3. SKINS SUB-SCREEN (Image 1: 12 Reticles Grid + Top Preview)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkinsSubScreen(
    config: AutoClickConfigEntity,
    onBack: () -> Unit,
    onSelectSkin: (String) -> Unit
) {
    val allSkins = remember {
        listOf(
            "DEFAULT" to "Default",
            "RED_CROSS" to "Red Cross",
            "GREEN_TARGET" to "Green Target",
            "ORANGE_BRACKET" to "Orange Bracket",
            "BLUE_LIGHTBULB" to "Blue Sparkle",
            "RED_STAR" to "Red Star",
            "GREEN_HEX" to "Green Hex",
            "ORANGE_DIAMOND" to "Orange Diamond",
            "ORANGE_SUN" to "Orange Sun",
            "RAINBOW" to "Rainbow",
            "CYAN_SNOW" to "Snowflake",
            "YELLOW_MOON" to "Yellow Moon"
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Skins", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Top Preview Area (Single target on left, Swipe on right)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Single target
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TargetSkinCanvas(skin = config.selectedSkin, size = 52.dp, label = "1")
                        Spacer(Modifier.height(6.dp))
                        Text("Single click", style = MaterialTheme.typography.labelSmall)
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.7f)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )

                    // Right: Swipe targets connected by bar
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TargetSkinCanvas(skin = config.selectedSkin, size = 42.dp, label = "1")
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(4.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            )
                            TargetSkinCanvas(skin = config.selectedSkin, size = 42.dp, label = "1E")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Slide click", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 12 Skins in 4-column Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allSkins, key = { it.first }) { (skinKey, skinName) ->
                    val isSelected = config.selectedSkin == skinKey
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSkin(skinKey) }
                            .then(
                                if (isSelected)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                else
                                    Modifier
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            TargetSkinCanvas(skin = skinKey, size = 44.dp, label = "1")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                skinName,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. GUIDE SUB-SCREEN (Step-by-Step Walkthrough)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideSubScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Guide", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "How to use AutoClick Features",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Follow these simple steps to automate taps, clicks, and gestures on any application or game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                GuideStepCard(
                    stepNumber = "1",
                    title = "Enable Accessibility Permission",
                    desc = "AutoClick needs Android Accessibility Service permission to simulate taps and gestures over other apps. Tap START and turn on Notch Assistant in Accessibility Settings."
                )
            }

            item {
                GuideStepCard(
                    stepNumber = "2",
                    title = "Start the Floating Menu Bar",
                    desc = "Tap the big rounded START button in the AutoClick Studio. A horizontal floating pill menu will appear on top of your screen."
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Floating Menu Controls (8 Icons)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        FloatingMenuPreview(menuSize = "MIDDLE")
                        Spacer(Modifier.height(12.dp))
                        IconGuideRow(iconColor = Color(0xFF3884FF), name = "Play / Pause", desc = "Starts or pauses the automated click execution.")
                        IconGuideRow(iconColor = Color(0xFF00E676), name = "Plus (+)", desc = "Adds a new numbered target reticle to your screen.")
                        IconGuideRow(iconColor = Color(0xFFFF5252), name = "Minus (-)", desc = "Removes the most recently added target reticle.")
                        IconGuideRow(iconColor = Color.White, name = "Save Preset", desc = "Saves your current target placement and timing configurations.")
                        IconGuideRow(iconColor = Color.White, name = "Eye Toggle", desc = "Hides or shows the target reticles without stopping.")
                        IconGuideRow(iconColor = Color.White, name = "Settings", desc = "Opens a floating mini popup to adjust delays and stop conditions.")
                        IconGuideRow(iconColor = Color.White, name = "4-Directional Arrow", desc = "Touch and drag to reposition the floating menu anywhere.")
                        IconGuideRow(iconColor = Color(0xFFFF5252), name = "Close", desc = "Closes the floating menu and resets targets to last saved preset.")
                    }
                }
            }

            item {
                GuideStepCard(
                    stepNumber = "3",
                    title = "Position & Customize Targets",
                    desc = "Drag any numbered target reticle anywhere on the screen. Tap a target while idle to switch gesture types (Single Tap, Double Tap, Long Press, or Swipe)."
                )
            }

            item {
                GuideStepCard(
                    stepNumber = "4",
                    title = "Delays, Sliding Times & Stop Rules",
                    desc = "Open Settings to configure click delay (min 50ms), continuous sliding time (min 300ms), and choose stop conditions: Infinite Loop, Timer countdown (h:m:s), or fixed Loop Count."
                )
            }

            item {
                GuideStepCard(
                    stepNumber = "5",
                    title = "Custom Sizes & 12 Reticle Skins",
                    desc = "Select from 12 distinct reticle styles in Skins (Crosshair, Green Target, Viewfinder, Lightbulb, Snowflake, Moon, etc.) and scale target size, menu size, and transparency in Custom Size."
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    title: String,
    desc: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stepNumber,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IconGuideRow(
    iconColor: Color,
    name: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(iconColor, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text("$name: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =========================================================================
// FLOATING MENU PILL PREVIEW COMPONENT (Matches Image 2)
// =========================================================================
@Composable
private fun FloatingMenuPreview(
    menuSize: String,
    modifier: Modifier = Modifier
) {
    val scale = when (menuSize) {
        "SMALL" -> 0.85f
        "BIG" -> 1.15f
        else -> 1.0f
    }
    val heightDp = (46 * scale).dp
    val iconSizeDp = (22 * scale).dp

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF212328))
            .border(1.5.dp, Color(0xFF4A4F5D), CircleShape)
            .padding(horizontal = (12 * scale).dp, vertical = (4 * scale).dp)
            .height(heightDp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Play (Blue)
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFF3884FF),
                modifier = Modifier.size(iconSizeDp)
            )
            // 2. Plus (Green)
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = Color(0xFF00E676),
                modifier = Modifier.size(iconSizeDp)
            )
            // 3. Minus (Red)
            Icon(
                Icons.Default.Remove,
                contentDescription = "Remove",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(iconSizeDp)
            )
            // 4. Save (White)
            Icon(
                Icons.Default.Save,
                contentDescription = "Save",
                tint = Color.White,
                modifier = Modifier.size(iconSizeDp)
            )
            // 5. Eye (White)
            Icon(
                Icons.Default.Visibility,
                contentDescription = "Eye",
                tint = Color.White,
                modifier = Modifier.size(iconSizeDp)
            )
            // 6. Settings / Nut (White)
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(iconSizeDp)
            )
            // 7. Move (White 4-directional arrow)
            Icon(
                Icons.Default.OpenWith,
                contentDescription = "Move",
                tint = Color.White,
                modifier = Modifier.size(iconSizeDp)
            )
            // 8. Close (Red)
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(iconSizeDp)
            )
        }
    }
}

// =========================================================================
// CANVAS DRAWING COMPOSABLE FOR THE 12 RETICLE SKINS (Matches Image 1)
// =========================================================================
@Composable
fun TargetSkinCanvas(
    skin: String,
    size: Dp,
    alpha: Float = 1.0f,
    label: String = "1",
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer(alpha = alpha.coerceIn(0.2f, 1.0f))
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = (w / 2f) - 2f

        when (skin) {
            "RED_CROSS" -> {
                // Black ring
                drawCircle(color = Color.Black, radius = r * 0.6f, center = Offset(cx, cy), style = Stroke(width = 4f))
                // 4 red ticks
                val tickColor = Color(0xFFE53935)
                drawLine(tickColor, Offset(cx, cy - r * 0.6f), Offset(cx, cy - r), strokeWidth = 4f)
                drawLine(tickColor, Offset(cx, cy + r * 0.6f), Offset(cx, cy + r), strokeWidth = 4f)
                drawLine(tickColor, Offset(cx - r * 0.6f, cy), Offset(cx - r, cy), strokeWidth = 4f)
                drawLine(tickColor, Offset(cx + r * 0.6f, cy), Offset(cx + r, cy), strokeWidth = 4f)
                // Center disc
                drawCircle(color = Color(0xCC000000), radius = r * 0.35f, center = Offset(cx, cy))
            }
            "GREEN_TARGET" -> {
                drawCircle(color = Color(0xFF43A047), radius = r * 0.9f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = r * 0.5f, center = Offset(cx, cy), style = Stroke(width = 3.5f))
                drawCircle(color = Color.Black, radius = r * 0.25f, center = Offset(cx, cy))
            }
            "ORANGE_BRACKET" -> {
                val bColor = Color(0xFFFF6D00)
                val pad = r * 0.2f
                val bLen = r * 0.35f
                // Top-left
                drawLine(bColor, Offset(cx - r + pad, cy - r + pad), Offset(cx - r + pad + bLen, cy - r + pad), strokeWidth = 4f)
                drawLine(bColor, Offset(cx - r + pad, cy - r + pad), Offset(cx - r + pad, cy - r + pad + bLen), strokeWidth = 4f)
                // Top-right
                drawLine(bColor, Offset(cx + r - pad, cy - r + pad), Offset(cx + r - pad - bLen, cy - r + pad), strokeWidth = 4f)
                drawLine(bColor, Offset(cx + r - pad, cy - r + pad), Offset(cx + r - pad, cy - r + pad + bLen), strokeWidth = 4f)
                // Bottom-left
                drawLine(bColor, Offset(cx - r + pad, cy + r - pad), Offset(cx - r + pad + bLen, cy + r - pad), strokeWidth = 4f)
                drawLine(bColor, Offset(cx - r + pad, cy + r - pad), Offset(cx - r + pad, cy + r - pad - bLen), strokeWidth = 4f)
                // Bottom-right
                drawLine(bColor, Offset(cx + r - pad, cy + r - pad), Offset(cx + r - pad - bLen, cy + r - pad), strokeWidth = 4f)
                drawLine(bColor, Offset(cx + r - pad, cy + r - pad), Offset(cx + r - pad, cy + r - pad - bLen), strokeWidth = 4f)
                drawCircle(color = Color(0xCCFF6D00), radius = r * 0.35f, center = Offset(cx, cy))
            }
            "BLUE_LIGHTBULB" -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF448AFF), Color(0xFF1565C0)), center = Offset(cx, cy), radius = r),
                    radius = r * 0.85f,
                    center = Offset(cx, cy)
                )
                drawCircle(Color.White, radius = 3f, center = Offset(cx - r * 0.4f, cy - r * 0.4f))
                drawCircle(Color.White, radius = 3.5f, center = Offset(cx + r * 0.4f, cy - r * 0.2f))
            }
            "RED_STAR" -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0xFFC62828)), center = Offset(cx, cy), radius = r),
                    radius = r * 0.85f,
                    center = Offset(cx, cy)
                )
            }
            "GREEN_HEX" -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF69F0AE), Color(0xFF2E7D32)), center = Offset(cx, cy), radius = r),
                    radius = r * 0.85f,
                    center = Offset(cx, cy)
                )
            }
            "ORANGE_DIAMOND" -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFFB74D), Color(0xFFE65100)), center = Offset(cx, cy), radius = r),
                    radius = r * 0.85f,
                    center = Offset(cx, cy)
                )
            }
            "ORANGE_SUN" -> {
                drawCircle(color = Color(0xFFFF6D00), radius = r * 0.45f, center = Offset(cx, cy))
                for (i in 0 until 8) {
                    val angle = (i * Math.PI / 4)
                    val dotX = cx + (r * 0.75f * cos(angle)).toFloat()
                    val dotY = cy + (r * 0.75f * sin(angle)).toFloat()
                    drawCircle(color = Color(0xFFFF6D00), radius = r * 0.12f, center = Offset(dotX, dotY))
                }
            }
            "RAINBOW" -> {
                drawArc(
                    color = Color(0xFF2979FF),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.8f, cy - r * 0.8f),
                    size = Size(r * 1.6f, r * 1.6f),
                    style = Stroke(width = 4f)
                )
                drawArc(
                    color = Color(0xFFFF1744),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.5f, cy - r * 0.5f),
                    size = Size(r * 1.0f, r * 1.0f),
                    style = Stroke(width = 4f)
                )
            }
            "CYAN_SNOW" -> {
                val sColor = Color(0xFF00E5FF)
                for (i in 0 until 6) {
                    val angle = (i * Math.PI / 3)
                    val ex = cx + (r * 0.85f * cos(angle)).toFloat()
                    val ey = cy + (r * 0.85f * sin(angle)).toFloat()
                    drawLine(sColor, Offset(cx, cy), Offset(ex, ey), strokeWidth = 3.5f)
                }
            }
            "YELLOW_MOON" -> {
                drawCircle(color = Color(0xFFFFD600), radius = r * 0.8f, center = Offset(cx, cy))
                drawCircle(color = Color(0xFF212328), radius = r * 0.7f, center = Offset(cx + r * 0.35f, cy - r * 0.2f))
            }
            else -> { // DEFAULT: Black ring with 4 blue ticks
                drawCircle(color = Color.Black, radius = r * 0.6f, center = Offset(cx, cy), style = Stroke(width = 4f))
                val tickColor = Color(0xFF2979FF)
                drawLine(tickColor, Offset(cx, cy - r * 0.6f), Offset(cx, cy - r), strokeWidth = 4f)
                drawLine(tickColor, Offset(cx, cy + r * 0.6f), Offset(cx, cy + r), strokeWidth = 4f)
                drawLine(tickColor, Offset(cx - r * 0.6f, cy), Offset(cx - r, cy), strokeWidth = 4f)
                drawLine(tickColor, Offset(cx + r * 0.6f, cy), Offset(cx + r, cy), strokeWidth = 4f)
                drawCircle(color = Color(0xCC000000), radius = r * 0.35f, center = Offset(cx, cy))
            }
        }
    }
}
