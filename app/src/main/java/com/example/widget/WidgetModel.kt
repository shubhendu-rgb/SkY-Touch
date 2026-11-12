package com.example.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** What a component shows. Every type except BACKGROUND is drawn as auto-fitted text. */
@Serializable
enum class ComponentType(val label: String) {
    BACKGROUND("Background"),
    TIME("Time"),
    AMPM("AM / PM"),
    DATE("Date"),
    WEEKDAY("Weekday"),
    BATTERY("Battery"),
    TEXT("Custom text"),
    IMAGE("Image")
}

/** Material 3 colour roles. At draw time they are read from the wallpaper-derived system palette (Android 12+). */
@Serializable
enum class DynRole(val label: String) {
    PRIMARY("Primary"),
    ON_PRIMARY("On primary"),
    PRIMARY_CONTAINER("Primary container"),
    ON_PRIMARY_CONTAINER("On primary container"),
    SECONDARY("Secondary"),
    SECONDARY_CONTAINER("Secondary container"),
    TERTIARY("Tertiary"),
    TERTIARY_CONTAINER("Tertiary container"),
    SURFACE("Surface"),
    ON_SURFACE("On surface"),
    SURFACE_VARIANT("Surface variant"),
    ON_SURFACE_VARIANT("On surface variant"),
    OUTLINE("Outline")
}

@Serializable
enum class FontChoice(val label: String, val family: String) {
    SANS("Sans", "sans-serif"),
    LIGHT("Light", "sans-serif-light"),
    MEDIUM("Medium", "sans-serif-medium"),
    CONDENSED("Condensed", "sans-serif-condensed"),
    SERIF("Serif", "serif"),
    MONO("Mono", "monospace"),
    CURSIVE("Cursive", "cursive")
}

@Serializable
enum class ImageFit(val label: String) { FIT("Fit"), FILL("Fill / crop"), STRETCH("Stretch") }

@Serializable
enum class TextAlignment(val label: String) { START("Left"), CENTER("Center"), END("Right") }

