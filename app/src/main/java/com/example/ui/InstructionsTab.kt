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
        description = "Turn your phone's camera cutout (notch or hole-punch) into an interactive shortcut button.",
        steps = listOf(
            "Go to 'Gestures' to assign actions to different swipes and taps.",
            "Supported gestures: Swipe Left, Swipe Right, Tap, Long Press, and Double Tap.",
            "Go to 'Calibration' to adjust the touch area size, position, and visibility to perfectly align with your phone's physical cutout."
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
        description = "Automatically detect, copy, and manage OTPs and Two-Factor Authentication codes.",
        steps = listOf(
            "Enable the listener to catch OTPs from incoming notifications.",
            "Detected codes are instantly copied to your clipboard.",
            "You can review your recent codes securely in the 'Code Detector' history."
        )
    ),
    InstructionItem(
        title = "Text Assistant & AI",
        icon = Icons.Filled.AutoAwesome,
        description = "Inline text expansion, AI rewriting, and advanced editing from any text box on your phone.",
        steps = listOf(
            "Instant Snippets: Create abbreviations. Type '?brb' in any app to auto-expand to 'Be right back!'.",
            "Dynamic Multiplier: Type a word followed by ? and a number to multiply it (e.g. 'hi ?5'). Add '_' for line-by-line (e.g. 'hi ?5_'), ',' for inline spaces, or rely on your default Settings preference.",
            "AI Assistant: Type a prompt followed by ? and a number (e.g. 'Summarize this ?50'). The AI will replace your text with a 50-word response inline.",
            "Inline Editing: Type '?copy', '?cut', '?paste', or '?clear' at the end of a text box to execute clipboard actions instantly."
        )
    ),
    InstructionItem(
        title = "Video Recordings",
        icon = Icons.Filled.Videocam,
        description = "Trigger silent background video recordings directly from notch gestures.",
        steps = listOf(
            "Assign 'Record Video' to a Notch Gesture (like Long Press).",
            "Trigger the gesture to silently capture a video from the background.",
            "View and manage your saved videos in the 'Video Recordings' tab."
        )
    ),
    InstructionItem(
        title = "Excluded Apps",
        icon = Icons.Filled.Block,
        description = "Prevent SkY Touch from interfering with specific apps (like full-screen games or banking apps).",
        steps = listOf(
            "Navigate to 'Excluded Apps'.",
            "Select any installed app to add it to the exclusion list.",
            "The Notch Area and Side Deck will be temporarily disabled while these apps are open."
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
                    modifier = Modifier.rotate(rotation)
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
