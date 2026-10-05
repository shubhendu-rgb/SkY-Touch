package com.example.ui.island

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IslandShape {
    CLASSIC,  // Standard black rounded pill shape
    NOTCH,    // Contoured shape that adapts to phone's top notch/bezel
    SLIM,     // Thinner rounded black pill
    SLAB      // Clean rectangular block shape with subtle rounded corners
}

enum class IslandEffectType {
    SOLID,          // Solid color fill (e.g., Classic, Graphite)
    FROST,          // Translucent frosted glass effect
    MATERIAL_YOU,   // Dynamic color adaptation from system theme
    OUTLINE,        // Sharp border stroke
    GLOW,           // Single-color soft glowing halo edge
    GRADIENT_GLOW   // Dual-color or multi-color gradient glowing edge
}

enum class SkinCategory(val title: String) {
    CORE_SHAPES("Core Shapes"),
    MATERIAL_EFFECTS("Material & Effects"),
    GLOWING_EDGES("Glowing Edge"),
    GRADIENT_GLOWS("Gradient Glow")
}

data class DynamicIslandSkin(
    val id: String,
    val name: String,
    val category: SkinCategory,
    val description: String,
    val shape: IslandShape = IslandShape.CLASSIC,
    val effectType: IslandEffectType = IslandEffectType.SOLID,
    val baseColor: Color = Color.Black,
    val glowColors: List<Color> = emptyList(),
    val outlineColor: Color = Color.Transparent,
    val outlineWidth: Dp = 1.dp,
    val glowRadius: Dp = 8.dp,
    val alpha: Float = 1.0f,
    // Customization properties from user editor
    val customShape: String = "Pill", // "Pill", "Rounded", "Sharp", "Notch"
    val customWidthDp: Int = 126,
    val customHeightDp: Int = 32,
    val cornerRadiusDp: Int = 18,
    val expandedWidthPercent: Int = 88,
    val islandColor: Color = Color.Black,
    val textAndIconsColor: Color = Color.White,
    val opacityPercent: Int = 100,
    val borderColor: Color = Color(0xFF2C2C2E),
    val borderWidthDp: Int = 0,
    val glowColor: Color = Color(0xFF000000),
    val glowSizeDp: Int = 0,
    val shineColor: Color = Color(0xFF000000),
    val shinePercent: Int = 0,
    val accentOutline: Boolean = true,
    val dropShadow: Boolean = true
)

object PredefinedSkins {

    // 1. Core Shapes
    val CLASSIC = DynamicIslandSkin(
        id = "classic",
        name = "Classic",
        category = SkinCategory.CORE_SHAPES,
        description = "Standard black rounded pill shape with smooth curvature.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.SOLID,
        baseColor = Color(0xFF000000)
    )

    val NOTCH = DynamicIslandSkin(
        id = "notch",
        name = "Notch",
        category = SkinCategory.CORE_SHAPES,
        description = "Contoured shape that adapts seamlessly to phone's notch cutout.",
        shape = IslandShape.NOTCH,
        effectType = IslandEffectType.SOLID,
        baseColor = Color(0xFF000000)
    )

    val SLIM = DynamicIslandSkin(
        id = "slim",
        name = "Slim",
        category = SkinCategory.CORE_SHAPES,
        description = "Thinner, ultra-compact rounded black pill for minimal obstruction.",
        shape = IslandShape.SLIM,
        effectType = IslandEffectType.SOLID,
        baseColor = Color(0xFF000000)
    )

    val SLAB = DynamicIslandSkin(
        id = "slab",
        name = "Slab",
        category = SkinCategory.CORE_SHAPES,
        description = "Clean rectangular block shape with modern rounded corners.",
        shape = IslandShape.SLAB,
        effectType = IslandEffectType.SOLID,
        baseColor = Color(0xFF000000)
    )

    // 2. Material and Effect Styles
    val GRAPHITE = DynamicIslandSkin(
        id = "graphite",
        name = "Graphite",
        category = SkinCategory.MATERIAL_EFFECTS,
        description = "Dark gray-tinted pill shape with refined charcoal matte finish.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.SOLID,
        baseColor = Color(0xFF2C2C2E),
        outlineColor = Color(0xFF48484A),
        outlineWidth = 1.dp
    )

    val FROST = DynamicIslandSkin(
        id = "frost",
        name = "Frost",
        category = SkinCategory.MATERIAL_EFFECTS,
        description = "Translucent pill with frosted glass blur effect and subtle glass rim.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.FROST,
        baseColor = Color(0x991C1C1E),
        outlineColor = Color(0x4DFFFFFF),
        outlineWidth = 1.dp,
        alpha = 0.85f
    )

