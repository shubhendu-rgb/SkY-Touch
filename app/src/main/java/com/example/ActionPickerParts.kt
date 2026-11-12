package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val NavColor = Color(0xFF4C8DFF)
private val MusicColor = Color(0xFFFF5C8A)
private val BrightColor = Color(0xFFFFB300)
private val WallColor = Color(0xFF8E6CFF)
private val CameraColor = Color(0xFF26C6A6)
private val AppsColor = Color(0xFF42A5F5)
private val ShortcutColor = Color(0xFFFF7043)
private val WebColor = Color(0xFF29B6F6)
private val DialColor = Color(0xFF66BB6A)
private val NotesColor = Color(0xFFFFCA28)
private val WidgetColor = Color(0xFF9575CD)
private val NoneColor = Color(0xFF90A4AE)
private val AutoClickColor = Color(0xFFEF5350)
private val FeaturesColor = Color(0xFF5C6BC0)

/** One option inside a category screen. [label] is the short caption, [fullLabel] is what gets stored. */
private data class PickerOption(
    val type: String,
    val label: String,
    val fullLabel: String,
    val icon: ImageVector
)

private val navigationOptions = listOf(
    PickerOption("BACK", "Back", "Go Back", Icons.AutoMirrored.Filled.ArrowBack),
    PickerOption("HOME", "Home", "Go Home", Icons.Filled.Home),
    PickerOption("RECENTS", "Recents", "Open Recents Overview", Icons.Filled.Layers),
    PickerOption("NOTIFICATIONS", "Notifications", "Open Notifications Shade", Icons.Filled.Notifications),
    PickerOption("QUICK_SETTINGS", "Quick Settings", "Open Quick Settings Panel", Icons.Filled.Settings),
    PickerOption("SCREENSHOT", "Screenshot", "Take Instant Screenshot", Icons.Filled.Screenshot)
)

private val musicOptions = listOf(
    PickerOption("MEDIA_PLAY_PAUSE", "Play / Pause", "Media Play / Pause", Icons.Filled.PlayArrow),
    PickerOption("MEDIA_NEXT", "Next Track", "Next Track", Icons.Filled.SkipNext),
    PickerOption("MEDIA_PREVIOUS", "Previous Track", "Previous Track", Icons.Filled.SkipPrevious),
    PickerOption("VOLUME_UP", "Volume Up", "Raise Music Volume", Icons.Filled.VolumeUp),
    PickerOption("VOLUME_DOWN", "Volume Down", "Lower Music Volume", Icons.Filled.VolumeDown),
    PickerOption("TOGGLE_EQUALIZER", "Equalizer On / Off", "Turn Equalizer on / off", Icons.Filled.GraphicEq)
)

private val brightnessOptions = listOf(
    PickerOption("BRIGHTNESS_UP", "Brighter", "Increase Brightness", Icons.Filled.BrightnessHigh),
    PickerOption("BRIGHTNESS_DOWN", "Dimmer", "Decrease Brightness", Icons.Filled.BrightnessLow)
)

private val wallpaperOptions = listOf(
    PickerOption("WALLPAPER_NEXT", "Next", "Next Wallpaper", Icons.Filled.Wallpaper),
    PickerOption("WALLPAPER_PREVIOUS", "Previous", "Previous Wallpaper", Icons.Filled.SkipPrevious),
    PickerOption("WALLPAPER_RANDOM", "Random", "Random Wallpaper", Icons.Filled.Shuffle),
    PickerOption("WALLPAPER_HOME_NEXT", "Next Home", "Next Home Wallpaper", Icons.Filled.Home),
    PickerOption("WALLPAPER_LOCK_NEXT", "Next Lock", "Next Lock Screen Wallpaper", Icons.Filled.Lock),
    PickerOption("WALLPAPER_IMAGES_NEXT", "Next (Images)", "Next Wallpaper (Selected Images)", Icons.Filled.Image),
    PickerOption("WALLPAPER_FOLDER_NEXT", "Next (Folder)", "Next Wallpaper (Folder)", Icons.Filled.Folder),
    PickerOption("LIVE_WALLPAPER_NEXT", "Next Live", "Next Live Wallpaper", Icons.Filled.Movie),
    PickerOption("WALLPAPER_CHANGER_TOGGLE", "Changer On / Off", "Turn Wallpaper Changer on / off", Icons.Filled.PowerSettingsNew)
)

