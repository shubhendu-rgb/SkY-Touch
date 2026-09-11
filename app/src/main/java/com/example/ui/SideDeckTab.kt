package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.AppEntry
import com.example.AppInfoCache
import com.example.data.SideDeckConfigEntity
import com.example.service.NotchAccessibilityService
import kotlin.math.roundToInt

@Composable
fun SideDeckTab(
    sideDeckConfig: SideDeckConfigEntity,
    isServiceActive: Boolean,
    onConfigChange: (SideDeckConfigEntity) -> Unit,
    onConfigChangeDebounced: (SideDeckConfigEntity) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var miniPreviewExpanded by remember { mutableStateOf(false) }

    // High performance local state for 60-120fps sliders
    var localConfig by remember(sideDeckConfig) { mutableStateOf(sideDeckConfig) }
    var sliderYOffset by remember(sideDeckConfig.yOffsetPercent) { mutableFloatStateOf(sideDeckConfig.yOffsetPercent) }
    var sliderHandleHeight by remember(sideDeckConfig.handleHeightDp) { mutableFloatStateOf(sideDeckConfig.handleHeightDp.toFloat()) }
    var sliderHandleWidth by remember(sideDeckConfig.handleWidthDp) { mutableFloatStateOf(sideDeckConfig.handleWidthDp.toFloat()) }
    var sliderHandleOpacity by remember(sideDeckConfig.handleOpacity) { mutableFloatStateOf(sideDeckConfig.handleOpacity) }
    var sliderHandleRoundness by remember(sideDeckConfig.handleRoundnessDp) { mutableFloatStateOf(sideDeckConfig.handleRoundnessDp.toFloat()) }
    var sliderHandleEdgeDistance by remember(sideDeckConfig.handleEdgeDistanceDp) { mutableFloatStateOf(sideDeckConfig.handleEdgeDistanceDp.toFloat()) }
    var sliderDeckRoundness by remember(sideDeckConfig.deckRoundnessDp) { mutableFloatStateOf(sideDeckConfig.deckRoundnessDp.toFloat()) }
    var sliderDeckEdgeDistance by remember(sideDeckConfig.deckEdgeDistanceDp) { mutableFloatStateOf(sideDeckConfig.deckEdgeDistanceDp.toFloat()) }
    var sliderBlockBuffer by remember(sideDeckConfig.backGestureBlockBufferDp) { mutableFloatStateOf(sideDeckConfig.backGestureBlockBufferDp.toFloat()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("side_deck_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Section Title & Introduction
        item {
            Text(
                text = "Side Deck Control",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Side Deck places a floating vertical handle on the edge of your screen. Swiping the handle slides out a sleek, compact capsule dock containing fast system shortcuts and quick-launch apps.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Master Toggle & Quick Test Action
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.FolderCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Enable Side Deck",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (localConfig.enabled) "Floating handle active" else "Feature disabled",
                                    fontSize = 12.sp,
                                    color = if (localConfig.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = localConfig.enabled,
                            onCheckedChange = { isChecked ->
                                localConfig = localConfig.copy(enabled = isChecked)
                                onConfigChange(localConfig)
                            },
                            modifier = Modifier.testTag("side_deck_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test Slide Deck Button
                    Button(
                        onClick = {
                            if (NotchAccessibilityService.isRunning && NotchAccessibilityService.instance != null) {
                                NotchAccessibilityService.instance?.openSideDeckPanel()
                                Toast.makeText(context, "Opening Side Deck capsule dock...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "SkY Touch accessibility service must be enabled in Settings first!",
                                    Toast.LENGTH_LONG
                                ).show()
                                onOpenSettings()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("test_side_deck_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Side Deck Overlay Now", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Live Interactive Phone Mockup Preview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Interactive Preview",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap handle or button to simulate swipe",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { miniPreviewExpanded = !miniPreviewExpanded }
                        ) {
                            Text(
                                text = if (miniPreviewExpanded) "Close Demo" else "Swipe Demo",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Phone Frame Box
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(260.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF0B0F19))
                            .border(3.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(26.dp))
                    ) {
                        // Notch dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface)
                                .align(Alignment.TopCenter)
                                .offset(y = 6.dp)
                        )

                        // Desktop Wallpaper Mockup
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            // Clock widget
                            Text(
                                text = "09:41",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // App grid dots
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x33FFFFFF))
                                    )
                                }
                            }

                            // Bottom dock
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                            )
                        }

                        // Floating Handle Simulation
                        val handleColor = try {
                            Color(android.graphics.Color.parseColor(localConfig.handleColorHex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        val isRightEdge = localConfig.edge.uppercase() != "LEFT"
                        val handleOffsetFraction = sliderYOffset.coerceIn(0.15f, 0.85f)
                        val miniHandleCornerRadius = (sliderHandleRoundness * 0.45f).dp.coerceAtLeast(0.dp)
                        val miniHandleEdgeOffset = (sliderHandleEdgeDistance * 0.35f).dp
                        val handleShape = if (sliderHandleEdgeDistance > 0f) {
                            RoundedCornerShape(miniHandleCornerRadius)
                        } else if (isRightEdge) {
                            RoundedCornerShape(topStart = miniHandleCornerRadius, bottomStart = miniHandleCornerRadius)
                        } else {
                            RoundedCornerShape(topEnd = miniHandleCornerRadius, bottomEnd = miniHandleCornerRadius)
                        }

                        Box(
                            modifier = Modifier
                                .align(if (isRightEdge) Alignment.TopEnd else Alignment.TopStart)
                                .offset(
                                    x = if (isRightEdge) -miniHandleEdgeOffset else miniHandleEdgeOffset,
                                    y = (260.dp * handleOffsetFraction) - 25.dp
                                )
                                .width(12.dp)
                                .height(50.dp)
                                .clip(handleShape)
                                .background(handleColor.copy(alpha = sliderHandleOpacity.coerceIn(0.3f, 1f)))
                                .clickable {
                                    miniPreviewExpanded = !miniPreviewExpanded
                                }
                        )

                        // Animated Mini Slide Deck Floating Edge Rail Dock
                        val drawerSlideOffset by animateFloatAsState(
                            targetValue = if (miniPreviewExpanded) 0f else if (isRightEdge) 60f else -60f,
                            animationSpec = tween(durationMillis = 220),
                            label = "drawerSlide"
                        )
                        val miniDockCornerRadius = (sliderDeckRoundness * 0.55f).dp.coerceAtLeast(0.dp)
                        val miniDockEdgeMargin = (sliderDeckEdgeDistance * 0.35f + 2f).dp

                        Box(
                            modifier = Modifier
                                .align(if (isRightEdge) Alignment.CenterEnd else Alignment.CenterStart)
                                .offset(x = (drawerSlideOffset + if (isRightEdge) -miniDockEdgeMargin.value else miniDockEdgeMargin.value).dp)
                                .width(36.dp)
                                .clip(RoundedCornerShape(miniDockCornerRadius))
                                .background(Color(0xE012121D))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // Red Screenshot tool
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Camera, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp))
                                }
                                // Blue Settings tool
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(12.dp))
                                }
                                // Flashlight tool
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(handleColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.FlashlightOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                }

                                // Accent divider line
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .height(1.dp)
                                        .background(Color(0x30FFFFFF))
                                )

                                // Pinned app icons (rounded, no harsh outline)
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(miniDockCornerRadius * 0.35f))
                                        .background(MaterialTheme.colorScheme.tertiary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(12.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(miniDockCornerRadius * 0.35f))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                }

                                // Edit action button
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Screen Edge Placement (Left or Right)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Screen Edge Placement",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose which side of your screen docks the floating handle",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Edge Option
                        val isLeft = localConfig.edge.uppercase() == "LEFT"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    localConfig = localConfig.copy(edge = "LEFT")
                                    onConfigChange(localConfig)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLeft) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                if (isLeft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (isLeft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Left Edge",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLeft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Swipe right to open",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Right Edge Option
                        val isRight = localConfig.edge.uppercase() != "LEFT"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    localConfig = localConfig.copy(edge = "RIGHT")
                                    onConfigChange(localConfig)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                if (isRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.ArrowRight, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (isRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Right Edge",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Swipe left to open",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Vertical Position Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vertical Screen Position",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val percentLabel = "${(sliderYOffset * 100).roundToInt()}%"
                        Text(
                            text = percentLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = sliderYOffset,
                        onValueChange = {
                            sliderYOffset = it
                            localConfig = localConfig.copy(yOffsetPercent = it)
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 0.10f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Haptic Feedback Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Haptic Touch Feedback",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Vibrate briefly when touching the handle or deck",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = localConfig.vibrateOnTouch,
                            onCheckedChange = { isChecked ->
                                localConfig = localConfig.copy(vibrateOnTouch = isChecked)
                                onConfigChange(localConfig)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // Dock Trigger Customization (Height, Width, Roundness, Distance from Edge, Opacity, Color)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Dock Trigger Appearance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fine-tune the floating edge handle size, curvature roundness, and edge distance",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Trigger Height Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trigger Height", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${sliderHandleHeight.roundToInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sliderHandleHeight,
                        onValueChange = {
                            sliderHandleHeight = it
                            localConfig = localConfig.copy(handleHeightDp = it.roundToInt())
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 50f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Trigger Width Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trigger Width", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${sliderHandleWidth.roundToInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sliderHandleWidth,
                        onValueChange = {
                            sliderHandleWidth = it
                            localConfig = localConfig.copy(handleWidthDp = it.roundToInt())
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 5f..40f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Trigger Roundness Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Trigger Roundness", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Curvature radius for the trigger pill", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${sliderHandleRoundness.roundToInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sliderHandleRoundness,
                        onValueChange = {
                            sliderHandleRoundness = it
                            localConfig = localConfig.copy(handleRoundnessDp = it.roundToInt())
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 0f..36f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Trigger Distance from Edge Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Trigger Distance from Edge", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Offset between trigger handle and screen edge", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${sliderHandleEdgeDistance.roundToInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sliderHandleEdgeDistance,
                        onValueChange = {
                            sliderHandleEdgeDistance = it
                            localConfig = localConfig.copy(handleEdgeDistanceDp = it.roundToInt())
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 0f..48f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Trigger Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trigger Transparency", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${(sliderHandleOpacity * 100).roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sliderHandleOpacity,
                        onValueChange = {
                            sliderHandleOpacity = it
                            localConfig = localConfig.copy(handleOpacity = it)
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 0.20f..1.00f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Accent Color Palette
                    Text("Trigger Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))

                    val palette = listOf(
                        "#4F46E5" to "Indigo",
                        "#7C3AED" to "Violet",
                        "#06B6D4" to "Cyan",
                        "#10B981" to "Emerald",
                        "#F59E0B" to "Amber",
                        "#EF4444" to "Red",
                        "#64748B" to "Slate",
                        "#FFFFFF" to "White"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        palette.forEach { (hex, _) ->
                            val isSelected = localConfig.handleColorHex.equals(hex, ignoreCase = true)
                            val col = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        if (isSelected) 3.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .clickable {
                                        localConfig = localConfig.copy(handleColorHex = hex)
                                        onConfigChange(localConfig)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (hex == "#FFFFFF") Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Deck & Pill Customization (Roundness, Distance from Edge)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Deck & Pill Appearance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize the corner roundness curvature and distance from the screen edge for the capsule dock and pills",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Deck / Pills Roundness Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Deck & Pills Roundness",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Curvature radius for the dock capsule and pills",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${sliderDeckRoundness.roundToInt()} dp",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = sliderDeckRoundness,
                        onValueChange = {
                            sliderDeckRoundness = it
                            localConfig = localConfig.copy(deckRoundnessDp = it.roundToInt())
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 0f..36f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Distance from Edge Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Distance from Edge",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Spacing offset between dock capsule and screen edge",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${sliderDeckEdgeDistance.roundToInt()} dp",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = sliderDeckEdgeDistance,
                        onValueChange = {
                            sliderDeckEdgeDistance = it
                            localConfig = localConfig.copy(deckEdgeDistanceDp = it.roundToInt())
                            onConfigChangeDebounced(localConfig)
                        },
                        onValueChangeFinished = {
                            onConfigChange(localConfig)
                        },
                        valueRange = 0f..48f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        // System Back Gesture Protection (Exclusion Area)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Block System Back Gesture",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (localConfig.blockBackGesture) "Exclusion active near handle" else "System back gesture allowed",
                                    fontSize = 11.sp,
                                    color = if (localConfig.blockBackGesture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = localConfig.blockBackGesture,
                            onCheckedChange = { isChecked ->
                                localConfig = localConfig.copy(blockBackGesture = isChecked)
                                onConfigChange(localConfig)
                            },
                            modifier = Modifier.testTag("block_back_gesture_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Excludes Android's system gesture navigation back swipe around the Side Deck handle so your swipe gestures open the dock reliably without accidentally navigating back.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    AnimatedVisibility(visible = localConfig.blockBackGesture) {
                        Column {
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Exclusion Buffer Zone",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Additional touch width excluded from back gesture",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "${sliderBlockBuffer.roundToInt()} dp",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Slider(
                                value = sliderBlockBuffer,
                                onValueChange = {
                                    sliderBlockBuffer = it
                                    localConfig = localConfig.copy(backGestureBlockBufferDp = it.roundToInt())
                                    onConfigChangeDebounced(localConfig)
                                },
                                onValueChangeFinished = {
                                    onConfigChange(localConfig)
                                },
                                valueRange = 0f..30f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // Panel Modules (System Controls & Pinned Apps)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Side Deck Contents",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select what tools and apps are displayed in the slide-out dock",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // System Tools Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Controls", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Flashlight, Screenshot, Quick Settings & Navigation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = localConfig.showSystemTools,
                            onCheckedChange = {
                                localConfig = localConfig.copy(showSystemTools = it)
                                onConfigChange(localConfig)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Shortcut Apps Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shortcut Apps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Quickly launch your pinned applications from the dock", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = localConfig.showShortcutApps,
                            onCheckedChange = {
                                localConfig = localConfig.copy(showShortcutApps = it)
                                onConfigChange(localConfig)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pinned Apps Manager Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Pinned Apps",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        TextButton(
                            onClick = {
                                localConfig = localConfig.copy(pinnedAppPackages = "")
                                onConfigChange(localConfig)
                                Toast.makeText(context, "Reset to recommended apps", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Reset to Defaults", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    val pinnedList = remember(localConfig.pinnedAppPackages) {
                        localConfig.pinnedAppPackages
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }

                    if (pinnedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Currently using automatic launcher apps. Tap '+ Add App' below to pin specific apps to the Side Deck.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val pillCornerRadius = (localConfig.deckRoundnessDp.toFloat().coerceIn(4f, 20f)).dp
                            pinnedList.forEach { pkg ->
                                var appTitle by remember { mutableStateOf(pkg.substringAfterLast(".")) }
                                LaunchedEffect(pkg) {
                                    try {
                                        val pm = context.packageManager
                                        val info = pm.getApplicationInfo(pkg, 0)
                                        appTitle = pm.getApplicationLabel(info).toString()
                                    } catch (_: Exception) {}
                                }

                                // Clean smooth pill without harsh outlines
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(pillCornerRadius))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = appTitle,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = pkg,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            val updated = pinnedList.filter { it != pkg }.joinToString(",")
                                            localConfig = localConfig.copy(pinnedAppPackages = updated)
                                            onConfigChange(localConfig)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FilledTonalIconButton(
                        onClick = { showAppPickerDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add App to Side Deck")
                    }
                }
            }
        }
    }

    // App Picker Dialog
    if (showAppPickerDialog) {
        AppSelectionDialog(
            context = context,
            onDismiss = { showAppPickerDialog = false },
            onAppChosen = { chosenPkg ->
                val current = localConfig.pinnedAppPackages
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (!current.contains(chosenPkg)) {
                    val updated = (current + chosenPkg).joinToString(",")
                    localConfig = localConfig.copy(pinnedAppPackages = updated)
                    onConfigChange(localConfig)
                }
                showAppPickerDialog = false
            }
        )
    }
}

@Composable
fun AppSelectionDialog(
    context: Context,
    onDismiss: () -> Unit,
    onAppChosen: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val apps = AppInfoCache.getOrLoadLauncherApps(context)
        appsList = apps
        isLoading = false
    }

    val filteredApps = remember(appsList, searchQuery) {
        if (searchQuery.isBlank()) appsList
        else appsList.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose App for Side Deck",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed applications...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onAppChosen(app.packageName) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp).clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp).clip(CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