    val MATERIAL_YOU = DynamicIslandSkin(
        id = "material_you",
        name = "Material You",
        category = SkinCategory.MATERIAL_EFFECTS,
        description = "Dynamic color adaptation based on the current Android system palette.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.MATERIAL_YOU,
        baseColor = Color.Black
    )

    val OUTLINE = DynamicIslandSkin(
        id = "outline",
        name = "Outline",
        category = SkinCategory.MATERIAL_EFFECTS,
        description = "Deep black pill framed by a precise, sharp crisp white stroke border.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.OUTLINE,
        baseColor = Color(0xFF000000),
        outlineColor = Color(0xFFFFFFFF),
        outlineWidth = 1.5.dp
    )

    // 3. Glowing Edge Styles
    val HALO = DynamicIslandSkin(
        id = "halo",
        name = "Halo",
        category = SkinCategory.GLOWING_EDGES,
        description = "Soft, glowing halo effect with an ambient blue and purple hue.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFF5B7FFF), Color(0xFF8A5BFF)),
        outlineColor = Color(0xFF7B8CFF),
        outlineWidth = 1.dp,
        glowRadius = 12.dp
    )

    val EMBER = DynamicIslandSkin(
        id = "ember",
        name = "Ember",
        category = SkinCategory.GLOWING_EDGES,
        description = "Soft, glowing halo effect with a burning orange and red hue.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFFFF5722), Color(0xFFFF3D00)),
        outlineColor = Color(0xFFFF6E40),
        outlineWidth = 1.dp,
        glowRadius = 12.dp
    )

    val MINT = DynamicIslandSkin(
        id = "mint",
        name = "Mint",
        category = SkinCategory.GLOWING_EDGES,
        description = "Soft, glowing halo effect with a vibrant neon green hue.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
        outlineColor = Color(0xFF69F0AE),
        outlineWidth = 1.dp,
        glowRadius = 12.dp
    )

    val NEON = DynamicIslandSkin(
        id = "neon",
        name = "Neon",
        category = SkinCategory.GLOWING_EDGES,
        description = "Sharp, distinct cyber cyan glowing edge effect.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFF00E5FF), Color(0xFF00B0FF)),
        outlineColor = Color(0xFF00E5FF),
        outlineWidth = 1.5.dp,
        glowRadius = 14.dp
    )

    // 4. Gradient Glowing Edge Styles
    val AURORA = DynamicIslandSkin(
        id = "aurora",
        name = "Aurora",
        category = SkinCategory.GRADIENT_GLOWS,
        description = "Dual-color glowing edge transitioning smoothly between cyan and purple.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GRADIENT_GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFF00E5FF), Color(0xFFD500F9)),
        outlineColor = Color.Transparent,
        outlineWidth = 1.5.dp,
        glowRadius = 14.dp
    )

    val SUNSET = DynamicIslandSkin(
        id = "sunset",
        name = "Sunset",
        category = SkinCategory.GRADIENT_GLOWS,
        description = "Warm glowing edge transitioning between radiant orange and deep red.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GRADIENT_GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFFFF9100), Color(0xFFFF1744)),
        outlineColor = Color.Transparent,
        outlineWidth = 1.5.dp,
        glowRadius = 14.dp
    )

    val GILT = DynamicIslandSkin(
        id = "gilt",
        name = "Gilt",
        category = SkinCategory.GRADIENT_GLOWS,
        description = "Subtle, glowing edge effect with a rich champagne gold tint.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GRADIENT_GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFFFFD700), Color(0xFFFFAB00), Color(0xFFFFE57F)),
        outlineColor = Color.Transparent,
        outlineWidth = 1.2.dp,
        glowRadius = 12.dp
    )

