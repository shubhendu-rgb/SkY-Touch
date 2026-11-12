package com.example.widget

import android.appwidget.AppWidgetManager
import android.graphics.Color as AColor
import android.widget.Toast
import com.example.ActionPickerSheet
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MIN_SIZE = 0.05f

private data class SizePreset(val label: String, val wDp: Float, val hDp: Float)

private val sizePresets = listOf(
    SizePreset("4×2", 250f, 110f),
    SizePreset("4×1", 250f, 56f),
    SizePreset("2×2", 110f, 110f),
    SizePreset("4×3", 250f, 180f),
    SizePreset("4×4", 250f, 250f)
)

private val swatchPresets = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF22C55E,
    0xFF14B8A6, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFF9CA3AF
).map { it.toInt() }

private fun ComponentType.icon() = when (this) {
    ComponentType.BACKGROUND -> Icons.Default.CropSquare
    ComponentType.TIME -> Icons.Default.Schedule
    ComponentType.AMPM -> Icons.Default.AccessTime
    ComponentType.DATE -> Icons.Default.CalendarToday
    ComponentType.WEEKDAY -> Icons.Default.Today
    ComponentType.BATTERY -> Icons.Default.BatteryFull
    ComponentType.TEXT -> Icons.Default.TextFields
    ComponentType.IMAGE -> Icons.Default.Image
}

private val ComponentType.isText get() = this != ComponentType.BACKGROUND && this != ComponentType.IMAGE

