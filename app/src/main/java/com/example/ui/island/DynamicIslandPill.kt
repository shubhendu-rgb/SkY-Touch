package com.example.ui.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun resolveTextColor(skin: DynamicIslandSkin, behavior: DynamicNotchBehavior, fallback: Color = skin.textAndIconsColor): Color {
    if (behavior.textColorOverrideHex.isNotBlank()) {
        try {
            return Color(android.graphics.Color.parseColor(behavior.textColorOverrideHex))
        } catch (_: Exception) {}
    }
    return fallback
}

private fun resolveTextWeight(behavior: DynamicNotchBehavior, defaultWeight: FontWeight): FontWeight {
    return if (behavior.textBold) FontWeight.Bold else defaultWeight
}

private fun resolveTextSize(baseSp: Float, behavior: DynamicNotchBehavior): TextUnit {
    val scale = behavior.textScale.coerceIn(0.70f, 1.50f)
    return (baseSp * scale).sp
}

enum class IslandState {
    HIDDEN,
    COLLAPSED,
    EXPANDED
}

/**
 * Scalable Dynamic Island Pill Composable with physics-based spring animations.
 * Faithful reproduction of iOS/Android custom island skins with dynamic edge glows,
 * gradient borders, and responsive touch mechanics.
 */
@Composable
fun DynamicIslandPill(
    skin: DynamicIslandSkin,
    state: IslandState,
    modifier: Modifier = Modifier,
    behavior: DynamicNotchBehavior = DynamicNotchBehavior(),
    previewLookState: String? = null,
    onStateChange: (IslandState) -> Unit = {},
    trackTitle: String = "Midnight City",
    artistName: String = "M83 • Hurry Up, We're Dreaming",
    isPlayingInitial: Boolean = true
) {
    val effectiveState = when (previewLookState) {
        "Idle", "Alert" -> IslandState.COLLAPSED
        else -> IslandState.COLLAPSED
    }

    val activeCapsule = DynamicNotchManager.currentCapsuleEvent.value ?: DynamicNotchManager.liveActivityEvent.value

    if (effectiveState == IslandState.HIDDEN) return
    val isIdleHidden = (effectiveState == IslandState.COLLAPSED && behavior.idleMode == IdleMode.HIDDEN && previewLookState == null && activeCapsule == null)

    var isPlaying by remember { mutableStateOf(isPlayingInitial) }
    var playbackProgress by remember { mutableFloatStateOf(0.42f) }

    // PHYSICS-BASED TRANSITION (Spring Specifications with dynamic speed)
    val transition = updateTransition(targetState = effectiveState, label = "DynamicIslandPhysicsTransition")

    // Dynamic width with snappy spring overshoot
    val baseCollapsedWidth = if (skin.customWidthDp > 0) skin.customWidthDp.dp else 126.dp
    val baseCollapsedHeight = if (skin.customHeightDp > 0) skin.customHeightDp.dp else 32.dp
    val expandedWidth = (345 * (skin.expandedWidthPercent / 88f).coerceIn(0.7f, 1.2f)).dp

    val islandWidth by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = behavior.animationSpeed.dampingRatio,
                stiffness = behavior.animationSpeed.stiffness
            )
        },
        label = "IslandWidth"
    ) { target ->
        when (target) {
            IslandState.HIDDEN -> 0.dp
            IslandState.COLLAPSED -> {
                if (activeCapsule != null) {
                    val extraActions = if (activeCapsule.actionButtons.isNotEmpty()) 45f else 0f
                    (baseCollapsedWidth.value.coerceAtLeast(185f) + extraActions).dp
                } else if (previewLookState == "Alert") {
                    (baseCollapsedWidth.value.coerceAtLeast(185f)).dp
                } else if (previewLookState == "Idle") {
                    baseCollapsedWidth
                } else {
                    when {
                        behavior.idleMode == IdleMode.EMPTY_PILL -> baseCollapsedWidth
                        behavior.idleMode == IdleMode.CLOCK -> (baseCollapsedWidth.value + 24f).dp
                        behavior.idleMode == IdleMode.BATTERY -> (baseCollapsedWidth.value + 20f).dp
                        else -> (baseCollapsedWidth.value.coerceAtLeast(160f)).dp
                    }
                }
            }
            IslandState.EXPANDED -> expandedWidth
        }
    }

    // Dynamic height with spring physics
    val islandHeight by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = behavior.animationSpeed.dampingRatio,
                stiffness = behavior.animationSpeed.stiffness
            )
        },
        label = "IslandHeight"
    ) { target ->
        when (target) {
            IslandState.HIDDEN -> 0.dp
            IslandState.COLLAPSED -> {
                if (activeCapsule != null && activeCapsule.actionButtons.isNotEmpty()) {
                    (baseCollapsedHeight.value.coerceAtLeast(36f) + 4f).dp
                } else {
                    baseCollapsedHeight
                }
            }
            IslandState.EXPANDED -> 168.dp
        }
    }

    // Dynamic corner radius
    val cornerRadius by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = behavior.animationSpeed.dampingRatio,
                stiffness = behavior.animationSpeed.stiffness
            )
        },
        label = "IslandCornerRadius"
    ) { target ->
        when (target) {
            IslandState.HIDDEN -> 50.dp
            IslandState.COLLAPSED -> when (skin.customShape) {
                "Sharp" -> 4.dp
                "Rounded" -> 14.dp
                "Notch" -> 16.dp
                else -> when (skin.shape) {
                    IslandShape.SLAB -> 8.dp
                    IslandShape.NOTCH -> 16.dp
                    else -> 50.dp
                }
            }
            IslandState.EXPANDED -> when (skin.customShape) {
                "Sharp" -> 12.dp
                "Rounded" -> 22.dp
                else -> 36.dp
            }
        }
    }

    // Dynamic scale pop for tactile click feedback
    val scaleFactor by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = behavior.animationSpeed.dampingRatio,
                stiffness = behavior.animationSpeed.stiffness
            )
        },
        label = "IslandScale"
    ) { target ->
        if (target == IslandState.HIDDEN) 0.6f else 1.0f
    }

    // Determine target shape based on custom shape or preset shape
    val islandShape: Shape = when (skin.customShape) {
        "Sharp" -> RoundedCornerShape(cornerRadius)
        "Rounded" -> RoundedCornerShape(cornerRadius)
        "Notch" -> {
            RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
            )
        }
        else -> when (skin.shape) {
            IslandShape.NOTCH -> RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
            )
            IslandShape.SLAB -> RoundedCornerShape(8.dp)
            else -> RoundedCornerShape(cornerRadius)
        }
    }

    // Determine container background fill with opacity and skin colors
    val alphaFloat = (skin.opacityPercent / 100f).coerceIn(0.1f, 1.0f) * skin.alpha
    val effectiveBaseColor = if (skin.islandColor != Color.Black) skin.islandColor else skin.baseColor
    val backgroundFill: Color = if (isIdleHidden) Color.Transparent else effectiveBaseColor.copy(alpha = alphaFloat)

    // Border stroke configuration
    val hasCustomBorder = skin.borderWidthDp > 0
    val hasCustomGlow = skin.glowSizeDp > 0

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            .width(islandWidth)
            .height(islandHeight)
            // Outer Glowing Halo Shader effect
            .then(
                if (!isIdleHidden && (hasCustomGlow || skin.effectType == IslandEffectType.GLOW || skin.effectType == IslandEffectType.GRADIENT_GLOW)) {
                    Modifier.drawBehind {
                        val glowPadding = if (hasCustomGlow) (skin.glowSizeDp.dp).toPx() else skin.glowRadius.toPx()
                        val glowColorToUse = if (hasCustomGlow) skin.glowColor else (skin.glowColors.firstOrNull() ?: Color.Cyan)
                        val glowBrushLocal = Brush.radialGradient(
                            colors = listOf(glowColorToUse.copy(alpha = 0.65f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension / 1.4f
                        )

                        for (i in 3 downTo 1) {
                            val expansion = glowPadding * (i / 3f)
                            val alphaMult = (0.32f / i)
                            drawRoundRect(
                                brush = glowBrushLocal,
                                topLeft = Offset(-expansion, -expansion),
                                size = size.copy(
                                    width = size.width + expansion * 2,
                                    height = size.height + expansion * 2
                                ),
                                cornerRadius = CornerRadius(
                                    cornerRadius.toPx() + expansion,
                                    cornerRadius.toPx() + expansion
                                ),
                                alpha = alphaMult,
                                style = Stroke(width = expansion * 1.5f)
                            )
                        }
                    }
                } else Modifier
            )
            // Drop shadow
            .then(
                if (!isIdleHidden && skin.dropShadow) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.45f),
                            topLeft = Offset(0f, 4f),
                            size = size,
                            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                        )
                    }
                } else Modifier
            )
            // Shape clipping
            .clip(islandShape)
            // Background fill
            .background(backgroundFill)
            // Border outline
            .then(
                if (isIdleHidden) {
                    Modifier
                } else {
                    when {
                        hasCustomBorder -> {
                            Modifier.border(
                                width = skin.borderWidthDp.dp,
                                color = skin.borderColor,
                                shape = islandShape
                            )
                        }
                        skin.effectType == IslandEffectType.OUTLINE -> {
                            Modifier.border(
                                width = skin.outlineWidth,
                                color = skin.outlineColor,
                                shape = islandShape
                            )
                        }
                        skin.accentOutline -> {
                            Modifier.border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.22f),
                                shape = islandShape
                            )
                        }
                        else -> Modifier
                    }
                }
            )
            // Specular Shine overlay
            .then(
                if (!isIdleHidden && skin.shinePercent > 0) {
                    Modifier.drawBehind {
                        val shineAlpha = (skin.shinePercent / 100f) * 0.4f
                        val shineBrush = Brush.verticalGradient(
                            colors = listOf(
                                skin.shineColor.copy(alpha = shineAlpha),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.6f
                        )
                        drawRoundRect(
                            brush = shineBrush,
                            size = size,
                            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // CONTENT SWITCHING
        if (!isIdleHidden && effectiveState == IslandState.COLLAPSED) {
            val showIdleOnly = previewLookState == "Idle"
            if (activeCapsule != null) {
                MiniCapsuleContent(
                    capsule = activeCapsule,
                    skin = skin,
                    behavior = behavior
                )
            } else if (previewLookState == "Alert") {
                MiniCapsuleContent(
                    capsule = MiniCapsuleEvent(
                        type = CapsuleType.BATTERY_CHARGING_STARTED,
                        title = "Charging",
                        subtitle = "88% • Fast Charge",
                        iconVector = Icons.Default.BatteryChargingFull,
                        accentColor = Color(0xFF00E676)
                    ),
                    skin = skin,
                    behavior = behavior
                )
            } else {
                CollapsedIslandContent(
                    trackTitle = trackTitle,
                    isPlaying = isPlaying,
                    skin = skin,
                    behavior = behavior,
                    isIdleOnly = showIdleOnly
                )
            }
        } else if (effectiveState == IslandState.EXPANDED) {
            ExpandedIslandContent(
                trackTitle = trackTitle,
                artistName = artistName,
                isPlaying = isPlaying,
                progress = playbackProgress,
                skin = skin,
                behavior = behavior,
                onPlayPauseToggle = { isPlaying = !isPlaying },
                onProgressChange = { playbackProgress = it },
                onCollapseRequest = { onStateChange(IslandState.COLLAPSED) }
            )
        }
    }
}

/**
 * Mini Capsule Content for displaying battery, Wi-Fi, headphones, volume,
 * and incoming filtered notifications with action buttons.
 */
@Composable
private fun MiniCapsuleContent(
    capsule: MiniCapsuleEvent,
    skin: DynamicIslandSkin,
    behavior: DynamicNotchBehavior
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Icon Badge with subtle accent glow/tint
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(capsule.accentColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = capsule.iconVector ?: Icons.Default.Notifications,
                contentDescription = null,
                tint = capsule.accentColor,
                modifier = Modifier.size(13.dp)
            )
        }

        // Title and Subtitle Text
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = capsule.title,
                color = resolveTextColor(skin, behavior),
                fontSize = resolveTextSize(10.5f, behavior),
                fontWeight = resolveTextWeight(behavior, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!capsule.subtitle.isNullOrBlank()) {
                Text(
                    text = capsule.subtitle,
                    color = resolveTextColor(skin, behavior).copy(alpha = 0.72f),
                    fontSize = resolveTextSize(9f, behavior),
                    fontWeight = resolveTextWeight(behavior, FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Buttons if present and enabled in behavior
        if (behavior.showNotificationButtons && capsule.actionButtons.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                capsule.actionButtons.take(2).forEach { actionItem ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(skin.textAndIconsColor.copy(alpha = 0.18f))
                            .clickable {
                                try {
                                    actionItem.actionIntent?.send()
                                    DynamicNotchManager.dismissCapsule()
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = actionItem.title,
                            color = resolveTextColor(skin, behavior),
                            fontSize = resolveTextSize(8.5f, behavior),
                            fontWeight = resolveTextWeight(behavior, FontWeight.SemiBold),
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            // Live indicator or subtle pulsing dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(capsule.accentColor.copy(alpha = 0.8f))
            )
        }
    }
}

/**
 * Collapsed compact pill content.
 * Displays mini album art thumbnail on left, and animated audio equalizer on right.
 */
@Composable
private fun CollapsedIslandContent(
    trackTitle: String,
    isPlaying: Boolean,
    skin: DynamicIslandSkin,
    behavior: DynamicNotchBehavior,
    isIdleOnly: Boolean = false
) {
    if (isIdleOnly) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(skin.textAndIconsColor.copy(alpha = 0.35f))
            )
        }
        return
    }

    when (behavior.idleMode) {
        IdleMode.EMPTY_PILL -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(skin.textAndIconsColor.copy(alpha = 0.35f))
                )
            }
        }
        IdleMode.CLOCK -> {
            val timeStr = remember {
                try {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                } catch (_: Exception) {
                    "10:42"
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = skin.textAndIconsColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = timeStr,
                    color = resolveTextColor(skin, behavior),
                    fontSize = resolveTextSize(12f, behavior),
                    fontWeight = resolveTextWeight(behavior, FontWeight.Bold)
                )
            }
        }
        IdleMode.BATTERY -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "88%",
                    color = resolveTextColor(skin, behavior),
                    fontSize = resolveTextSize(12f, behavior),
                    fontWeight = resolveTextWeight(behavior, FontWeight.Bold)
                )
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Album Art Thumbnail
                AlbumArtThumbnail(size = 22.dp)

                // Middle: Subtle title label
                Text(
                    text = trackTitle,
                    color = resolveTextColor(skin, behavior),
                    fontSize = resolveTextSize(11f, behavior),
                    fontWeight = resolveTextWeight(behavior, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .weight(1f, fill = false)
                )

                // Right: Animated Audio Equalizer Bars
                AudioEqualizer(
                    isPlaying = isPlaying,
                    barCount = 3,
                    primaryColor = if (skin.glowSizeDp > 0) skin.glowColor else (skin.glowColors.firstOrNull() ?: skin.textAndIconsColor)
                )
            }
        }
    }
}