    val PRISM = DynamicIslandSkin(
        id = "prism",
        name = "Prism",
        category = SkinCategory.GRADIENT_GLOWS,
        description = "Broad, multi-color rainbow spectrum gradient glowing edge.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GRADIENT_GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(
            Color(0xFFFF1744),
            Color(0xFFFF9100),
            Color(0xFFFFEA00),
            Color(0xFF00E676),
            Color(0xFF00E5FF),
            Color(0xFF7C4DFF),
            Color(0xFFFF1744)
        ),
        outlineColor = Color.Transparent,
        outlineWidth = 2.dp,
        glowRadius = 16.dp
    )

    val COMET = DynamicIslandSkin(
        id = "comet",
        name = "Comet",
        category = SkinCategory.GRADIENT_GLOWS,
        description = "Broad interstellar purple and electric blue glowing edge effect.",
        shape = IslandShape.CLASSIC,
        effectType = IslandEffectType.GRADIENT_GLOW,
        baseColor = Color(0xFF000000),
        glowColors = listOf(Color(0xFF7C4DFF), Color(0xFF2979FF), Color(0xFF00E5FF)),
        outlineColor = Color.Transparent,
        outlineWidth = 1.5.dp,
        glowRadius = 15.dp
    )

    val allSkins: List<DynamicIslandSkin> = listOf(
        // Core Shapes
        CLASSIC,
        NOTCH,
        SLIM,
        SLAB,
        // Material & Effect Styles
        GRAPHITE,
        FROST,
        MATERIAL_YOU,
        OUTLINE,
        // Glowing Edge Styles
        HALO,
        EMBER,
        MINT,
        NEON,
        // Gradient Glowing Edge Styles
        AURORA,
        SUNSET,
        GILT,
        PRISM,
        COMET
    )

    fun getSkinById(id: String): DynamicIslandSkin {
        return allSkins.find { it.id.equals(id, ignoreCase = true) } ?: CLASSIC
    }

    private const val PREFS_NAME = "dynamic_island_skin_prefs"
    private const val KEY_SELECTED_SKIN = "selected_skin_id"

    fun getSavedSkin(context: Context): DynamicIslandSkin {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skinId = prefs.getString(KEY_SELECTED_SKIN, CLASSIC.id) ?: CLASSIC.id
        val base = getSkinById(skinId)
        return getSkinWithCustomizations(context, base)
    }

    fun saveSkin(context: Context, skin: DynamicIslandSkin) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_SKIN, skin.id).apply()
    }

    fun getSkinWithCustomizations(context: Context, base: DynamicIslandSkin): DynamicIslandSkin {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = "skin_${base.id}_"
        if (!prefs.contains(prefix + "customized")) {
            return base
        }
        val defaultIslandColor = if (base.islandColor != Color.Black) base.islandColor else base.baseColor
        val savedIslandColor = prefs.getInt(prefix + "island_color", defaultIslandColor.toArgb())
        val savedTextColor = prefs.getInt(prefix + "text_color", base.textAndIconsColor.toArgb())
        val savedBorderColor = prefs.getInt(prefix + "border_color", base.borderColor.toArgb())
        val savedGlowColor = prefs.getInt(prefix + "glow_color", base.glowColor.toArgb())
        val savedShineColor = prefs.getInt(prefix + "shine_color", base.shineColor.toArgb())

        return base.copy(
            customShape = prefs.getString(prefix + "shape", base.customShape) ?: base.customShape,
            customWidthDp = prefs.getInt(prefix + "width", base.customWidthDp),
            customHeightDp = prefs.getInt(prefix + "height", base.customHeightDp),
            expandedWidthPercent = prefs.getInt(prefix + "exp_width", base.expandedWidthPercent),
            islandColor = Color(savedIslandColor),
            textAndIconsColor = Color(savedTextColor),
            opacityPercent = prefs.getInt(prefix + "opacity", base.opacityPercent),
            borderColor = Color(savedBorderColor),
            borderWidthDp = prefs.getInt(prefix + "border_width", base.borderWidthDp),
            glowColor = Color(savedGlowColor),
            glowSizeDp = prefs.getInt(prefix + "glow_size", base.glowSizeDp),
            shineColor = Color(savedShineColor),
            shinePercent = prefs.getInt(prefix + "shine_percent", base.shinePercent),
            accentOutline = prefs.getBoolean(prefix + "accent_outline", base.accentOutline),
            dropShadow = prefs.getBoolean(prefix + "drop_shadow", base.dropShadow)
        )
    }

    fun saveCustomizedSkin(context: Context, skin: DynamicIslandSkin) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = "skin_${skin.id}_"
        val effectiveColorToSave = if (skin.islandColor != Color.Black) skin.islandColor else skin.baseColor
        prefs.edit()
            .putBoolean(prefix + "customized", true)
            .putString(prefix + "shape", skin.customShape)
            .putInt(prefix + "width", skin.customWidthDp)
            .putInt(prefix + "height", skin.customHeightDp)
            .putInt(prefix + "exp_width", skin.expandedWidthPercent)
            .putInt(prefix + "island_color", effectiveColorToSave.toArgb())
            .putInt(prefix + "text_color", skin.textAndIconsColor.toArgb())
            .putInt(prefix + "opacity", skin.opacityPercent)
            .putInt(prefix + "border_color", skin.borderColor.toArgb())
            .putInt(prefix + "border_width", skin.borderWidthDp)
            .putInt(prefix + "glow_color", skin.glowColor.toArgb())
            .putInt(prefix + "glow_size", skin.glowSizeDp)
            .putInt(prefix + "shine_color", skin.shineColor.toArgb())
            .putInt(prefix + "shine_percent", skin.shinePercent)
            .putBoolean(prefix + "accent_outline", skin.accentOutline)
            .putBoolean(prefix + "drop_shadow", skin.dropShadow)
            .apply()
    }

    fun resetPreset(context: Context, skinId: String): DynamicIslandSkin {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = "skin_${skinId}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
        return getSkinById(skinId)
    }
}
