package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class InstructionItem(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val steps: List<String>
)

val instructionsList = listOf(
    InstructionItem(
        title = "Notch Gestures",
        icon = Icons.Filled.TouchApp,
        description = "Turn your phone's camera cutout (notch or hole-punch) into interactive shortcut buttons.",
        steps = listOf(
            "Open 'Notch Gestures' to assign an action to each gesture: Single, Double and Triple Click, Long Press, Swipe Left / Right, and Swipe Left / Right & Hold.",
            "Tap the + icon at the top to add another notch area (up to 3 in total). Every area has its own gestures, position and size.",
            "Pick an area with the 'Notch 1 / 2 / 3' chips. Open 'Position & Size' to change its width, height, corner radius and X / Y position. Extra areas can be removed there.",
            "Tap a gesture to choose its action. The picker shows one list of categories: Navigation, Music & Sound, Brightness, Wallpaper, Camera & Recording, Auto Click and SkY Touch Features, followed by Open App, App Shortcuts, Web Link, Quick Dial, Quick Notes, Widget Editor and No Action. Tap a category to see its options.",
 "Every Quick Settings tile is also a gesture action: Notch, Side Deck, Code Detector, Text Assistant, Equalizer, Wallpaper Changer, Live Wallpaper, wallpaper from Images or Folder, AutoClick Studio and Stop Recording."
        )
    ),
    InstructionItem(
        title = "Notch Position",
        icon = Icons.Filled.GpsFixed,
        description = "Place each notch gesture area exactly over your camera cutout.",
        steps = listOf(
            "Open 'Notch Position' on the Dashboard and pick the area you want to move.",
            "Keep 'Show on my screen' on: colored markers appear on your real screen at the exact position of every area (orange = the selected one) and follow every change you make.",
            "Drag the highlighted area in the preview, or use the sliders for width, height, corner radius and X / Y position. Presets fit common cutout sizes.",
            "Haptic vibration strength and 'Disable in Fullscreen Apps' apply to all areas. The markers need the Accessibility Service to be on."
        )
    ),
    InstructionItem(
        title = "Web Link Gesture",
        icon = Icons.Filled.Language,
        description = "Open a favorite website with one gesture.",
        steps = listOf(
            "Tap a gesture, open 'Web Link' in the list and type an address such as example.com or a full https:// link.",
            "Use 'Test Link' to check it, then 'Save Web Link'.",
            "The link opens in your phone's default browser, even if another app would normally handle that link."
        )
    ),
    InstructionItem(
        title = "Lock Wallpaper for 2 Minutes",
        icon = Icons.Filled.Lock,
        description = "Show a picture of your choice on the lock screen for a short time.",
        steps = listOf(
            "Tap a gesture, open 'Wallpaper', tap 'Lock Pic 2 min', choose a picture and tap 'Use this'.",
            "When you trigger the gesture, the lock screen shows that picture for 2 minutes and then your old lock screen comes back by itself.",
            "To go back safely, the app first remembers your current lock screen. If your phone does not allow that, set a default Lock picture in the wallpaper settings first. If going back is impossible, nothing is changed."
        )
    ),
    InstructionItem(
        title = "Quick Notes",
        icon = Icons.Filled.StickyNote2,
        description = "Keep short text notes that slide down from the top of the screen.",
        steps = listOf(
            "Tap a gesture and choose 'Quick Notes' in the list.",
            "Trigger the gesture and the notes panel slides down. It scrolls and grows up to half of the screen. Press and hold text to select it, or tap the copy icon next to a note.",
            "Use + to add a note, the pin icon to keep important notes at the top, and the up / down arrows icon to rearrange, edit or delete notes.",
            "Tap outside the panel or press Back to close it. Notes are included in Backup & Restore."
        )
    ),
    InstructionItem(
        title = "Quick Dial",
        icon = Icons.Filled.Call,
        description = "Call a favorite contact with one gesture.",
        steps = listOf(
            "Tap a gesture, open 'Quick Dial', tap 'Choose contact' and pick the person.",
            "Allow the phone permission when asked so the call starts directly. Without it, the dialer opens with the number ready.",
            "Trigger the gesture and the call starts."
        )
    ),
    InstructionItem(
        title = "Side Deck",
        icon = Icons.Filled.ViewSidebar,
        description = "A customizable edge panel that gives you quick access to apps and shortcuts from anywhere.",
        steps = listOf(
            "Enable the Side Deck handle in the 'Side Deck' tab.",
            "Swipe inward from the handle to open your floating dock.",
            "Customize its size, color, apps, and vertical position in the settings."
        )
    ),
    InstructionItem(
        title = "Code Detector",
        icon = Icons.Filled.QrCodeScanner,
        description = "Finds verification codes (OTPs) in your messages and notifications and makes them easy to copy.",
        steps = listOf(
            "Open 'Code Detector' and switch it on at the top. The 'Setup' section shows what still needs to be allowed.",
            "Under 'Where to look for codes', choose notifications, text messages (SMS), and whether all apps or only chosen apps are checked.",
            "Under 'When a code is found', choose an alert with a Copy button, automatic copying and vibration. The alert appears 2 seconds after the code is found, and only the newest code is shown.",
            "Under 'How codes are recognized', set the code length, allow codes with letters, and add keywords, a message format or a custom pattern if a code is missed.",
            "Use 'Try it' to test a message, then check 'Found codes' for your saved codes. Tap the copy icon to copy one, or the bin icon to delete all."
        )
    ),
    InstructionItem(
        title = "Text Assistant & AI",
        icon = Icons.Filled.AutoAwesome,
        description = "Inline text expansion, AI rewriting, and advanced editing from any text box on your phone.",
        steps = listOf(
            "AI Triggers: Type any text followed by '?improve', '?shorten', '?expand', '?formal', '?casual', '?emoji', '?human', '?reply', or '?fix' to transform your text instantly with Gemini.",
            "Inline Editing & Clipboard: Type '?replace' (replace text with clipboard), '?paste', '?undo' (restore original text), '?copy', or '?cut' at the end of any input field.",
            "Dynamic Translation: Type '?translate:es', '?translate:fr', etc. to translate any text directly into the specified language.",
            "Instant Text Snippets: Type abbreviations like '?addr' to auto-expand to predefined addresses or full templates.",
            "Dynamic Word Multiplier: Type a word followed by '?' and a count (e.g. 'hi ?5') to repeat it inline."
        )
    ),
    InstructionItem(
        title = "AutoClick Studio",
        icon = Icons.Filled.Mouse,
        description = "Automate taps, double taps, long presses and swipes on any app or game.",
        steps = listOf(
            "Open 'AutoClick Studio' and tap START. A floating menu bar appears on top of your screen (needs the Accessibility Service).",
            "Use + to add numbered target reticles and drag them where you want to tap. Tap a target while idle to change its gesture type.",
            "Open the menu's settings to set the click delay and when to stop: infinite loop, a timer or a loop count. Save a preset to reuse your setup.",
            "You can also assign Auto Click start / stop to a notch gesture or use its Quick Settings tile."
        )
    ),
    InstructionItem(
        title = "Wallpaper Changer",
        icon = Icons.Filled.Wallpaper,
        description = "Change your home and lock wallpapers automatically or on demand.",
        steps = listOf(
            "Open 'Wallpaper Changer', choose pictures or a folder for the Home and Lock screens and set how often they change.",
            "Adjust blur, gradient, grayscale, crop alignment and scrolling for each screen.",
            "Change the wallpaper instantly with a notch gesture or the Quick Settings tiles (Images, Folder and the Changer on / off tile).",
            "'Pause all wallpapers' stops all automation and applies your default Home and Lock pictures."
        )
    ),
    InstructionItem(
        title = "Live Wallpaper Studio",
        icon = Icons.Filled.Movie,
        description = "Use a GIF or video as your live wallpaper.",
        steps = listOf(
            "Open 'Live Wallpaper Studio' from the Wallpaper Changer, pick a GIF or video and set the crop, rotation, fit, blur and color mood.",
            "Turn on auto change to cycle through several live wallpapers, or change to the next one right away.",
            "Mute video audio so it does not play on the home screen. The live wallpaper freezes while Game Mode or Night Battery Saver is active."
        )
    ),
    InstructionItem(
        title = "Equalizer",
        icon = Icons.Filled.GraphicEq,
        description = "A system-wide equalizer with presets that switch by app or output device.",
        steps = listOf(
            "Open 'Equalizer', turn it on and shape the sound with the bands, preamp, bass, surround and loudness.",
            "Add rules so a different preset is used for a specific app, speaker, neckband or wired headset.",
            "Allow the Bluetooth permission (optional) so speakers and headphones can be told apart.",
            "Import or export your curve as a text file. Apps that do not share their audio session may stay unaffected."
        )
    ),
    InstructionItem(
        title = "Widget Studio",
        icon = Icons.Filled.Widgets,
        description = "Design your own home screen clock and info widgets.",
        steps = listOf(
            "Open 'Widget Studio', or add the SkY Touch widget to your home screen, to open the editor.",
            "Add, move and resize components such as time, date, battery, text and images, and set their colors (including Material You colors) and fonts.",
            "In the 'Action' tab choose what each component does when you tap it on the home screen, or set one action for the empty widget background. Tap Save to apply it."
        )
    ),
    InstructionItem(
        title = "Night Battery Saver",
        icon = Icons.Filled.Bedtime,
        description = "Lowers the battery use of the whole app while you sleep.",
        steps = listOf(
            "Open 'Night Battery Saver' and set the time you go to sleep and the time you wake up.",
            "Between those times wallpaper changers stop, overlays are removed, Auto Click stops and the live wallpaper freezes.",
            "Everything returns by itself at the end time."
        )
    ),
    InstructionItem(
        title = "Game Mode",
        icon = Icons.Filled.SportsEsports,
        description = "Stops the whole app while one of your chosen games is open.",
        steps = listOf(
            "Open 'Game Mode', turn it on and select your games.",
            "While a chosen game is on screen, overlays, gestures, Auto Click, code detection and wallpaper changes are paused so nothing disturbs the game.",
            "Everything restarts by itself when you leave the game."
        )
    ),
    InstructionItem(
        title = "Video Recordings & Editor",
        icon = Icons.Filled.Videocam,
        description = "Record silent background videos and edit them.",
        steps = listOf(
            "Assign 'Record Video' to a notch gesture (like Long Press) and trigger it to capture a video silently from the background. Use the 'Stop Silent Record' tile to stop.",
            "View and manage your saved videos in 'Video Recordings'.",
            "Open 'Video Editor' to cut a range, combine videos or export frames as PNG images. Results are saved in your Movies folder."
        )
    ),
    InstructionItem(
        title = "Quick Settings Tiles",
        icon = Icons.Filled.Apps,
        description = "Start features from the pull-down Quick Settings panel.",
        steps = listOf(
            "Edit your Quick Settings panel and add the SkY Touch tiles: Notch Gesture, Side Deck, OTP Detector, Text Assistant, Wallpaper Images / Folder / Changer, Live Wallpaper, AutoClick Studio and Stop Silent Record.",
            "Tap a tile to switch the feature on or off. Long press a tile to open its settings screen."
        )
    ),
    InstructionItem(
        title = "Excluded Apps",
        icon = Icons.Filled.Block,
        description = "Prevent SkY Touch from interfering with specific apps (like full-screen games or banking apps).",
        steps = listOf(
            "Navigate to 'Excluded Apps'.",
            "Select any installed app to add it to the exclusion list.",
            "The Notch Areas and Side Deck will be temporarily disabled while these apps are open."
        )
    ),
    InstructionItem(
        title = "Analytics",
        icon = Icons.Filled.Analytics,
        description = "See which gestures you use most.",
        steps = listOf(
            "Open 'Analytics' from the menu to see how often each gesture was used."
        )
    ),
    InstructionItem(
        title = "Backup & Restore",
        icon = Icons.Filled.Backup,
        description = "Save your settings and move them to another phone.",
        steps = listOf(
            "Open 'Backup & Restore' to export your gestures, notch areas, quick notes and settings to a file, or to import a backup.",
            "Pictures chosen for the lock wallpaper gesture, and Quick Dial contacts' permissions, have to be set up again on a new phone. The app restarts after an import."
        )
    ),
    InstructionItem(
        title = "Permissions",
        icon = Icons.Filled.Security,
        description = "See every permission the app needs and why.",
        steps = listOf(
            "Tap the shield icon at the top right of the Dashboard.",
            "Each permission shows why it is needed and which features use it. A red 'Grant' button means it is not granted yet.",
            "Tap 'Grant' to allow it. The list updates when you come back."
        )
    )
)

@Composable
fun InstructionsTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Welcome to SkY Touch!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
            )
            Text(
                text = "Discover how to unlock the full potential of your device with smart edge gestures and automation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
            )
        }

        items(instructionsList, key = { it.title }) { instruction ->
            InstructionCard(instruction)
        }
    }
}

@Composable
fun InstructionCard(item: InstructionItem) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    item.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
