package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AppEntry
import com.example.AppInfoCache
import com.example.service.audio.DeviceCaps
import com.example.service.audio.AudioRoute
import com.example.service.audio.AudioRouteMonitor
import com.example.service.audio.EqLayout
import com.example.service.audio.EqPresets
import com.example.service.audio.EqRule
import com.example.service.audio.PRESET_BYPASS
import com.example.service.audio.RouteCategory
import com.example.service.audio.RuleKind
import com.example.service.audio.SessionInfo
import com.example.service.audio.presetDisplayName
import com.example.service.audio.EqSettings
import com.example.service.audio.EqualizerController
import com.example.service.audio.EqualizerEngine
import com.example.service.audio.EngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqualizerTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by EqualizerController.state.collectAsStateWithLifecycle()
    val caps by EqualizerController.caps.collectAsStateWithLifecycle()
    val status by EqualizerEngine.status.collectAsStateWithLifecycle()
    val route by EqualizerController.route.collectAsStateWithLifecycle()
    val knownDevices by EqualizerController.knownDevices.collectAsStateWithLifecycle()

    // Optional: lets us tell Bluetooth speakers from headphones / neckbands (Android 12+)
    var btGranted by remember { mutableStateOf(AudioRouteMonitor.canClassifyBluetooth(context)) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        btGranted = AudioRouteMonitor.canClassifyBluetooth(context)
        EqualizerController.refreshRoute()
    }

    LaunchedEffect(Unit) { EqualizerController.init(context) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var showSaveDialog by remember { mutableStateOf(false) }

    val levels = remember(settings.bandDb, settings.bandCount) { settings.levelsFor(settings.bandCount) }

    val scope = rememberCoroutineScope()

    // Export: let the user pick where to save a .txt file (Storage Access Framework, no permission needed)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val text = EqualizerController.exportText()
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                    } catch (_: Exception) {
                        false
                    }
                }
                Toast.makeText(context, if (ok) "Equalizer exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Import: pick any text file with Preamp / Filter lines
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    val text = readTextLimited(context, uri, 500_000)
                    if (text == null) EqualizerController.ImportOutcome(false, "Could not read this file (too large or unreadable).")
                    else EqualizerController.importText(text)
                }
                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MasterCard(
            settings = settings,
            status = status,
            onToggle = { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                EqualizerController.setEnabled(context, on)
            }
        )

        PresetsRow(
            settings = settings,
            onSelect = { EqualizerController.applyPreset(it) },
            onDelete = { EqualizerController.deleteCustomPreset(it) }
        )

        val ruleNote = if (settings.enabled && settings.autoProfiles && status.defaultFromRule)
            "Right now \"${status.defaultProfile}\" is playing. The sliders below edit your default curve." else null

        BandsCard(
            ruleNote = ruleNote,
            levels = levels,
            hz = EqLayout.hz(settings.bandCount),
            bandCount = settings.bandCount,
            deviceBands = caps.bands,
            minDb = caps.minDb,
            maxDb = caps.maxDb,
            enabled = settings.enabled,
            preampDb = settings.preampDb,
            onBandCountChange = { EqualizerController.setBandCount(it) },
            onBandChange = { i, db -> EqualizerController.setBand(i, db) },
            onPreampChange = { EqualizerController.setPreamp(it) },
            onAutoPreamp = { EqualizerController.autoPreamp() },
            onReset = { EqualizerController.resetAll() },
            onSave = { showSaveDialog = true }
        )

        ProfilesCard(
            settings = settings,
            status = status,
            route = route,
            known = knownDevices,
            btGranted = btGranted,
            onRequestBluetooth = { btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
        )

        BackupCard(
            onExport = {
                val safe = settings.presetName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Custom" }
                exportLauncher.launch("SkyTouch EQ - $safe.txt")
            },
            onImport = { importLauncher.launch(arrayOf("*/*")) }
        )

        OpenFromAppsCard()

        ExtrasCard(settings = settings, caps = caps)

        StatusCard(settings = settings, status = status, caps = caps)
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                val ok = EqualizerController.saveCustomPreset(name)
                if (ok) showSaveDialog = false
                ok
            }
        )
    }
}

// ------------------------------------------------------------------------------------------ master

@Composable
private fun MasterCard(settings: EqSettings, status: EngineStatus, onToggle: (Boolean) -> Unit) {
    val summary = when {
        !settings.enabled -> "Off. Turn on to shape the sound of your phone."
        status.globalActive && status.apps.isNotEmpty() -> "On. System-wide + ${status.apps.size} app session(s)"
        status.globalActive -> "On. Applied system-wide"
        status.apps.any { it.attached } -> "On. Applied to ${status.apps.count { it.attached }} app session(s)"
        else -> "On. Waiting for an app to play audio…"
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Equalizer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.enabled, onCheckedChange = onToggle)
        }
    }
}

