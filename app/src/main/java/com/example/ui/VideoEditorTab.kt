package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.util.VideoToolkit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the running job so a long cut / join keeps going while the user moves around the app. */
class VideoEditorViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val busy: Boolean = false,
        val progress: Float = 0f,
        val message: String = "",
        val savedFiles: List<String> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun clearResult() {
        if (!_state.value.busy) _state.value = State()
    }

    fun cancel() {
        job?.cancel()
    }

    fun split(uri: Uri, name: String, seconds: Int) {
        if (_state.value.busy) return
        _state.value = State(busy = true, message = "Analysing video…")
        val saved = ArrayList<String>()
        job = viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    VideoToolkit.splitVideo(getApplication(), uri, name, seconds) { f, m ->
                        _state.value = _state.value.copy(progress = f, message = m)
                    }
                }
                saved.addAll(files)
                _state.value = State(savedFiles = saved.toList(), message = "Saved ${files.size} part(s) to Movies/${VideoToolkit.OUTPUT_FOLDER}")
            } catch (e: CancellationException) {
                _state.value = State(message = "Cancelled")
                throw e
            } catch (e: VideoToolkit.EditException) {
                _state.value = State(error = e.message)
            } catch (e: Exception) {
                _state.value = State(error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun merge(uris: List<Uri>, forceCombine: Boolean = false) {
        if (_state.value.busy) return
        _state.value = State(busy = true, message = "Preparing…")
        job = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    VideoToolkit.mergeVideos(getApplication(), uris, forceCombine) { f, m ->
                        _state.value = _state.value.copy(progress = f, message = m)
                    }
                }
                _state.value = State(savedFiles = listOf(file), message = "Saved to Movies/${VideoToolkit.OUTPUT_FOLDER}")
            } catch (e: CancellationException) {
                _state.value = State(message = "Cancelled")
                throw e
            } catch (e: VideoToolkit.EditException) {
                _state.value = State(error = e.message)
            } catch (e: Exception) {
                _state.value = State(error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun runJob(
        start: String,
        successMessage: (List<String>) -> String,
        block: suspend ((Float, String) -> Unit) -> List<String>
    ) {
        if (_state.value.busy) return
        _state.value = State(busy = true, message = start)
        job = viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    block { f, m -> _state.value = _state.value.copy(progress = f, message = m) }
                }
                _state.value = State(savedFiles = files, message = successMessage(files))
            } catch (e: CancellationException) {
                _state.value = State(message = "Cancelled")
                throw e
            } catch (e: VideoToolkit.EditException) {
                _state.value = State(error = e.message)
            } catch (e: Exception) {
                _state.value = State(error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun removeSections(uri: Uri, name: String, rangesMs: List<Pair<Long, Long>>) {
        runJob("Analysing video…", { "Saved to Movies/${VideoToolkit.OUTPUT_FOLDER}" }) { progress ->
            listOf(VideoToolkit.removeSections(getApplication(), uri, name, rangesMs, progress))
        }
    }

    fun insertClip(mainUri: Uri, clipUri: Uri, name: String, atMs: Long, forceCombine: Boolean = false) {
        runJob("Analysing videos…", { "Saved to Movies/${VideoToolkit.OUTPUT_FOLDER}" }) { progress ->
            listOf(VideoToolkit.insertClip(getApplication(), mainUri, clipUri, name, atMs, forceCombine, progress))
        }
    }

    fun exportFrames(uri: Uri, name: String, baseUs: Long, fromMs: Long, toMs: Long, stepUs: Long, fps: Float) {
        runJob("Exporting frames…", { "Saved ${it.size} PNG frame(s) to Pictures/${VideoToolkit.FRAMES_FOLDER}" }) { progress ->
            VideoToolkit.exportFrames(getApplication(), uri, name, baseUs, fromMs, toMs, stepUs, fps, 1000, progress)
        }
    }
}

private fun formatTime(ms: Long): String {
    val m = ms / 60000
    val s = (ms % 60000) / 1000
    return "%d:%02d.%03d".format(m, s, ms % 1000)
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
fun VideoEditorTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: VideoEditorViewModel = viewModel()
    val state by vm.state.collectAsState()

    var mode by remember { mutableIntStateOf(0) } // 0 = split, 1 = combine

    // --- split state
    var splitInfo by remember { mutableStateOf<VideoToolkit.VideoInfo?>(null) }
    var segmentSeconds by remember { mutableIntStateOf(30) }
    var customText by remember { mutableStateOf("") }

    // --- combine state
    var clips by remember { mutableStateOf<List<VideoToolkit.VideoInfo>>(emptyList()) }
    var forceCombine by rememberSaveable { mutableStateOf(false) }

    var loadError by remember { mutableStateOf<String?>(null) }

    // Legacy storage permission (only Android 9 and older need it to save into Movies)
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke() else loadError = "Storage permission is needed to save videos into Movies/${VideoToolkit.OUTPUT_FOLDER} on this Android version."
    }
    fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loadError = null
            vm.clearResult()
            try {
                splitInfo = VideoToolkit.readInfo(context, uri)
            } catch (e: Exception) {
                loadError = e.message
            }
        }
    }
    val pickMultiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            loadError = null
            vm.clearResult()
            val added = ArrayList<VideoToolkit.VideoInfo>()
            for (u in uris) {
                try {
                    added.add(VideoToolkit.readInfo(context, u))
                } catch (e: Exception) {
                    loadError = e.message
                }
            }
            clips = clips + added
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_editor_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Video Editor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cut a video into equal parts or join videos together. Nothing is re-encoded, so the original resolution and audio quality are kept exactly. Results are saved automatically to Movies/${VideoToolkit.OUTPUT_FOLDER}.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            TabRow(selectedTabIndex = mode) {
                Tab(selected = mode == 0, onClick = { mode = 0 }, enabled = !state.busy, text = { Text("Cut into parts") }, icon = { Icon(Icons.Default.ContentCut, null) })
                Tab(selected = mode == 1, onClick = { mode = 1 }, enabled = !state.busy, text = { Text("Combine") }, icon = { Icon(Icons.Default.Merge, null) })
                Tab(selected = mode == 2, onClick = { mode = 2 }, enabled = !state.busy, text = { Text("Frames") }, icon = { Icon(Icons.Default.Image, null) })
            }
        }

        if (mode == 2) {
            item {
                FrameEditorPanel(vm = vm, busy = state.busy, withStorage = { withStorage(it) })
            }
        } else if (mode == 0) {
            item {
                OutlinedButton(
                    onClick = { pickSingle.launch(arrayOf("video/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VideoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (splitInfo == null) "Choose a video" else "Choose another video")
                }
            }
            splitInfo?.let { info ->
                item {
                    Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(info.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                listOf(formatDuration(info.durationMs), "${info.width}×${info.height}", formatSize(info.sizeBytes))
                                    .filter { it.isNotBlank() }.joinToString("  •  "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Length of each part", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 15, 30, 60).forEach { s ->
                                    FilterChip(
                                        selected = segmentSeconds == s && customText.isBlank(),
                                        onClick = { segmentSeconds = s; customText = "" },
                                        enabled = !state.busy,
                                        label = { Text("${s}s") }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { v ->
                                    val digits = v.filter { it.isDigit() }.take(5)
                                    customText = digits
                                    digits.toIntOrNull()?.let { if (it >= 1) segmentSeconds = it }
                                },
                                enabled = !state.busy,
                                label = { Text("Custom length (seconds)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            val seconds = segmentSeconds.coerceAtLeast(1)
                            val parts = ((info.durationMs / 1000.0) / seconds).let { kotlin.math.ceil(it).toInt().coerceAtLeast(1) }
                            Text(
                                "About $parts part(s). Each part is never longer than ${seconds}s; cuts snap to the nearest key frame just before the limit, so a part can be a little shorter.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            withStorage { vm.split(info.uri, info.name, segmentSeconds.coerceAtLeast(1)) }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cut & save automatically")
                    }
                }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = { pickMultiple.launch(arrayOf("video/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (clips.isEmpty()) "Add videos" else "Add more videos")
                }
            }
            if (clips.isNotEmpty()) {
                item {
                    Text(
                        "Order: top plays first. Total ${formatDuration(clips.sumOf { it.durationMs })}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(clips, key = { i, c -> "${c.uri}#$i" }) { index, clip ->
                    Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(clip.name, fontWeight = FontWeight.Medium, maxLines = 1, fontSize = 14.sp)
                                Text(
                                    "${formatDuration(clip.durationMs)}  •  ${clip.width}×${clip.height}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                enabled = !state.busy && index > 0,
                                onClick = {
                                    clips = clips.toMutableList().also { val t = it[index]; it[index] = it[index - 1]; it[index - 1] = t }
                                }
                            ) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(
                                enabled = !state.busy && index < clips.size - 1,
                                onClick = {
                                    clips = clips.toMutableList().also { val t = it[index]; it[index] = it[index + 1]; it[index + 1] = t }
                                }
                            ) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(
                                enabled = !state.busy,
                                onClick = { clips = clips.toMutableList().also { it.removeAt(index) } }
                            ) { Icon(Icons.Default.Close, "Remove") }
                        }
                    }
                }
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (forceCombine)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (forceCombine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { forceCombine = !forceCombine }
                            .testTag("force_combine_toggle_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (forceCombine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Force combine clips",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Bypasses strict encoder (SPS/PPS), audio & format mismatches. Enable this when clips can't be joined due to 'different video encoder settings'.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = forceCombine,
                                onCheckedChange = { forceCombine = it },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("switch_force_combine")
                            )
                        }
                    }
                }
                item {
                    Text(
                        if (forceCombine)
                            "⚡ Force Combine active: Clips will be joined frame-to-frame by streaming raw samples without failing on minor encoder parameter or audio differences."
                        else
                            "Videos are joined frame-to-frame with no gap and nothing is re-encoded. They need the same codec, resolution and audio settings (normal for clips from the same camera).",
                        fontSize = 12.sp,
                        color = if (forceCombine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Button(
                        onClick = { withStorage { vm.merge(clips.map { it.uri }, forceCombine = forceCombine) } },
                        enabled = !state.busy && clips.size >= 2,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_combine_videos")
                    ) {
                        Icon(if (forceCombine) Icons.Default.Bolt else Icons.Default.Merge, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (clips.size >= 2) {
                                if (forceCombine) "⚡ Force combine & save" else "Combine & save automatically"
                            } else "Add at least 2 videos"
                        )
                    }
                }
            }
        }

        // Progress / result
        if (state.busy) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(state.message, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${(state.progress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.cancel() }) { Text("Cancel") }
                    }
                }
            }
        }
        if (!state.busy && state.savedFiles.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(state.message, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.height(6.dp))
                        state.savedFiles.forEach {
                            Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
        if (!state.busy && state.savedFiles.isEmpty() && state.message == "Cancelled") {
            item { Text("Cancelled. The unfinished file was removed.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val err = state.error ?: loadError
        if (err != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("error_card")
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Cannot Join Clips", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(err, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)

                        val isMismatch = err.contains("re-encoding", ignoreCase = true) ||
                            err.contains("encoder settings", ignoreCase = true) ||
                            err.contains("different", ignoreCase = true)

                        if (isMismatch && mode == 1 && clips.size >= 2 && !forceCombine) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    forceCombine = true
                                    withStorage { vm.merge(clips.map { it.uri }, forceCombine = true) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("btn_force_combine_error")
                            ) {
                                Icon(Icons.Default.Bolt, null)
                                Spacer(Modifier.width(8.dp))
                                Text("⚡ Force Combine Anyway (Bypass Check)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}


/**
 * Frame editor: remove sections, insert a video, or export frames as PNG. Everything that ends up in a
 * video is copied without re-encoding; exported frames are saved as lossless PNG.
 */
@Composable
private fun FrameEditorPanel(vm: VideoEditorViewModel, busy: Boolean, withStorage: (() -> Unit) -> Unit) {
    val context = LocalContext.current

    var info by remember { mutableStateOf<VideoToolkit.VideoInfo?>(null) }
    var analysis by remember { mutableStateOf<VideoToolkit.Analysis?>(null) }
    var analysing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var op by remember { mutableIntStateOf(0) } // 0 remove, 1 add, 2 export
    var posMs by remember { mutableLongStateOf(0L) }
    var markStart by remember { mutableStateOf<Long?>(null) }
    var markEnd by remember { mutableStateOf<Long?>(null) }
    var ranges by remember { mutableStateOf<List<Pair<Long, Long>>>(emptyList()) }
    var addInfo by remember { mutableStateOf<VideoToolkit.VideoInfo?>(null) }
    var forceInsert by rememberSaveable { mutableStateOf(false) }
    var stepChoice by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            error = null
            try {
                val i = VideoToolkit.readInfo(context, uri)
                info = i
                analysis = null
                posMs = 0L
                markStart = null
                markEnd = null
                ranges = emptyList()
                preview = null
                vm.clearResult()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    val pickInsert = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            error = null
            try {
                addInfo = VideoToolkit.readInfo(context, uri)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    LaunchedEffect(info) {
        val i = info ?: return@LaunchedEffect
        analysing = true
        try {
            analysis = withContext(Dispatchers.IO) { VideoToolkit.analyze(context, i.uri) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? VideoToolkit.EditException)?.message ?: "Could not analyse this video."
        } finally {
            analysing = false
        }
    }

    LaunchedEffect(info, analysis, posMs) {
        val i = info ?: return@LaunchedEffect
        val a = analysis ?: return@LaunchedEffect
        delay(150)
        preview = withContext(Dispatchers.IO) { VideoToolkit.previewFrame(context, i.uri, a.baseUs + posMs * 1000L) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { pickVideo.launch(arrayOf("video/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.VideoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text(if (info == null) "Choose a video" else "Choose another video")
        }

        val i = info
        val a = analysis
        if (i != null) {
            Text(
                "${i.name}  •  ${formatDuration(i.durationMs)}  •  ${i.width}×${i.height}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (analysing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Analysing frames…", fontSize = 13.sp)
            }
        }

        if (i != null && a != null) {
            val fps = a.fps
            val frameMs = 1000f / fps
            val maxPos = a.durationMs

            // --- preview + scrubber
            val aspect = if (i.width > 0 && i.height > 0) i.width.toFloat() / i.height else 16f / 9f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .aspectRatio(aspect.coerceIn(0.4f, 2.5f))
                    .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = preview
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Frame preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                "${formatTime(posMs)}   •   frame ${Math.round(posMs * fps / 1000f)}   •   ${"%.2f".format(fps)} fps",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = posMs.toFloat(),
                onValueChange = { posMs = it.toLong().coerceIn(0L, maxPos) },
                valueRange = 0f..maxPos.toFloat(),
                enabled = !busy
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { posMs = (posMs - 1000L).coerceAtLeast(0L) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("−1 s", fontSize = 12.sp) }
                OutlinedButton(onClick = { posMs = (posMs - frameMs).toLong().coerceAtLeast(0L) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("−1 frame", fontSize = 12.sp) }
                OutlinedButton(onClick = { posMs = (posMs + frameMs).toLong().coerceAtMost(maxPos) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+1 frame", fontSize = 12.sp) }
                OutlinedButton(onClick = { posMs = (posMs + 1000L).coerceAtMost(maxPos) }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+1 s", fontSize = 12.sp) }
            }

            // --- operation selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = op == 0, onClick = { op = 0 }, enabled = !busy, label = { Text("Remove") })
                FilterChip(selected = op == 1, onClick = { op = 1 }, enabled = !busy, label = { Text("Add") })
                FilterChip(selected = op == 2, onClick = { op = 2 }, enabled = !busy, label = { Text("Export") })
            }

            // --- start / end marks (used by Remove and Export)
            if (op != 1) {
                Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select a range", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { markStart = posMs }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text("Start: ${markStart?.let { formatTime(it) } ?: "—"}", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { markEnd = posMs }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text("End: ${markEnd?.let { formatTime(it) } ?: "—"}", fontSize = 12.sp)
                            }
                        }
                        Text(
                            "Move the slider to a frame, then tap Start or End.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when (op) {
                0 -> {
                    val ms = markStart
                    val me = markEnd
                    Button(
                        onClick = {
                            if (ms != null && me != null && me > ms) {
                                ranges = ranges + Pair(ms, me)
                                markStart = null
                                markEnd = null
                            }
                        },
                        enabled = !busy && ms != null && me != null && me > ms,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add range to remove") }

                    ranges.forEachIndexed { idx, r ->
                        val snap = a.snapRangeMs(r.first, r.second)
                        Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${formatTime(r.first)} → ${formatTime(r.second)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        "Removed exactly from ${formatTime(snap.first)} to ${formatTime(snap.second)} (nearest key frames)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(enabled = !busy, onClick = { ranges = ranges.toMutableList().also { it.removeAt(idx) } }) {
                                    Icon(Icons.Default.Close, "Remove range")
                                }
                            }
                        }
                    }
                    Text(
                        "Frames are never re-encoded, so a removal can only start and end on key frames. It is widened to the key frames around your selection, so every selected frame is gone and the rest stays 100% original quality.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { withStorage { vm.removeSections(i.uri, i.name, ranges) } },
                        enabled = !busy && ranges.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove & save automatically")
                    }
                }

                1 -> {
                    OutlinedButton(onClick = { pickInsert.launch(arrayOf("video/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (addInfo == null) "Choose the video to insert" else "Choose a different video")
                    }
                    addInfo?.let {
                        Text(
                            "${it.name}  •  ${formatDuration(it.durationMs)}  •  ${it.width}×${it.height}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Text(
                        if (forceInsert)
                            "⚡ Force Insert active: Will insert by streaming raw samples, bypassing strict encoder parameters or audio format checks."
                        else
                            "It will be inserted at ${formatTime(a.snapInsertMs(posMs))} (the key frame at or before the slider position) with no gap and no re-encoding. The video must use the same codec, resolution and audio settings as this one.",
                        fontSize = 12.sp,
                        color = if (forceInsert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (forceInsert)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (forceInsert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { forceInsert = !forceInsert }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (forceInsert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Force insert (bypass encoder checks)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = forceInsert,
                                onCheckedChange = { forceInsert = it },
                                enabled = !busy
                            )
                        }
                    }
                    Button(
                        onClick = { addInfo?.let { c -> withStorage { vm.insertClip(i.uri, c.uri, i.name, posMs, forceCombine = forceInsert) } } },
                        enabled = !busy && addInfo != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(if (forceInsert) Icons.Default.Bolt else Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (forceInsert) "⚡ Force insert & save" else "Insert & save automatically")
                    }
                }

                else -> {
                    Button(
                        onClick = { withStorage { vm.exportFrames(i.uri, i.name, a.baseUs, posMs, posMs, 0L, fps) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save this frame as PNG")
                    }
                    Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Export the selected range", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Every frame", "0.5 s", "1 s", "5 s").forEachIndexed { idx, label ->
                                    FilterChip(selected = stepChoice == idx, onClick = { stepChoice = idx }, enabled = !busy, label = { Text(label, fontSize = 12.sp) })
                                }
                            }
                            val stepUs = when (stepChoice) { 1 -> 500_000L; 2 -> 1_000_000L; 3 -> 5_000_000L; else -> 0L }
                            val ms = markStart
                            val me = markEnd
                            if (ms != null && me != null && me > ms) {
                                val stepD = if (stepUs > 0) stepUs.toDouble() else 1_000_000.0 / fps
                                val count = (((me - ms) * 1000L) / stepD).toInt() + 1
                                Text("About $count PNG image(s) (max 1000 at once)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Mark a start and an end above first.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    if (ms != null && me != null && me > ms) {
                                        withStorage { vm.exportFrames(i.uri, i.name, a.baseUs, ms, me, stepUs, fps) }
                                    }
                                },
                                enabled = !busy && ms != null && me != null && me > ms,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Export range as PNG") }
                        }
                    }
                    Text(
                        "Frames are saved as PNG at the video's full resolution (lossless) to Pictures/${VideoToolkit.FRAMES_FOLDER}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(14.dp)) {
                Text(it, modifier = Modifier.padding(14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