/**
 * Expanded full notification / media card.
 * Rich media interface featuring artwork, title, artist, seekbar, and media controls.
 */
@Composable
private fun ExpandedIslandContent(
    trackTitle: String,
    artistName: String,
    isPlaying: Boolean,
    progress: Float,
    skin: DynamicIslandSkin,
    behavior: DynamicNotchBehavior,
    onPlayPauseToggle: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onCollapseRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Row: Album Art, Metadata, and Equalizer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumbnail(
                size = 46.dp,
                cornerRadius = 10.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = trackTitle,
                    color = resolveTextColor(skin, behavior, Color.White),
                    fontSize = resolveTextSize(14f, behavior),
                    fontWeight = resolveTextWeight(behavior, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artistName,
                    color = resolveTextColor(skin, behavior, Color.White).copy(alpha = 0.7f),
                    fontSize = resolveTextSize(11f, behavior),
                    fontWeight = resolveTextWeight(behavior, FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Audio Waveform Visualizer
            AudioEqualizer(
                isPlaying = isPlaying,
                barCount = 5,
                primaryColor = if (skin.glowColors.isNotEmpty()) skin.glowColors.first() else Color(0xFF00E5FF)
            )

            // Quick Collapse Chevron Button
            IconButton(
                onClick = onCollapseRequest,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Collapse",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Quick Reply Bar (if enabled in behavior)
        if (behavior.quickReply) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { /* Quick reply */ }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = "Quick Reply",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Quick reply without leaving app...",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
            }
        }

        // Middle Row: Scrubber Progress Bar and Timers
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (skin.glowColors.isNotEmpty()) skin.glowColors.first() else Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1:42",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
                Text(
                    text = "-2:21",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }

        // Bottom Row: Playback / Notification Controls
        if (behavior.showNotificationButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                IconButton(
                    onClick = { /* Previous track */ },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Play / Pause Circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Next
                IconButton(
                    onClick = { /* Next track */ },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Stylized Album Artwork depiction for 'Midnight City' / M83.
 * Renders an artistic synthwave / neon sunset album cover aesthetic.
 */
@Composable
fun AlbumArtThumbnail(
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = size / 2
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF8A2387),
                        Color(0xFFE94057),
                        Color(0xFFF27121)
                    )
                )
            )
            .border(
                width = 0.75.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner vinyl groove / neon city disc aesthetic
        Box(
            modifier = Modifier
                .size(size * 0.45f)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
    }
}

/**
 * Animated Audio Equalizer with 3 or 5 jumping bars.
 */
@Composable
fun AudioEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    primaryColor: Color = Color(0xFF00E5FF)
) {
    if (!isPlaying) {
        // Zero CPU/GPU usage when paused: static rendering, no infinite transition running
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(barCount) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(primaryColor)
                )
            }
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "EqualizerTransition")

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until barCount) {
                val duration = 350 + (i * 120)
                val animatedHeight by infiniteTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "EqualizerBar$i"
                )

                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(12.dp)
                        .graphicsLayer {
                            scaleY = animatedHeight
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .clip(RoundedCornerShape(1.dp))
                        .background(primaryColor)
                )
            }
        }
    }
}