private val cameraOptions = listOf(
    PickerOption("FLASHLIGHT", "Flashlight", "Toggle Camera Flashlight", Icons.Filled.FlashlightOn),
    PickerOption("SPY_CAM_BACK", "Record Back", "Spy Cam (Silent Back Record)", Icons.Filled.CameraRear),
    PickerOption("SPY_CAM_FRONT", "Record Front", "Spy Cam (Silent Front Record)", Icons.Filled.CameraFront),
    PickerOption("STOP_SPY_CAM", "Stop Recording", "Stop silent recording", Icons.Filled.StopCircle)
)

private val autoClickOptions = listOf(
    PickerOption("AUTOCLICK_MENU_TOGGLE", "Show / Hide Menu", "Show / hide Auto Click menu", Icons.Filled.Mouse),
    PickerOption("AUTOCLICK_START", "Start", "Start Auto Click", Icons.Filled.PlayArrow),
    PickerOption("AUTOCLICK_STOP", "Stop", "Stop Auto Click", Icons.Filled.Stop),
    PickerOption("AUTOCLICK_TOGGLE", "Start / Stop", "Start / stop Auto Click", Icons.Filled.TouchApp)
)

private val featureOptions = listOf(
    PickerOption("TOGGLE_NOTCH", "Notch Gestures", "Turn notch gestures on / off", Icons.Filled.PhoneAndroid),
    PickerOption("TOGGLE_SIDE_DECK", "Side Deck", "Turn Side Deck on / off", Icons.Filled.ViewSidebar),
    PickerOption("TOGGLE_OTP", "Code Detector", "Turn Code Detector on / off", Icons.Filled.QrCodeScanner),
    PickerOption("TOGGLE_TEXT_ASSISTANT", "Text Assistant", "Turn Text Assistant on / off", Icons.Filled.AutoAwesome)
)

private fun categoryOf(actionType: String): String = when (actionType) {
    "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "SCREENSHOT" -> "NAV"
    "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS", "VOLUME_UP", "VOLUME_DOWN", "TOGGLE_EQUALIZER" -> "MUSIC"
    "BRIGHTNESS_UP", "BRIGHTNESS_DOWN", "CUSTOM_BRIGHTNESS" -> "BRIGHT"
    "WALLPAPER_NEXT", "WALLPAPER_PREVIOUS", "WALLPAPER_RANDOM", "WALLPAPER_HOME_NEXT", "WALLPAPER_LOCK_NEXT",
    "WALLPAPER_IMAGES_NEXT", "WALLPAPER_FOLDER_NEXT", "TEMP_LOCK_WALLPAPER",
    "LIVE_WALLPAPER_NEXT", "WALLPAPER_CHANGER_TOGGLE" -> "WALL"
    "FLASHLIGHT", "SPY_CAM_BACK", "SPY_CAM_FRONT", "STOP_SPY_CAM" -> "CAMERA"
    "AUTOCLICK_MENU_TOGGLE", "AUTOCLICK_START", "AUTOCLICK_STOP", "AUTOCLICK_TOGGLE" -> "AUTOCLICK"
    "TOGGLE_NOTCH", "TOGGLE_SIDE_DECK", "TOGGLE_OTP", "TOGGLE_TEXT_ASSISTANT" -> "FEATURES"
    "LAUNCH_APP" -> "APPS"
    "SHORTCUT" -> "SHORTCUTS"
    "OPEN_URL" -> "WEB"
    "QUICK_DIAL" -> "DIAL"
    else -> ""
}

fun pickerScreenTitle(screen: String): String = when (screen) {
    "NAV" -> "Navigation"
    "MUSIC" -> "Music & Sound"
    "BRIGHT" -> "Brightness"
    "WALL" -> "Wallpaper"
    "CAMERA" -> "Camera & Recording"
    "AUTOCLICK" -> "Auto Click"
    "FEATURES" -> "SkY Touch Features"
    "APPS" -> "Open App"
    "SHORTCUTS" -> "App Shortcuts"
    "WEB" -> "Web Link"
    "DIAL" -> "Quick Dial"
    "LOCKWP" -> "Lock Wallpaper 2 Min"
    else -> "Actions"
}

