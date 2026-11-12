package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One quick note. The list order is the display order; pinned notes are always kept first. */
data class QuickNote(
    val id: String = UUID.randomUUID().toString().take(10),
    val text: String,
    val pinned: Boolean = false
)

/**
 * Storage for the notch "Quick Notes" panel (SharedPreferences, no database change).
 * The same object is used by the overlay and the note editor, so both stay in sync through [notes].
 */
object NotesStore {
    private const val PREFS = "quick_notes_prefs"
    private const val KEY = "notes"

    private val _notes = MutableStateFlow<List<QuickNote>>(emptyList())
    val notes: StateFlow<List<QuickNote>> = _notes.asStateFlow()

    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        _notes.value = decode(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        )
        loaded = true
    }

    private fun normalize(list: List<QuickNote>): List<QuickNote> =
        list.filter { it.pinned } + list.filter { !it.pinned }

    private fun save(context: Context, list: List<QuickNote>) {
        val ordered = normalize(list)
        _notes.value = ordered
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, encode(ordered)).apply()
    }

    fun get(id: String): QuickNote? = _notes.value.firstOrNull { it.id == id }

    /** Adds a new note at the end of the unpinned notes. Blank text is ignored. */
    @Synchronized
    fun add(context: Context, text: String) {
        init(context)
        val clean = text.trim()
        if (clean.isEmpty()) return
        save(context, _notes.value + QuickNote(text = clean))
    }

    @Synchronized
    fun update(context: Context, id: String, text: String) {
        init(context)
        val clean = text.trim()
        if (clean.isEmpty()) return
        save(context, _notes.value.map { if (it.id == id) it.copy(text = clean) else it })
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        init(context)
        save(context, _notes.value.filterNot { it.id == id })
    }

    /** Pinning moves the note to the end of the pinned notes, unpinning to the start of the others. */
    @Synchronized
    fun togglePin(context: Context, id: String) {
        init(context)
        val current = _notes.value
        val note = current.firstOrNull { it.id == id } ?: return
        val rest = current.filterNot { it.id == id }
        val toggled = note.copy(pinned = !note.pinned)
        val pinned = rest.filter { it.pinned }
        val others = rest.filter { !it.pinned }
        save(context, if (toggled.pinned) pinned + toggled + others else pinned + toggled + others)
    }

    /** Moves a note one place up (-1) or down (+1) inside its own group (pinned / not pinned). */
    @Synchronized
    fun move(context: Context, id: String, delta: Int) {
        init(context)
        val current = _notes.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = index + delta
        if (target !in current.indices) return
        if (current[target].pinned != current[index].pinned) return
        val item = current.removeAt(index)
        current.add(target, item)
        save(context, current)
    }

    fun encode(list: List<QuickNote>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("pinned", it.pinned))
        }
        return arr.toString()
    }

    fun decode(json: String?): List<QuickNote> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<QuickNote>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val text = o.optString("text", "")
                if (text.isBlank()) continue
                out.add(QuickNote(id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(10) }, text = text, pinned = o.optBoolean("pinned", false)))
            }
            normalize(out)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Replaces all notes (used by Restore). */
    @Synchronized
    fun replaceAll(context: Context, list: List<QuickNote>) {
        init(context)
        save(context, list)
    }
}
