package com.example.widget

import android.content.Context

/** Saves one layout per home-screen widget id, plus a template that new widgets start from. */
object WidgetStore {
    private const val PREFS = "sky_widget_engine"
    const val TEMPLATE_ID = -1

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(id: Int) = if (id == TEMPLATE_ID) "layout_template" else "layout_$id"

    private fun read(context: Context, id: Int): WidgetLayout? =
        prefs(context).getString(key(id), null)?.let {
            runCatching { WidgetJson.decodeFromString<WidgetLayout>(it) }.getOrNull()
        }

    /** The widget's own layout, else the saved template, else the built-in default. */
    fun load(context: Context, id: Int): WidgetLayout =
        read(context, id) ?: (if (id != TEMPLATE_ID) read(context, TEMPLATE_ID) else null) ?: DefaultLayouts.create()

    fun save(context: Context, id: Int, layout: WidgetLayout) {
        prefs(context).edit().putString(key(id), WidgetJson.encodeToString(WidgetLayout.serializer(), layout)).apply()
    }

    fun delete(context: Context, id: Int) {
        prefs(context).edit().remove(key(id)).apply()
    }

    /** Image files used by any saved layout (so unused imports can be cleaned up). */
    fun referencedImages(context: Context): Set<String> {
        val out = HashSet<String>()
        prefs(context).all.forEach { (k, v) ->
            if (k.startsWith("layout_") && v is String) {
                runCatching { WidgetJson.decodeFromString<WidgetLayout>(v) }.getOrNull()
                    ?.components?.forEach { if (it.image.isNotBlank()) out.add(it.image) }
            }
        }
        return out
    }
}