// ------------------------------------------------------------------------------------------ presets

@Composable
private fun PresetsRow(settings: EqSettings, onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    val names = remember(settings.customPresets) { EqPresets.builtIn.keys.toList() + settings.customPresets.keys.toList() }
    Column {
        Text("Presets", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.presetName == "Custom") {
                item(key = "__custom") {
                    FilterChip(selected = true, onClick = {}, label = { Text("Custom") })
                }
            }
            items(names, key = { it }) { name ->
                val isCustomPreset = settings.customPresets.containsKey(name)
                FilterChip(
                    selected = settings.presetName == name,
                    onClick = { onSelect(name) },
                    label = { Text(name) },
                    trailingIcon = if (isCustomPreset) {
                        {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Delete preset",
                                modifier = Modifier.size(16.dp).clickable { onDelete(name) }
                            )
                        }
                    } else null
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ bands

@Composable
private fun BandsCard(
    ruleNote: String?,
    levels: List<Float>,
    hz: List<Int>,
    bandCount: Int,
    deviceBands: Int,
    minDb: Float,
    maxDb: Float,
    enabled: Boolean,
    preampDb: Float,
    onBandCountChange: (Int) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onPreampChange: (Float) -> Unit,
    onAutoPreamp: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    val compact = levels.size > 6
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            // 5 / 10 band switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Equalizer bands", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                FilterChip(selected = bandCount == 5, onClick = { onBandCountChange(5) }, label = { Text("5 band") })
                FilterChip(selected = bandCount == 10, onClick = { onBandCountChange(10) }, label = { Text("10 band") })
            }
            if (deviceBands < bandCount) {
                Text(
                    "This phone's equalizer hardware has $deviceBands bands, so your $bandCount-band curve is blended onto them as closely as possible.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (ruleNote != null) {
                Text(
                    ruleNote,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            ResponseCurve(
                levels = levels,
                minDb = minDb,
                maxDb = maxDb,
                modifier = Modifier.fillMaxWidth().height(84.dp).alpha(if (enabled) 1f else 0.45f)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                levels.forEachIndexed { i, db ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(formatDb(db), fontSize = if (compact) 9.sp else 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BandSlider(
                            valueDb = db,
                            minDb = minDb,
                            maxDb = maxDb,
                            compact = compact,
                            onChange = { onBandChange(i, it) },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Text(
                            formatHz(hz.getOrElse(i) { 0 }),
                            fontSize = if (compact) 9.sp else 10.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            LabeledSlider(
                title = "Preamp",
                valueLabel = formatDb(preampDb) + " dB",
                value = preampDb,
                range = -15f..15f,
                supported = true,
                onChange = onPreampChange
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAutoPreamp) { Text("Auto preamp (prevent clipping)", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save preset")
                }
            }
        }
    }
}

@Composable
private fun ResponseCurve(levels: List<Float>, minDb: Float, maxDb: Float, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val n = levels.size
        if (n < 2) return@Canvas
        val w = size.width
        val h = size.height
        val range = (maxDb - minDb).coerceAtLeast(1f)
        fun px(i: Int) = w * (i + 0.5f) / n
        fun py(db: Float) = h * (maxDb - db.coerceIn(minDb, maxDb)) / range

        drawLine(grid, Offset(0f, py(0f)), Offset(w, py(0f)), strokeWidth = 1.dp.toPx())

        val curve = Path()
        curve.moveTo(px(0), py(levels[0]))
        for (i in 1 until n) {
            val x0 = px(i - 1); val y0 = py(levels[i - 1])
            val x1 = px(i); val y1 = py(levels[i])
            val cx = (x0 + x1) / 2f
            curve.cubicTo(cx, y0, cx, y1, x1, y1)
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(px(n - 1), h)
            lineTo(px(0), h)
            close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(line.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(curve, color = line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A vertical slider that sets a dB value. Drag or tap anywhere on it. */
@Composable
private fun BandSlider(
    valueDb: Float,
    minDb: Float,
    maxDb: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val active = MaterialTheme.colorScheme.primary
    var heightPx by remember { mutableFloatStateOf(1f) }

    fun yToDb(y: Float): Float {
        val f = (y / heightPx).coerceIn(0f, 1f)
        var db = maxDb - f * (maxDb - minDb)
        db = (db * 2f).roundToInt() / 2f           // snap to 0.5 dB
        if (abs(db) < 0.4f) db = 0f                // magnet at 0 dB
        return db.coerceIn(minDb, maxDb)
    }

    Box(
        modifier = modifier
            .width(if (compact) 26.dp else 40.dp)
            .height(190.dp)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(minDb, maxDb) {
                detectTapGestures { offset -> onChange(yToDb(offset.y)) }
            }
            .pointerInput(minDb, maxDb) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> onChange(yToDb(offset.y)) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        onChange(yToDb(change.position.y))
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val range = (maxDb - minDb).coerceAtLeast(1f)
            val zeroY = size.height * maxDb / range
            val valueY = size.height * (maxDb - valueDb.coerceIn(minDb, maxDb)) / range
            val trackW = 4.dp.toPx()

            drawLine(track, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = trackW, cap = StrokeCap.Round)
            drawLine(active, Offset(cx, zeroY), Offset(cx, valueY), strokeWidth = trackW, cap = StrokeCap.Round)
            drawLine(track, Offset(cx - 7.dp.toPx(), zeroY), Offset(cx + 7.dp.toPx(), zeroY), strokeWidth = 1.5.dp.toPx())
            drawCircle(active, radius = (if (compact) 8.dp else 9.dp).toPx(), center = Offset(cx, valueY))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 3.5.dp.toPx(), center = Offset(cx, valueY))
        }
    }
}

// ------------------------------------------------------------------------------------------ open from music apps

private enum class PanelOwner { US, CHOOSER, OTHER, NONE }

private data class PanelState(val owner: PanelOwner, val otherLabel: String? = null, val otherPackage: String? = null)

private fun panelIntent(): Intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

/** Who opens when a music app taps its Equalizer button? (us / Android's "Open with" list / another app) */
private fun resolvePanelOwner(context: android.content.Context): PanelState {
    val pm = context.packageManager
    val best = try {
        pm.resolveActivity(panelIntent(), PackageManager.MATCH_DEFAULT_ONLY)
    } catch (_: Exception) {
        null
    } ?: return PanelState(PanelOwner.NONE)

    val pkg = best.activityInfo?.packageName ?: return PanelState(PanelOwner.NONE)
    val name = best.activityInfo?.name.orEmpty()
    return when {
        pkg == context.packageName -> PanelState(PanelOwner.US)
        pkg == "android" || pkg == "com.android.intentresolver" || name.contains("ResolverActivity") || name.contains("ChooserActivity") ->
            PanelState(PanelOwner.CHOOSER)
        else -> {
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            PanelState(PanelOwner.OTHER, label, pkg)
        }
    }
}

@Composable
private fun OpenFromAppsCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // re-check every time the user comes back (e.g. after tapping "Always" or clearing a default)
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val state = remember(refresh) { resolvePanelOwner(context) }
    val appLabel = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }

    fun launchPanel() {
        try {
            context.startActivity(panelIntent())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No equalizer available", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(pkg: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Open from music apps", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            when (state.owner) {
                PanelOwner.US -> {
                    StatusLine(ok = true, text = "The Equalizer button in music apps opens this screen directly")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { launchPanel() }, modifier = Modifier.weight(1f)) { Text("Test it") }
                        TextButton(onClick = { openAppInfo(context.packageName) }, modifier = Modifier.weight(1f)) {
                            Text("Reset default")
                        }
                    }
                }

                PanelOwner.OTHER -> {
                    StatusLine(ok = false, text = "Currently opens ${state.otherLabel} directly")
                    Text(
                        "To use this equalizer instead: open ${state.otherLabel}'s app info → Open by default (or Set as default) → Clear defaults. " +
                            "Then come back here and tap \"Set as default\".",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { state.otherPackage?.let { openAppInfo(it) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear ${state.otherLabel}", maxLines = 1) }
                    }
                }

                else -> {
                    StatusLine(ok = false, text = "Android will show an \"Open with\" list")
                    Text(
                        "Tap \"Set as default\", pick $appLabel in the list and press Always. " +
                            "After that, the Equalizer button in Spotify, YouTube Music, VLC and other players opens this screen directly.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { launchPanel() }, modifier = Modifier.fillMaxWidth()) { Text("Set as default") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ auto profiles (per app / per device)

@Composable
private fun ProfilesCard(
    settings: EqSettings,
    status: EngineStatus,
    route: AudioRoute?,
    known: List<AudioRoute>,
    btGranted: Boolean,
    onRequestBluetooth: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<EqRule?>(null) }
    val presetNames = remember(settings.customPresets) { EqPresets.builtIn.keys.toList() + settings.customPresets.keys.toList() }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto profiles", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Pick a different preset for an app, a speaker, a neckband or a wired headset.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.autoProfiles, onCheckedChange = { EqualizerController.setAutoProfiles(it) })
            }

            StatusLine(
                ok = route != null,
                text = if (route == null) "Checking output device…" else "Playing on: ${route.label} · ${route.category.title}"
            )
            if (settings.enabled && status.defaultProfile.isNotEmpty()) {
                Text("Preset now: ${status.defaultProfile}", fontSize = 12.sp)
            }

            if (!btGranted) {
                TextButton(onClick = onRequestBluetooth) {
                    Text("Allow Bluetooth to tell speakers from headphones", fontSize = 12.sp)
                }
            }

            if (settings.rules.isEmpty()) {
                Text(
                    "No rules yet. Add one and the preset switches by itself when that app or device is used.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            settings.rules.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { editRule = rule }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            "${rule.kind.title} → ${presetDisplayName(rule.preset)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { EqualizerController.removeRule(rule) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                    }
                }
            }

            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add rule")
            }

            Text(
                "Priority: app → exact device → device type → your default settings. " +
                    "A rule changes the curve only; preamp, bass, surround and loudness stay as you set them.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.perAppMode) {
                Text(
                    "App rules are in use, so the system-wide output is paused and every app gets its own preset. " +
                        "Apps that do not share their audio session keep their original sound.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showAdd || editRule != null) {
        RuleDialog(
            editing = editRule,
            known = known,
            playing = status.apps,
            presetNames = presetNames,
            onDismiss = { showAdd = false; editRule = null },
            onSave = { rule ->
                EqualizerController.upsertRule(rule)
                showAdd = false
                editRule = null
            }
        )
    }
}

@Composable
private fun RuleDialog(
    editing: EqRule?,
    known: List<AudioRoute>,
    playing: List<SessionInfo>,
    presetNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (EqRule) -> Unit
) {
    // step 1 = choose what the rule is for, step 2 = choose the preset
    var target by remember { mutableStateOf(editing?.let { Triple(it.kind, it.key, it.label) }) }
    var preset by remember { mutableStateOf(editing?.preset ?: "Flat") }
    val chosen = target

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (chosen == null) "Add rule – choose target" else "Choose preset") },
        text = {
            if (chosen == null) {
                TargetPicker(known = known, playing = playing) { kind, key, label -> target = Triple(kind, key, label) }
            } else {
                Column {
                    Text(chosen.third, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(presetNames + PRESET_BYPASS, key = { it }) { name ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { preset = name },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = preset == name, onClick = { preset = name })
                                Text(presetDisplayName(name))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (chosen != null) {
                TextButton(onClick = { onSave(EqRule(chosen.first, chosen.second, chosen.third, preset)) }) { Text("Save") }
            }
        },
        dismissButton = {
            if (chosen != null && editing == null) {
                TextButton(onClick = { target = null }) { Text("Back") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun TargetPicker(
    known: List<AudioRoute>,
    playing: List<SessionInfo>,
    onPick: (RuleKind, String, String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Device type") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("My devices") })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("App") })
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(RouteCategory.selectable, key = { it.key }) { c ->
                    PickRow(title = c.title, subtitle = null) { onPick(RuleKind.CATEGORY, c.key, c.title) }
                }
            }

            1 -> {
                val devices = known.filter { it.category != RouteCategory.PHONE_SPEAKER }
                if (devices.isEmpty()) {
                    Text(
                        "No devices yet. Connect a Bluetooth, wired or USB device once and it will appear here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(devices, key = { it.key }) { d ->
                            PickRow(title = d.label, subtitle = d.category.title) { onPick(RuleKind.DEVICE, d.key, d.label) }
                        }
                    }
                }
            }

            else -> AppPicker(playing = playing, onPick = onPick)
        }
    }
}

@Composable
private fun AppPicker(playing: List<SessionInfo>, onPick: (RuleKind, String, String) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            try { AppInfoCache.getOrLoadLauncherApps(context) } catch (_: Throwable) { emptyList() }
        }
    }
    var query by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        val list = apps
        if (list == null) {
            Text("Loading apps…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val q = query.trim().lowercase()
            val playingPkgs = playing.mapNotNull { it.packageName }.toSet()
            val filtered = list
                .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                .sortedByDescending { it.packageName in playingPkgs } // apps playing right now first
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                items(filtered, key = { it.packageName }) { a ->
                    PickRow(
                        title = a.label,
                        subtitle = if (a.packageName in playingPkgs) "Playing now" else null,
                        icon = a.icon
                    ) { onPick(RuleKind.APP, a.packageName, a.label) }
                }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, subtitle: String?, icon: ImageBitmap? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ backup / share

@Composable
private fun BackupCard(onExport: () -> Unit, onImport: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup & share", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Save your curve as a .txt file (Preamp + Filter lines) that other equalizer apps can open, " +
                    "or import a file from them. Files with more filters than this phone has bands are fitted as closely as possible.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export .txt")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import .txt")
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ extras

@Composable
private fun ExtrasCard(settings: EqSettings, caps: DeviceCaps) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sound enhancers", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            LabeledSlider(
                title = "Bass boost",
                valueLabel = "${settings.bassStrength / 10}%",
                value = settings.bassStrength.toFloat(),
                range = 0f..1000f,
                supported = caps.bassSupported,
                onChange = { EqualizerController.setBass(it.roundToInt()) }
            )
            LabeledSlider(
                title = "Surround (virtualizer)",
                valueLabel = "${settings.virtualizerStrength / 10}%",
                value = settings.virtualizerStrength.toFloat(),
                range = 0f..1000f,
                supported = caps.virtualizerSupported,
                onChange = { EqualizerController.setVirtualizer(it.roundToInt()) }
            )
            LabeledSlider(
                title = "Loudness boost",
                valueLabel = "+" + String.format("%.1f", settings.loudnessMb / 100f) + " dB",
                value = settings.loudnessMb.toFloat(),
                range = 0f..1200f,
                supported = caps.loudnessSupported,
                onChange = { EqualizerController.setLoudness(it.roundToInt()) }
            )
            if (settings.loudnessMb > 600) {
                Text(
                    "High loudness can distort audio and may be hard on speakers and ears. Lower it if you hear crackling.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("System-wide output", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Also try the global audio mix. Some phones block this; apps that share their audio session still work.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.globalSession, onCheckedChange = { EqualizerController.setGlobalSession(it) })
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    supported: Boolean,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.alpha(if (supported) 1f else 0.45f)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                if (supported) valueLabel else "Not supported",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = supported)
    }
}

// ------------------------------------------------------------------------------------------ status / help

@Composable
private fun StatusCard(settings: EqSettings, status: EngineStatus, caps: DeviceCaps) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { EqualizerController.refresh() }, enabled = settings.enabled) {
                    Text("Re-attach")
                }
            }

            if (settings.enabled) {
                StatusLine(
                    ok = status.globalActive,
                    text = when {
                        !settings.globalSession -> "System-wide output: switched off"
                        status.globalActive -> "System-wide output: active"
                        status.globalFailed -> "System-wide output: blocked by this phone"
                        else -> "System-wide output: starting…"
                    }
                )
                if (status.apps.isEmpty()) {
                    Text(
                        "No app has shared its audio session yet. Start playing music or video.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    status.apps.forEach { app ->
                        StatusLine(ok = app.attached, text = "${app.label} · ${app.profile.ifEmpty { "default" }}")
                    }
                }
            }

            if (!caps.fromDevice) {
                Text(
                    "Using default 5-band layout – the device's real band list could not be read yet.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "How it works: Android lets an equalizer attach to the audio of apps that announce their audio session " +
                    "(Spotify, YouTube Music, VLC, most music and video players) and, on some phones, to the whole output mix. " +
                    "Apps that don't announce a session (some games, calls, some video apps) may stay unaffected. " +
                    "Another equalizer, such as a Dolby or Samsung system effect, can override this one – turn it off if you hear no change.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(if (ok) Color(0xFF43A047) else Color(0xFFFB8C00))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------------------------------------ dialog

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20); error = null },
                    singleLine = true,
                    label = { Text("Preset name") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onSave(name)) error = "Pick a different name"
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------------------------------ helpers

/** Reads a small text file; returns null if it cannot be read or is longer than [maxChars]. */
private fun readTextLimited(context: android.content.Context, uri: Uri, maxChars: Int): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                if (sb.length + n > maxChars) return@use null
                sb.append(buf, 0, n)
            }
            sb.toString()
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatDb(db: Float): String {
    val v = (db * 10f).roundToInt() / 10f
    return when {
        v > 0f -> "+" + v.toString().removeSuffix(".0")
        else -> v.toString().removeSuffix(".0")
    }
}

private fun formatHz(hz: Int): String = when {
    hz >= 10_000 -> "${hz / 1000}k"
    hz >= 1000 -> String.format("%.1fk", hz / 1000f).replace(".0k", "k")
    else -> hz.toString()
}