@Serializable
enum class DynTheme(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

/** Either a fixed ARGB colour or a dynamic Material role. [alpha] (0..1) applies to both. */
@Serializable
data class ColorSpec(
    val dynamic: Boolean = false,
    val argb: Int = 0xFFFFFFFF.toInt(),
    val role: DynRole = DynRole.ON_SURFACE,
    val alpha: Float = 1f
) {
    companion object {
        fun fixed(argb: Int, alpha: Float = 1f) = ColorSpec(false, argb, DynRole.ON_SURFACE, alpha)
        fun dyn(role: DynRole, alpha: Float = 1f) = ColorSpec(true, 0xFFFFFFFF.toInt(), role, alpha)
    }
}

/** Click action attached to a widget component or the entire widget container. */
@Serializable
data class WidgetClickAction(
    val actionType: String = "NONE",
    val packageToLaunch: String? = null,
    val label: String = "Disabled (No Action)",
    val extraValue: String? = null
)

/**
 * One element of the widget. x/y/w/h are fractions (0..1) of the widget, so a layout scales to any widget size.
 * Text is auto-fitted inside the box; [textScale] only shrinks it (0.3..1).
 */
@Serializable
data class WidgetComponent(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: ComponentType,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: ColorSpec = ColorSpec.dyn(DynRole.ON_SURFACE),
    val visible: Boolean = true,
    val font: FontChoice = FontChoice.SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val allCaps: Boolean = false,
    val align: TextAlignment = TextAlignment.CENTER,
    val textScale: Float = 1f,
    val text: String = "Hello",
    val format: String = "",
    val use24h: Boolean = false,
    val cornerDp: Float = 28f,
    /** File name of an imported font (see [WidgetAssets]); blank = use [font]. */
    val customFont: String = "",
    /** File name of an imported image (IMAGE components). */
    val image: String = "",
    val imageFit: ImageFit = ImageFit.FILL,
    /** Paint the image in the component's colour (good for single-colour icons + Material You). */
    val tintImage: Boolean = false,
    /** Click action when this component is tapped on the home screen. */
    val clickAction: WidgetClickAction = WidgetClickAction()
)

@Serializable
data class WidgetLayout(
    val components: List<WidgetComponent>,
    val theme: DynTheme = DynTheme.SYSTEM,
    /** Whether touch action on widget background / blank areas is enabled. When false, only components respond to touch. */
    val enableBackgroundClick: Boolean = false,
    /** Action triggered when clicking the widget background or unassigned space. */
    val clickAction: WidgetClickAction = WidgetClickAction(actionType = "OPEN_EDITOR", label = "Open Widget Editor"),
    /** Base width in dp used when designing the layout (defaults to standard 4x2 250dp). */
    val baseWidthDp: Float = 250f,
    /** Base height in dp used when designing the layout (defaults to standard 4x2 110dp). */
    val baseHeightDp: Float = 110f,
    /** When true, automatically adjusts component sizes and keeps relative distance proportional when resized in launcher. */
    val autoAdjust: Boolean = true
)

val WidgetJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

object DefaultLayouts {
    fun create(): WidgetLayout = WidgetLayout(
        components = listOf(
            WidgetComponent(
                type = ComponentType.BACKGROUND, x = 0f, y = 0f, w = 1f, h = 1f,
                color = ColorSpec.dyn(DynRole.SURFACE_VARIANT, 0.92f), cornerDp = 28f
            ),
            WidgetComponent(
                type = ComponentType.TIME, x = 0.05f, y = 0.06f, w = 0.66f, h = 0.54f,
                color = ColorSpec.dyn(DynRole.ON_SURFACE), font = FontChoice.LIGHT, align = TextAlignment.START
            ),
            WidgetComponent(
                type = ComponentType.AMPM, x = 0.72f, y = 0.30f, w = 0.20f, h = 0.20f,
                color = ColorSpec.dyn(DynRole.PRIMARY), bold = true, align = TextAlignment.START
            ),
            WidgetComponent(
                type = ComponentType.DATE, x = 0.05f, y = 0.62f, w = 0.90f, h = 0.15f,
                color = ColorSpec.dyn(DynRole.PRIMARY), format = "EEE, d MMM", align = TextAlignment.START
            ),
            WidgetComponent(
                type = ComponentType.BATTERY, x = 0.05f, y = 0.80f, w = 0.30f, h = 0.13f,
                color = ColorSpec.dyn(DynRole.SECONDARY), align = TextAlignment.START
            )
        )
    )

    /** Starting values for a component added from the editor. */
    fun newComponent(type: ComponentType): WidgetComponent = when (type) {
        ComponentType.BACKGROUND -> WidgetComponent(
            type = type, x = 0f, y = 0f, w = 1f, h = 1f, color = ColorSpec.dyn(DynRole.PRIMARY_CONTAINER, 0.9f)
        )
        ComponentType.TIME -> WidgetComponent(type = type, x = 0.1f, y = 0.1f, w = 0.6f, h = 0.4f)
        ComponentType.AMPM -> WidgetComponent(type = type, x = 0.72f, y = 0.3f, w = 0.2f, h = 0.2f)
        ComponentType.DATE -> WidgetComponent(type = type, x = 0.1f, y = 0.6f, w = 0.8f, h = 0.15f, format = "EEE, d MMM")
        ComponentType.WEEKDAY -> WidgetComponent(type = type, x = 0.1f, y = 0.6f, w = 0.5f, h = 0.15f, format = "EEEE")
        ComponentType.BATTERY -> WidgetComponent(type = type, x = 0.1f, y = 0.8f, w = 0.3f, h = 0.13f)
        ComponentType.TEXT -> WidgetComponent(type = type, x = 0.2f, y = 0.35f, w = 0.6f, h = 0.25f)
        ComponentType.IMAGE -> WidgetComponent(
            type = type, x = 0.72f, y = 0.08f, w = 0.22f, h = 0.4f,
            color = ColorSpec.fixed(0xFFFFFFFF.toInt()), cornerDp = 12f
        )
    }
}