private fun pickerScreenIcon(screen: String): Pair<ImageVector, Color> = when (screen) {
    "NAV" -> Icons.Filled.Navigation to NavColor
    "MUSIC" -> Icons.Filled.MusicNote to MusicColor
    "BRIGHT" -> Icons.Filled.BrightnessMedium to BrightColor
    "WALL", "LOCKWP" -> Icons.Filled.Wallpaper to WallColor
    "CAMERA" -> Icons.Filled.PhotoCamera to CameraColor
    "AUTOCLICK" -> Icons.Filled.Mouse to AutoClickColor
    "FEATURES" -> Icons.Filled.PhoneAndroid to FeaturesColor
    "APPS" -> Icons.Filled.Apps to AppsColor
    "SHORTCUTS" -> Icons.Filled.AppShortcut to ShortcutColor
    "WEB" -> Icons.Filled.Language to WebColor
    "DIAL" -> Icons.Filled.Call to DialColor
    else -> Icons.Filled.Apps to NoneColor
}

/** Back arrow + icon + title shown above a category or form. */
@Composable
fun PickerScreenHeader(title: String, onBack: () -> Unit) {
    // The icon of the title comes from the category, found again from the title
    val screen = listOf("NAV", "MUSIC", "BRIGHT", "WALL", "CAMERA", "AUTOCLICK", "FEATURES", "APPS", "SHORTCUTS", "WEB", "DIAL", "LOCKWP")
        .firstOrNull { pickerScreenTitle(it) == title } ?: ""
    val (icon, color) = pickerScreenIcon(screen)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to categories",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PickerHomeRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) color.copy(alpha = 0.7f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Current action", tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * First screen of the action picker: one single list with every category, followed by the actions that
 * do not belong to a category. Categories open their options, the rest is picked (or opened) directly.
 */
@Composable
fun PickerHomeList(
    currentAction: String,
    onOpen: (String) -> Unit,
    onPick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = categoryOf(currentAction)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            PickerHomeRow(Icons.Filled.Navigation, NavColor, "Navigation", "Back, Home, Recents, Notifications, Screenshot...", current == "NAV", true) { onOpen("NAV") }
        }
        item {
            PickerHomeRow(Icons.Filled.MusicNote, MusicColor, "Music & Sound", "Play, pause, next, previous, volume and equalizer", current == "MUSIC", true) { onOpen("MUSIC") }
        }
        item {
            PickerHomeRow(Icons.Filled.BrightnessMedium, BrightColor, "Brightness", "Brighter, dimmer or an exact level", current == "BRIGHT", true) { onOpen("BRIGHT") }
        }
        item {
            PickerHomeRow(Icons.Filled.Wallpaper, WallColor, "Wallpaper", "Change wallpapers, live wallpaper, lock screen picture for 2 min", current == "WALL", true) { onOpen("WALL") }
        }
        item {
            PickerHomeRow(Icons.Filled.PhotoCamera, CameraColor, "Camera & Recording", "Flashlight and silent video recording", current == "CAMERA", true) { onOpen("CAMERA") }
        }
        item {
            PickerHomeRow(Icons.Filled.Mouse, AutoClickColor, "Auto Click", "Show the menu, start and stop automatic tapping", current == "AUTOCLICK", true) { onOpen("AUTOCLICK") }
        }
        item {
            PickerHomeRow(Icons.Filled.PhoneAndroid, FeaturesColor, "SkY Touch Features", "Turn Notch Gestures, Side Deck, Code Detector and Text Assistant on or off", current == "FEATURES", true) { onOpen("FEATURES") }
        }
        item {
            PickerHomeRow(Icons.Filled.Apps, AppsColor, "Open App", "Launch any installed app", current == "APPS", true) { onOpen("APPS") }
        }
        item {
            PickerHomeRow(Icons.Filled.AppShortcut, ShortcutColor, "App Shortcuts", "Jump into a screen or chat inside an app", current == "SHORTCUTS", true) { onOpen("SHORTCUTS") }
        }
        item {
            PickerHomeRow(Icons.Filled.Language, WebColor, "Web Link", "Open a website in your default browser", current == "WEB", true) { onOpen("WEB") }
        }
        item {
            PickerHomeRow(Icons.Filled.Call, DialColor, "Quick Dial", "Call a chosen contact", current == "DIAL", true) { onOpen("DIAL") }
        }
        item {
            PickerHomeRow(Icons.Filled.StickyNote2, NotesColor, "Quick Notes", "Slide-down notes panel", currentAction == "OPEN_NOTES", false) {
                onPick("OPEN_NOTES", "Open quick notes")
            }
        }
        item {
            PickerHomeRow(Icons.Filled.Widgets, WidgetColor, "Widget Editor", "Open the widget editor", currentAction == "OPEN_EDITOR", false) {
                onPick("OPEN_EDITOR", "Open Widget Editor")
            }
        }
        item {
            PickerHomeRow(Icons.Filled.Block, NoneColor, "No Action", "Turn this gesture off", currentAction == "NONE", false) {
                onPick("NONE", "Disabled (No Action)")
            }
        }
    }
}

