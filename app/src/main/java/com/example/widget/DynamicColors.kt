package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build

/**
 * Resolves a [ColorSpec] to an ARGB int. Dynamic roles map to the Android 12+ system palette
 * (the same tokens Compose's dynamicLightColorScheme / dynamicDarkColorScheme use), so the widget
 * matches the wallpaper colours exactly. Below Android 12 a fixed fallback palette is used.
 */
object DynamicColors {

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isDark(context: Context, theme: DynTheme): Boolean = when (theme) {
        DynTheme.LIGHT -> false
        DynTheme.DARK -> true
        DynTheme.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /** light token, dark token, light fallback, dark fallback */
    private class Entry(val light: String, val dark: String, val fbLight: Int, val fbDark: Int)

    private val table: Map<DynRole, Entry> = mapOf(
        DynRole.PRIMARY to Entry("accent1_600", "accent1_200", 0xFF4F46E5.toInt(), 0xFF818CF8.toInt()),
        DynRole.ON_PRIMARY to Entry("accent1_0", "accent1_800", 0xFFFFFFFF.toInt(), 0xFF1E1B4B.toInt()),
        DynRole.PRIMARY_CONTAINER to Entry("accent1_100", "accent1_700", 0xFFEEF2FF.toInt(), 0xFF312E81.toInt()),
        DynRole.ON_PRIMARY_CONTAINER to Entry("accent1_900", "accent1_100", 0xFF312E81.toInt(), 0xFFE0E7FF.toInt()),
        DynRole.SECONDARY to Entry("accent2_600", "accent2_200", 0xFF6366F1.toInt(), 0xFFA5B4FC.toInt()),
        DynRole.SECONDARY_CONTAINER to Entry("accent2_100", "accent2_700", 0xFFE0E7FF.toInt(), 0xFF3730A3.toInt()),
        DynRole.TERTIARY to Entry("accent3_600", "accent3_200", 0xFF7C3AED.toInt(), 0xFFC084FC.toInt()),
        DynRole.TERTIARY_CONTAINER to Entry("accent3_100", "accent3_700", 0xFFF3E8FF.toInt(), 0xFF581C87.toInt()),
        DynRole.SURFACE to Entry("neutral1_10", "neutral1_900", 0xFFFFFFFF.toInt(), 0xFF1E293B.toInt()),
        DynRole.ON_SURFACE to Entry("neutral1_900", "neutral1_100", 0xFF0F172A.toInt(), 0xFFF8FAFC.toInt()),
        DynRole.SURFACE_VARIANT to Entry("neutral2_100", "neutral2_700", 0xFFF1F5F9.toInt(), 0xFF334155.toInt()),
        DynRole.ON_SURFACE_VARIANT to Entry("neutral2_700", "neutral2_200", 0xFF64748B.toInt(), 0xFF94A3B8.toInt()),
        DynRole.OUTLINE to Entry("neutral2_500", "neutral2_400", 0xFFCBD5E1.toInt(), 0xFF64748B.toInt())
    )

    /** Opaque colour of a role. */
    fun role(context: Context, role: DynRole, dark: Boolean): Int {
        val e = table.getValue(role)
        val fallback = if (dark) e.fbDark else e.fbLight
        if (!supported) return fallback
        val name = "system_" + (if (dark) e.dark else e.light)
        val id = context.resources.getIdentifier(name, "color", "android")
        return if (id != 0) {
            try { context.getColor(id) } catch (_: Throwable) { fallback }
        } else fallback
    }

    /** Final colour of a spec, with its alpha applied. */
    fun resolve(context: Context, spec: ColorSpec, dark: Boolean): Int {
        val base = if (spec.dynamic) role(context, spec.role, dark) else spec.argb
        val a = (Color.alpha(base) * spec.alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(base), Color.green(base), Color.blue(base))
    }
}
