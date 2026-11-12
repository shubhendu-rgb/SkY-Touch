package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.NoteEditActivity
import com.example.data.NotesStore
import com.example.data.QuickNote
import com.example.ui.theme.MyApplicationTheme

/**
 * "Quick Notes" panel: slides down from the top of the screen over every app. It lists the saved notes
 * (selectable text, a copy button on every note, pin / rearrange / edit / delete), scrolls when there are
 * many notes and grows to at most half of the screen height.
 */
class NotesOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private var container: FrameLayout? = null
    private var owner: ServiceOverlayLifecycleOwner? = null
    private var removeRunnable: Runnable? = null
    private val visibleState = mutableStateOf(false)

    val isShowing: Boolean get() = container != null && visibleState.value

    fun toggle() {
        if (isShowing) hide() else show()
    }

    fun show() {
        removeRunnable?.let { handler.removeCallbacks(it) }
        removeRunnable = null
        if (container != null) {
            visibleState.value = true
            return
        }

        NotesStore.init(service)
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = service.resources.displayMetrics
        val density = metrics.density

        val statusBarId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarPx = if (statusBarId > 0) service.resources.getDimensionPixelSize(statusBarId) else (24 * density).toInt()
        val topInset = (statusBarPx / density).dp
        val maxPanelHeight = (metrics.heightPixels / 2f / density).dp

        val lifecycleOwner = ServiceOverlayLifecycleOwner().also {
            it.performRestore(null)
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        val composeView = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MyApplicationTheme {
                    val visible by visibleState
                    NotesPanel(
                        visible = visible,
                        topInset = topInset,
                        maxPanelHeight = maxPanelHeight,
                        onClose = { hide() },
                        onCopy = { copyNote(it) },
                        onAdd = { openEditor(null) },
                        onEdit = { openEditor(it) }
                    )
                }
            }
        }

        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hide()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            isFocusableInTouchMode = true
        }
        root.addView(
            composeView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        try {
            windowManager.addView(root, params)
            container = root
            owner = lifecycleOwner
            root.requestFocus()
            // Start the slide-down animation on the next frame
            handler.post { visibleState.value = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notes panel", e)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    fun hide() {
        if (container == null) return
        visibleState.value = false
        removeRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { removeWindow() }
        removeRunnable = runnable
        handler.postDelayed(runnable, 300L)
    }

    /** Removes the window immediately (service shutting down). */
    fun destroy() {
        removeRunnable?.let { handler.removeCallbacks(it) }
        removeRunnable = null
        removeWindow()
    }

    private fun removeWindow() {
        val view = container ?: return
        try {
            (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing notes panel", e)
        }
        container = null
        visibleState.value = false
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        owner = null
        removeRunnable = null
    }

    private fun copyNote(text: String) {
        try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Quick note", text))
            Toast.makeText(service, "Note copied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed", e)
        }
    }

    /** Opens the small editor window to add a note (id = null) or edit one; the panel reopens after saving. */
    private fun openEditor(noteId: String?) {
        hide()
        try {
            val intent = Intent(service, NoteEditActivity::class.java).apply {
                if (noteId != null) putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open note editor", e)
        }
    }

    companion object {
        private const val TAG = "NotesOverlay"
    }
}

@Composable
private fun NotesPanel(
    visible: Boolean,
    topInset: Dp,
    maxPanelHeight: Dp,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notes by NotesStore.notes.collectAsState()
    var manageMode by remember { mutableStateOf(false) }
    val scrim by animateColorAsState(
        targetValue = if (visible) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = tween(250),
        label = "notes_scrim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = tween(280)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(240)) { -it } + fadeOut(tween(200))
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight)
                    // Taps on the panel must not reach the dismiss area behind it
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(modifier = Modifier.padding(top = topInset)) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.StickyNote2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Quick Notes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { manageMode = !manageMode }) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Rearrange and edit",
                                tint = if (manageMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = "Add note", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    if (notes.isEmpty()) {
                        Text(
                            "No notes yet. Tap + to add one.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                val index = notes.indexOfFirst { it.id == note.id }
                                val canMoveUp = index > 0 && notes[index - 1].pinned == note.pinned
                                val canMoveDown = index < notes.lastIndex && notes[index + 1].pinned == note.pinned
                                NoteRow(
                                    note = note,
                                    manageMode = manageMode,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onCopy = { onCopy(note.text) },
                                    onTogglePin = { NotesStore.togglePin(context, note.id) },
                                    onMoveUp = { NotesStore.move(context, note.id, -1) },
                                    onMoveDown = { NotesStore.move(context, note.id, 1) },
                                    onEdit = { onEdit(note.id) },
                                    onDelete = { NotesStore.delete(context, note.id) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: QuickNote,
    manageMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (note.pinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.text,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = if (note.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                        tint = if (note.pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy note",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            if (manageMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit note", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