@Composable
private fun PickerTile(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) color.copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Icon grid with the options of one category (Navigation, Music, Brightness, Wallpaper, Camera). */
@Composable
fun PickerOptionsGrid(
    screen: String,
    isHold: Boolean,
    currentAction: String,
    customBrightness: Float,
    onCustomBrightnessChange: (Float) -> Unit,
    isWriteGranted: Boolean,
    onGrantWrite: () -> Unit,
    onOpen: (String) -> Unit,
    onPick: (type: String, label: String, extra: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val (_, color) = pickerScreenIcon(screen)
    val options = when (screen) {
        "NAV" -> navigationOptions
        "MUSIC" -> musicOptions
        "BRIGHT" -> brightnessOptions
        "WALL" -> if (isHold) emptyList() else wallpaperOptions
        "CAMERA" -> cameraOptions
        "AUTOCLICK" -> autoClickOptions
        "FEATURES" -> featureOptions
        else -> emptyList()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (isHold && (screen == "MUSIC" || screen == "BRIGHT")) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Volume and brightness repeat while you keep holding, and speed up after 2 seconds.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (screen == "FEATURES") {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Each tile switches the feature on if it is off, and off if it is on. If you turn Notch Gestures off, turn them on again from the Quick Settings tile, the Side Deck or the app.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (screen == "WALL" && isHold) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Wallpaper changes are available for tap and swipe gestures. The lock screen picture below works for any gesture.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(options, key = { it.type }) { option ->
            PickerTile(
                label = option.label,
                icon = option.icon,
                color = color,
                selected = currentAction == option.type,
                onClick = { onPick(option.type, option.fullLabel, null) }
            )
        }

        if (screen == "WALL") {
            item {
                PickerTile(
                    label = "Lock Pic 2 min",
                    icon = Icons.Filled.Lock,
                    color = color,
                    selected = currentAction == "TEMP_LOCK_WALLPAPER",
                    onClick = { onOpen("LOCKWP") }
                )
            }
        }

        if (screen == "BRIGHT") {
            if (!isWriteGranted) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Permission Needed", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                Text(
                                    "Allow 'Modify system settings' for brightness gestures to work.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onGrantWrite,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentAction == "CUSTOM_BRIGHTNESS") color.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.BrightnessMedium, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Exact level", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                "${customBrightness.roundToInt()}%",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = color
                            )
                        }
                        Slider(
                            value = customBrightness,
                            onValueChange = onCustomBrightnessChange,
                            valueRange = 5f..100f,
                            steps = 18,
                            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
                        )
                        Button(
                            onClick = {
                                val pct = customBrightness.roundToInt()
                                onPick("CUSTOM_BRIGHTNESS", "Set Brightness to $pct%", pct.toString())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Use ${customBrightness.roundToInt()}% brightness", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