/** Live preview of a layout, painted by the same renderer as the home-screen widget. */
@Composable
fun WidgetPreviewCanvas(layout: WidgetLayout, widthDp: Float, heightDp: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(15_000) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val dpScale = density.density * (maxWidth.value / widthDp)
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                WidgetRenderer.draw(c.nativeCanvas, context, layout, size.width, size.height, dpScale, now, placeholders = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetEditorScreen(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSave: (WidgetLayout) -> Unit,
    onSaveAsTemplate: (WidgetLayout) -> Unit
) {
    val context = LocalContext.current
    val isTemplate = appWidgetId == WidgetStore.TEMPLATE_ID
    val initial = remember { WidgetStore.load(context, appWidgetId) }
    var layout by remember { mutableStateOf(initial) }
    var selectedId by remember {
        mutableStateOf(initial.components.firstOrNull { it.type == ComponentType.TIME }?.id)
    }
    var tab by remember { mutableIntStateOf(0) }
    var presetIndex by remember { mutableIntStateOf(0) }
    var showDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var fontVersion by remember { mutableIntStateOf(0) }
    var pendingImageFor by remember { mutableStateOf<String?>(null) }
    var pendingFontFor by remember { mutableStateOf<String?>(null) }
    var editingActionForComponentId by remember { mutableStateOf<String?>(null) }
    var isEditingWidgetAction by remember { mutableStateOf(false) }

    val (realW, realH) = remember {
        if (isTemplate) 0f to 0f
        else SkyWidgetProvider.sizeDp(context, AppWidgetManager.getInstance(context), appWidgetId)
    }
    val widthDp = if (isTemplate) sizePresets[presetIndex].wDp else realW
    val heightDp = if (isTemplate) sizePresets[presetIndex].hDp else realH

    val selected = layout.components.firstOrNull { it.id == selectedId }

    fun updateComponent(id: String, t: (WidgetComponent) -> WidgetComponent) {
        layout = layout.copy(components = layout.components.map { if (it.id == id) t(it) else it })
    }
    fun updateSelected(t: (WidgetComponent) -> WidgetComponent) { selectedId?.let { updateComponent(it, t) } }

    /** Keeps the box's width and changes its height so the image isn't distorted (clamped inside the widget). */
    fun fitToAspect(c: WidgetComponent, aspect: Float): WidgetComponent {
        if (aspect <= 0f || widthDp <= 0f || heightDp <= 0f) return c
        var w = c.w
        var h = w * widthDp / (aspect * heightDp)
        if (h > 1f) { h = 1f; w = h * aspect * heightDp / widthDp }
        w = w.coerceIn(MIN_SIZE, 1f); h = h.coerceIn(MIN_SIZE, 1f)
        return c.copy(w = w, h = h, x = c.x.coerceAtMost(1f - w), y = c.y.coerceAtMost(1f - h))
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingImageFor
        pendingImageFor = null
        if (uri != null && target != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    WidgetAssets.importImage(context, uri).map { it to WidgetAssets.aspect(context, it) }
                }
                result.onSuccess { (name, aspect) ->
                    updateComponent(target) { c ->
                        val withImage = c.copy(image = name)
                        if (aspect != null) fitToAspect(withImage, aspect) else withImage
                    }
                }.onFailure {
                    Toast.makeText(context, it.message ?: "Couldn't add that image", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingFontFor
        pendingFontFor = null
        if (uri != null && target != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { WidgetAssets.importFont(context, uri) }
                result.onSuccess { name ->
                    fontVersion++
                    updateComponent(target) { it.copy(customFont = name) }
                }.onFailure {
                    Toast.makeText(context, it.message ?: "Couldn't add that font", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun requestClose() { if (layout != initial) showDiscard = true else onCancel() }
    BackHandler { requestClose() }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this widget have not been saved.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isTemplate) "Default widget design" else "Widget editor", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { onSave(layout.copy(baseWidthDp = widthDp, baseHeightDp = heightDp)) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (isTemplate) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview size", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sizePresets.forEachIndexed { i, p ->
                        FilterChip(selected = presetIndex == i, onClick = { presetIndex = i }, label = { Text(p.label) })
                    }
                }
            } else {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AspectRatio,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Launcher size: ${widthDp.roundToInt()} × ${heightDp.roundToInt()} dp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            PreviewEditor(
                layout = layout,
                selectedId = selectedId,
                widthDp = widthDp,
                heightDp = heightDp,
                onSelect = { selectedId = it },
                onMove = { id, dx, dy ->
                    updateComponent(id) {
                        it.copy(
                            x = (it.x + dx).coerceIn(0f, 1f - it.w),
                            y = (it.y + dy).coerceIn(0f, 1f - it.h)
                        )
                    }
                },
                onResize = { id, left, top, dx, dy ->
                    updateComponent(id) { c ->
                        var l = c.x; var t = c.y; var r = c.x + c.w; var b = c.y + c.h
                        if (left) l = (l + dx).coerceIn(0f, r - MIN_SIZE) else r = (r + dx).coerceIn(l + MIN_SIZE, 1f)
                        if (top) t = (t + dy).coerceIn(0f, b - MIN_SIZE) else b = (b + dy).coerceIn(t + MIN_SIZE, 1f)
                        c.copy(x = l, y = t, w = r - l, h = b - t)
                    }
                }
            )

            val tabs = listOf("Layers", "Position", "Color", "Style", "Action")

            TabRow(selectedTabIndex = tab.coerceAtMost(tabs.size - 1)) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) })
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tab == 0) {
                    LayersPanel(
                        layout = layout,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        onToggle = { id -> updateComponent(id) { it.copy(visible = !it.visible) } },
                        onMove = { id, delta ->
                            val list = layout.components.toMutableList()
                            val i = list.indexOfFirst { it.id == id }
                            val j = i + delta
                            if (i >= 0 && j in list.indices) {
                                val item = list.removeAt(i); list.add(j, item)
                                layout = layout.copy(components = list)
                            }
                        },
                        onDelete = { id ->
                            layout = layout.copy(components = layout.components.filterNot { it.id == id })
                            if (selectedId == id) selectedId = null
                        },
                        onAdd = { type ->
                            val c = DefaultLayouts.newComponent(type)
                            layout = layout.copy(components = layout.components + c)
                            selectedId = c.id
                            if (type == ComponentType.IMAGE) {
                                pendingImageFor = c.id
                                imagePicker.launch(arrayOf("image/*"))
                            }
                        },
                        onTheme = { layout = layout.copy(theme = it) },
                        onEditWidgetAction = { isEditingWidgetAction = true },
                        onToggleAutoAdjust = { layout = layout.copy(autoAdjust = it) },
                        onToggleBackgroundClick = { layout = layout.copy(enableBackgroundClick = it) },
                        onReset = {
                            layout = DefaultLayouts.create()
                            selectedId = layout.components.firstOrNull { it.type == ComponentType.TIME }?.id
                        },
                        onSaveAsTemplate = {
                            onSaveAsTemplate(layout.copy(baseWidthDp = widthDp, baseHeightDp = heightDp))
                            Toast.makeText(context, "Saved as the design for new widgets", Toast.LENGTH_SHORT).show()
                        },
                        showTemplateButton = !isTemplate
                    )
                } else if (selected == null) {
                    Text(
                        "Tap a part of the widget in the preview, or pick one in Layers, to edit it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Editing: ${selected.type.label}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    when (tab) {
                        1 -> PositionPanel(selected) { t -> updateSelected(t) }
                        2 -> ColorPanel(
                            c = selected,
                            theme = layout.theme,
                            onChange = { spec -> updateSelected { it.copy(color = spec) } },
                            onApplyToAllText = { spec ->
                                layout = layout.copy(components = layout.components.map {
                                    if (it.type.isText) it.copy(color = spec) else it
                                })
                                Toast.makeText(context, "Applied to all text", Toast.LENGTH_SHORT).show()
                            }
                        )
                        3 -> StylePanel(
                            c = selected,
                            fonts = remember(fontVersion) { WidgetAssets.listFonts(context) },
                            onPickImage = {
                                pendingImageFor = selected.id
                                imagePicker.launch(arrayOf("image/*"))
                            },
                            onMatchImageShape = {
                                WidgetAssets.aspect(context, selected.image)?.let { a ->
                                    updateComponent(selected.id) { fitToAspect(it, a) }
                                }
                            },
                            onImportFont = {
                                pendingFontFor = selected.id
                                fontPicker.launch(arrayOf("*/*"))
                            },
                            onDeleteFont = { name ->
                                WidgetAssets.deleteFont(context, name)
                                layout = layout.copy(components = layout.components.map {
                                    if (it.customFont == name) it.copy(customFont = "") else it
                                })
                                fontVersion++
                            },
                            update = { t -> updateSelected(t) }
                        )
                        4 -> ActionPanel(
                            c = selected,
                            enableBackgroundClick = layout.enableBackgroundClick,
                            backgroundActionLabel = layout.clickAction.label,
                            onToggleBackgroundClick = { layout = layout.copy(enableBackgroundClick = it) },
                            onEditBackgroundAction = { isEditingWidgetAction = true },
                            onPickAction = { editingActionForComponentId = selected.id },
                            onClearAction = { updateSelected { it.copy(clickAction = WidgetClickAction()) } },
                            onSelectActionDirect = { type, pkg, lbl, extra ->
                                updateSelected { it.copy(clickAction = WidgetClickAction(type, pkg, lbl, extra)) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (editingActionForComponentId != null) {
        val targetId = editingActionForComponentId!!
        val comp = layout.components.find { it.id == targetId }
        val prettyTitle = "${comp?.type?.label ?: "Component"} Tap Action"
        ActionPickerSheet(
            gestureName = prettyTitle,
            currentAction = comp?.clickAction?.actionType ?: "NONE",
            currentPackage = comp?.clickAction?.packageToLaunch,
            currentExtra = comp?.clickAction?.extraValue,
            onDismiss = { editingActionForComponentId = null },
            onActionSelected = { actionType, pkg, label, extra ->
                updateComponent(targetId) {
                    it.copy(clickAction = WidgetClickAction(actionType, pkg, label, extra))
                }
                editingActionForComponentId = null
            }
        )
    }

    if (isEditingWidgetAction) {
        ActionPickerSheet(
            gestureName = "Widget Tap Action",
            currentAction = layout.clickAction.actionType,
            currentPackage = layout.clickAction.packageToLaunch,
            currentExtra = layout.clickAction.extraValue,
            onDismiss = { isEditingWidgetAction = false },
            onActionSelected = { actionType, pkg, label, extra ->
                layout = layout.copy(clickAction = WidgetClickAction(actionType, pkg, label, extra))
                isEditingWidgetAction = false
            }
        )
    }
}

// ───────────────────────────── Preview with drag + resize ─────────────────────────────

@Composable
private fun PreviewEditor(
    layout: WidgetLayout,
    selectedId: String?,
    widthDp: Float,
    heightDp: Float,
    onSelect: (String?) -> Unit,
    onMove: (id: String, dx: Float, dy: Float) -> Unit,
    onResize: (id: String, left: Boolean, top: Boolean, dx: Float, dy: Float) -> Unit
) {
    val density = LocalDensity.current
    val ratio = heightDp / widthDp
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxH = 260.dp
        var wDp = maxWidth
        var hDp = wDp * ratio
        if (hDp > maxH) { hDp = maxH; wDp = maxH / ratio }
        val wPx = with(density) { wDp.toPx() }
        val hPx = with(density) { hDp.toPx() }

        Box(
            Modifier.size(wDp, hDp).align(Alignment.Center)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1F3B63), Color(0xFF6B4E9B), Color(0xFFB4637A))),
                    RoundedCornerShape(6.dp)
                )
        ) {
            WidgetPreviewCanvas(layout, widthDp, heightDp, Modifier.fillMaxSize())

            // Touch overlays. The selected one goes on top (unless it's the background, which must stay below).
            val visible = layout.components.filter { it.visible }
            val sel = visible.firstOrNull { it.id == selectedId }
            val ordered = if (sel != null && sel.type != ComponentType.BACKGROUND) visible.filter { it.id != sel.id } + sel else visible

            ordered.forEach { c ->
                key(c.id) {
                    val isSel = c.id == selectedId
                    val boxW = with(density) { (c.w * wPx).toDp() }
                    val boxH = with(density) { (c.h * hPx).toDp() }
                    Box(
                        Modifier
                            .offset { IntOffset((c.x * wPx).roundToInt(), (c.y * hPx).roundToInt()) }
                            .size(boxW, boxH)
                            .then(
                                if (isSel) Modifier.border(
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(2.dp)
                                ) else Modifier
                            )
                            .pointerInput(c.id) { detectTapGestures { currentOnSelect(c.id) } }
                            .then(
                                if (isSel) Modifier.pointerInput(c.id) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        currentOnMove(c.id, drag.x / wPx, drag.y / hPx)
                                    }
                                } else Modifier
                            )
                    ) {
                        if (isSel) {
                            val big = boxW > 80.dp && boxH > 60.dp
                            val corners = if (big) listOf(
                                Triple(Alignment.TopStart, true, true), Triple(Alignment.TopEnd, false, true),
                                Triple(Alignment.BottomStart, true, false), Triple(Alignment.BottomEnd, false, false)
                            ) else listOf(Triple(Alignment.BottomEnd, false, false))
                            corners.forEach { (align, left, top) ->
                                Box(
                                    Modifier.align(align).size(28.dp)
                                        .pointerInput(c.id, left, top) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                currentOnResize(c.id, left, top, drag.x / wPx, drag.y / hPx)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(14.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .border(2.dp, Color.White, CircleShape)
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

// ───────────────────────────── Layers ─────────────────────────────

@Composable
private fun LayersPanel(
    layout: WidgetLayout,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onToggle: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (ComponentType) -> Unit,
    onTheme: (DynTheme) -> Unit,
    onEditWidgetAction: () -> Unit,
    onToggleAutoAdjust: (Boolean) -> Unit,
    onToggleBackgroundClick: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    showTemplateButton: Boolean
) {
    var addMenu by remember { mutableStateOf(false) }

    // Widget Root Tap Action Card with Blank Area Switch
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Touch action on blank areas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        if (layout.enableBackgroundClick)
                            "Tapping empty/blank space triggers the widget background action below."
                        else
                            "Disabled. Tapping blank areas does nothing; only components respond to touch.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = layout.enableBackgroundClick,
                    onCheckedChange = onToggleBackgroundClick
                )
            }

            if (layout.enableBackgroundClick) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Blank Area Action", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            layout.clickAction.label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onEditWidgetAction,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Change", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Auto-adjust on resize switch card
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Auto-adjust on launcher resize", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "Auto adjusts component size and keeps relative distance proportional when resizing on launcher.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = layout.autoAdjust,
                onCheckedChange = onToggleAutoAdjust
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    Text("Layers (top of the list is drawn in front)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

    layout.components.asReversed().forEach { c ->
        val isSel = c.id == selectedId
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().clickable { onSelect(c.id) }
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(c.type.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.type == ComponentType.TEXT) "Text: ${c.text.take(14)}" else c.type.label,
                        maxLines = 1,
                        fontSize = 13.sp
                    )
                    if (c.clickAction.actionType != "NONE") {
                        Text(
                            "Tap: ${c.clickAction.label}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
                IconButton(onClick = { onToggle(c.id) }) {
                    Icon(
                        if (c.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Show / hide"
                    )
                }
                IconButton(onClick = { onMove(c.id, +1) }) { Icon(Icons.Default.KeyboardArrowUp, "Bring forward") }
                IconButton(onClick = { onMove(c.id, -1) }) { Icon(Icons.Default.KeyboardArrowDown, "Send back") }
                IconButton(onClick = { onDelete(c.id) }) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }

    Box {
        FilledTonalButton(onClick = { addMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add component")
        }
        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
            ComponentType.values().forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.label) },
                    leadingIcon = { Icon(t.icon(), contentDescription = null) },
                    onClick = { addMenu = false; onAdd(t) }
                )
            }
        }
    }

    HorizontalDivider()
    Text("Material You colours follow", fontWeight = FontWeight.SemiBold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DynTheme.values().forEach { t ->
            FilterChip(selected = layout.theme == t, onClick = { onTheme(t) }, label = { Text(t.label) })
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset design") }
        if (showTemplateButton) {
            OutlinedButton(onClick = onSaveAsTemplate, modifier = Modifier.weight(1f)) { Text("Use for new widgets") }
        }
    }
}

// ───────────────────────────── Position & size ─────────────────────────────

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "%",
    scale: Float = 100f, onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp)
            Text("${(value * scale).roundToInt()}$suffix", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PositionPanel(c: WidgetComponent, update: ((WidgetComponent) -> WidgetComponent) -> Unit) {
    LabeledSlider("Position X (from left)", c.x, 0f..(1f - c.w).coerceAtLeast(0.001f)) { v -> update { it.copy(x = v) } }
    LabeledSlider("Position Y (from top)", c.y, 0f..(1f - c.h).coerceAtLeast(0.001f)) { v -> update { it.copy(y = v) } }
    LabeledSlider("Width", c.w, MIN_SIZE..1f) { v -> update { it.copy(w = v, x = it.x.coerceAtMost(1f - v)) } }
    LabeledSlider("Height", c.h, MIN_SIZE..1f) { v -> update { it.copy(h = v, y = it.y.coerceAtMost(1f - v)) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { update { it.copy(x = (1f - it.w) / 2f) } }, modifier = Modifier.weight(1f)) {
            Text("Center ↔", fontSize = 12.sp)
        }
        OutlinedButton(onClick = { update { it.copy(y = (1f - it.h) / 2f) } }, modifier = Modifier.weight(1f)) {
            Text("Center ↕", fontSize = 12.sp)
        }
        OutlinedButton(onClick = { update { it.copy(x = 0f, y = 0f, w = 1f, h = 1f) } }, modifier = Modifier.weight(1f)) {
            Text("Fill", fontSize = 12.sp)
        }
    }
    Text(
        "Tip: you can also drag the part in the preview and pull its corner dots to resize.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ───────────────────────────── Color ─────────────────────────────

private fun hex6(argb: Int) = String.format("%06X", argb and 0xFFFFFF)

private fun parseHex6(s: String): Int? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    val v = t.toLongOrNull(16) ?: return null
    return (0xFF000000L or v).toInt()
}

@Composable
private fun ColorPanel(
    c: WidgetComponent,
    theme: DynTheme,
    onChange: (ColorSpec) -> Unit,
    onApplyToAllText: (ColorSpec) -> Unit
) {
    val context = LocalContext.current
    val spec = c.color
    val dark = DynamicColors.isDark(context, theme)

    if (c.type == ComponentType.IMAGE && !c.tintImage) {
        LabeledSlider("Image opacity", spec.alpha, 0f..1f) { onChange(spec.copy(alpha = it)) }
        Text(
            "The image keeps its own colours. Turn on \"Tint image\" on the Style tab to paint it in a custom or Material You colour.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !spec.dynamic, onClick = { onChange(spec.copy(dynamic = false)) }, label = { Text("Custom colour") })
        FilterChip(selected = spec.dynamic, onClick = { onChange(spec.copy(dynamic = true)) }, label = { Text("Material You") })
    }

    if (!spec.dynamic) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatchPresets.forEach { argb ->
                val on = spec.argb == argb
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(argb))
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onChange(spec.copy(argb = argb)) }
                )
            }
        }
        var hex by remember(spec.argb) { mutableStateOf(hex6(spec.argb)) }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                val clean = v.removePrefix("#").take(6).uppercase()
                hex = clean
                parseHex6(clean)?.let { onChange(spec.copy(argb = it)) }
            },
            label = { Text("Hex (RRGGBB)") },
            prefix = { Text("#") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val r = AColor.red(spec.argb); val g = AColor.green(spec.argb); val b = AColor.blue(spec.argb)
        LabeledSlider("Red", r / 255f, 0f..1f, "", 255f) { v -> onChange(spec.copy(argb = AColor.argb(255, (v * 255).roundToInt(), g, b))) }
        LabeledSlider("Green", g / 255f, 0f..1f, "", 255f) { v -> onChange(spec.copy(argb = AColor.argb(255, r, (v * 255).roundToInt(), b))) }
        LabeledSlider("Blue", b / 255f, 0f..1f, "", 255f) { v -> onChange(spec.copy(argb = AColor.argb(255, r, g, (v * 255).roundToInt()))) }
    } else {
        if (!DynamicColors.supported) {
            Text(
                "Material You wallpaper colours need Android 12 or newer. This phone uses the app's built-in palette instead.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                "These follow your wallpaper and update by themselves when it changes.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DynRole.values().toList().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { role ->
                    val on = spec.role == role
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .border(
                                if (on) 2.dp else 1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onChange(spec.copy(dynamic = true, role = role)) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .background(Color(DynamicColors.role(context, role, dark)))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(role.label, fontSize = 12.sp, maxLines = 2, lineHeight = 14.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    LabeledSlider(if (c.type == ComponentType.BACKGROUND) "Opacity" else "Transparency (100% = solid)", spec.alpha, 0f..1f) {
        onChange(spec.copy(alpha = it))
    }

    if (c.type.isText) {
        OutlinedButton(onClick = { onApplyToAllText(spec) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Palette, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use this colour for all text")
        }
    }
}

// ───────────────────────────── Style / text ─────────────────────────────

@Composable
private fun StylePanel(
    c: WidgetComponent,
    fonts: List<String>,
    onPickImage: () -> Unit,
    onMatchImageShape: () -> Unit,
    onImportFont: () -> Unit,
    onDeleteFont: (String) -> Unit,
    update: ((WidgetComponent) -> WidgetComponent) -> Unit
) {
    if (c.type == ComponentType.IMAGE) {
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (c.image.isBlank()) "Choose image" else "Change image")
        }
        Text(
            "PNG, JPG or WebP, up to 5 MB. Large images are shrunk to 1024 px to keep the widget light. " +
                "Animated GIFs show their first frame.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Fit", fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageFit.values().forEach { f ->
                FilterChip(selected = c.imageFit == f, onClick = { update { it.copy(imageFit = f) } }, label = { Text(f.label) })
            }
        }
        LabeledSlider("Corner radius", c.cornerDp, 0f..80f, " dp", 1f) { v -> update { it.copy(cornerDp = v) } }
        SwitchRow("Tint image (use the Color tab)", c.tintImage) { v -> update { it.copy(tintImage = v) } }
        if (c.image.isNotBlank()) {
            OutlinedButton(onClick = onMatchImageShape, modifier = Modifier.fillMaxWidth()) {
                Text("Match box to image shape")
            }
        }
        return
    }

    if (c.type == ComponentType.BACKGROUND) {
        LabeledSlider("Corner radius", c.cornerDp, 0f..80f, " dp", 1f) { v -> update { it.copy(cornerDp = v) } }
        return
    }

    when (c.type) {
        ComponentType.TIME -> SwitchRow("24-hour time", c.use24h) { v -> update { it.copy(use24h = v) } }
        ComponentType.DATE -> FormatEditor(
            c.format, listOf("EEE, d MMM", "EEEE, d MMMM", "d MMMM yyyy", "dd/MM/yyyy", "MMM d"), "EEE, d MMM"
        ) { v -> update { it.copy(format = v) } }
        ComponentType.WEEKDAY -> FormatEditor(
            c.format, listOf("EEEE", "EEE", "E"), "EEEE"
        ) { v -> update { it.copy(format = v) } }
        ComponentType.TEXT -> OutlinedTextField(
            value = c.text, onValueChange = { v -> update { it.copy(text = v) } },
            label = { Text("Text") }, modifier = Modifier.fillMaxWidth()
        )
        else -> {}
    }

    Text("Font", fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FontChoice.values().forEach { f ->
            FilterChip(
                selected = c.customFont.isBlank() && c.font == f,
                onClick = { update { it.copy(font = f, customFont = "") } },
                label = { Text(f.label) }
            )
        }
    }
    Text("My fonts", fontSize = 13.sp)
    if (fonts.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fonts.forEach { name ->
                FilterChip(
                    selected = c.customFont == name,
                    onClick = { update { it.copy(customFont = name) } },
                    label = { Text(WidgetAssets.fontLabel(name)) }
                )
            }
        }
    }
    OutlinedButton(onClick = onImportFont, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Import font (.ttf / .otf, up to 5 MB)")
    }
    if (c.customFont.isNotBlank()) {
        TextButton(onClick = { onDeleteFont(c.customFont) }) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Remove \"${WidgetAssets.fontLabel(c.customFont)}\" from my fonts", fontSize = 12.sp)
        }
    }
    Text(
        "Imported fonts are available to every text part. Pick one per part, so time and date can use different fonts.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SwitchRow("Bold", c.bold) { v -> update { it.copy(bold = v) } }
    SwitchRow("Italic", c.italic) { v -> update { it.copy(italic = v) } }
    SwitchRow("ALL CAPS", c.allCaps) { v -> update { it.copy(allCaps = v) } }

    Text("Alignment", fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextAlignment.values().forEach { a ->
            FilterChip(selected = c.align == a, onClick = { update { it.copy(align = a) } }, label = { Text(a.label) })
        }
    }
    LabeledSlider("Text size inside its box", c.textScale, 0.3f..1f) { v -> update { it.copy(textScale = v) } }
    Text(
        "Text always fills its box. Resize the box on the Position tab, or use this slider to shrink the text within it.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FormatEditor(current: String, presets: List<String>, default: String, onChange: (String) -> Unit) {
    val shown = current.ifBlank { default }
    Text("Format", fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { p ->
            FilterChip(selected = shown == p, onClick = { onChange(p) }, label = { Text(p) })
        }
    }
    OutlinedTextField(
        value = current, onValueChange = onChange,
        label = { Text("Custom pattern (e.g. EEE d MMM)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActionPanel(
    c: WidgetComponent,
    enableBackgroundClick: Boolean,
    backgroundActionLabel: String,
    onToggleBackgroundClick: (Boolean) -> Unit,
    onEditBackgroundAction: () -> Unit,
    onPickAction: () -> Unit,
    onClearAction: () -> Unit,
    onSelectActionDirect: (String, String?, String, String?) -> Unit
) {
    val act = c.clickAction
    val isSet = act.actionType != "NONE" && act.actionType.isNotBlank()

    // Dedicated Switch card for Blank Area Touch Action
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!enableBackgroundClick) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (!enableBackgroundClick) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Touch action on blank areas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (!enableBackgroundClick)
                            "Off: Taps on blank areas are ignored. Touch actions work ONLY on components."
                        else
                            "On: Tapping empty or blank spaces triggers the widget background action.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enableBackgroundClick,
                    onCheckedChange = onToggleBackgroundClick
                )
            }

            if (enableBackgroundClick) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Blank Area Action", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(
                            backgroundActionLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onEditBackgroundAction,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Change", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

    Text(
        "${c.type.label} Tap Action",
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSet) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isSet) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSet) Icons.Default.TouchApp else Icons.Default.DoNotTouch,
                        contentDescription = null,
                        tint = if (isSet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isSet) act.label else "No action assigned",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isSet) "Tapping this component on the home screen triggers this action"
                        else if (enableBackgroundClick) "Tapping this component will trigger the overall widget background action"
                        else "No action assigned. Taps on this component will be ignored",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPickAction,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSet) "Change Action" else "Choose Action", fontSize = 13.sp)
                }
                if (isSet) {
                    OutlinedButton(
                        onClick = onClearAction,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (c.type != ComponentType.BACKGROUND) {
        Text(
            "Quick Suggestions",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val quicks = when (c.type) {
            ComponentType.TIME -> listOf(
                Triple("Toggle Flashlight", "FLASHLIGHT", "Toggle Flashlight"),
                Triple("Take Screenshot", "SCREENSHOT", "Take Instant Screenshot"),
                Triple("Open Widget Editor", "OPEN_EDITOR", "Open Widget Editor")
            )
            ComponentType.DATE, ComponentType.WEEKDAY -> listOf(
                Triple("Go Home", "HOME", "Go Home"),
                Triple("Notifications", "NOTIFICATIONS", "Open Notifications Shade"),
                Triple("Open Widget Editor", "OPEN_EDITOR", "Open Widget Editor")
            )
            ComponentType.BATTERY -> listOf(
                Triple("Toggle Flashlight", "FLASHLIGHT", "Toggle Flashlight"),
                Triple("Quick Settings", "QUICK_SETTINGS", "Open Quick Settings Panel"),
                Triple("Take Screenshot", "SCREENSHOT", "Take Instant Screenshot")
            )
            else -> listOf(
                Triple("Take Screenshot", "SCREENSHOT", "Take Instant Screenshot"),
                Triple("Toggle Flashlight", "FLASHLIGHT", "Toggle Flashlight"),
                Triple("Open Widget Editor", "OPEN_EDITOR", "Open Widget Editor")
            )
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quicks.forEach { (label, actionType, fullLabel) ->
                SuggestionChip(
                    onClick = { onSelectActionDirect(actionType, null, fullLabel, null) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
    }
}
