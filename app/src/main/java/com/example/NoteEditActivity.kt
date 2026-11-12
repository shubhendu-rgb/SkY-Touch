package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NotesStore
import com.example.service.NotchAccessibilityService
import com.example.ui.theme.MyApplicationTheme

/**
 * Small window on top of the current app for writing a new quick note or editing one.
 * After saving or cancelling, the Quick Notes panel slides down again.
 */
class NoteEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotesStore.init(this)
        val noteId = intent?.getStringExtra(EXTRA_NOTE_ID)
        val existing = noteId?.let { NotesStore.get(it) }

        setContent {
            MyApplicationTheme {
                NoteEditorDialog(
                    initialText = existing?.text.orEmpty(),
                    isEdit = existing != null,
                    onSave = { text ->
                        if (existing != null) NotesStore.update(this, existing.id, text)
                        else NotesStore.add(this, text)
                        finishAndReopenPanel()
                    },
                    onCancel = { finishAndReopenPanel() }
                )
            }
        }
    }

    private fun finishAndReopenPanel() {
        NotchAccessibilityService.openNotesPanel()
        finish()
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}

@Composable
private fun NoteEditorDialog(
    initialText: String,
    isEdit: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            ,
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (isEdit) "Edit note" else "New note",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 220.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Write your note...") },
                    shape = RoundedCornerShape(16.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(
                        onClick = { onSave(text) },
                        enabled = text.isNotBlank(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Save") }
                }
            }
        }
    }
}
