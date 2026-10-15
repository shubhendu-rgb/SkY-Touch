package com.example.service

import android.content.Context

/**
 * User-configurable rules that decide HOW the wallpaper Quick Settings tiles and the
 * notch gestures change the wallpaper.
 *
 * Stored in SharedPreferences on purpose: WallpaperConfigEntity lives in a Room database that
 * uses fallbackToDestructiveMigration, so adding a column there would wipe every user setting.
 */
object WallpaperRules {

    // Triggers (who is changing the wallpaper)
    const val TRIGGER_TILE_IMAGES = "TILE_IMAGES"
    const val TRIGGER_TILE_FOLDER = "TILE_FOLDER"
    const val TRIGGER_GESTURE = "GESTURE"
    val ALL_TRIGGERS = listOf(TRIGGER_TILE_IMAGES, TRIGGER_TILE_FOLDER, TRIGGER_GESTURE)

    // Which screen(s) get changed
    const val TARGET_HOME = "HOME"
    const val TARGET_LOCK = "LOCK"
    const val TARGET_BOTH = "BOTH"

    // Where the next wallpaper is picked from (values match WallpaperWorker's sourceFilter)
    const val SOURCE_ANY = "ANY"
    const val SOURCE_IMAGES = "IMAGES_ONLY"
    const val SOURCE_FOLDER = "FOLDER_ONLY"

    // Order used by tiles
    const val ORDER_SEQUENTIAL = "SEQUENTIAL"
    const val ORDER_RANDOM = "RANDOM"

    data class Rule(
        val target: String = TARGET_BOTH,
        val source: String = SOURCE_ANY,
        val order: String = ORDER_SEQUENTIAL,
        /** Only matters when target == BOTH: use the very same picture on Home and Lock. */
        val sameOnBoth: Boolean = true
    ) {
        /** Source filter understood by WallpaperWorker (null = images and folder). */
        val sourceFilter: String? get() = if (source == SOURCE_ANY) null else source
    }

    fun defaultFor(trigger: String): Rule = when (trigger) {
        TRIGGER_TILE_IMAGES -> Rule(source = SOURCE_IMAGES)
        TRIGGER_TILE_FOLDER -> Rule(source = SOURCE_FOLDER)
        else -> Rule()
    }

    private const val PREFS = "wallpaper_rules_prefs"

    fun load(context: Context, trigger: String): Rule {
        val d = defaultFor(trigger)
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = p.getString("${trigger}_target", d.target) ?: d.target
        val source = p.getString("${trigger}_source", d.source) ?: d.source
        val order = p.getString("${trigger}_order", d.order) ?: d.order
        return Rule(
            target = if (target in listOf(TARGET_HOME, TARGET_LOCK, TARGET_BOTH)) target else d.target,
            // Tiles have a fixed source, whatever was stored.
            source = when (trigger) {
                TRIGGER_TILE_IMAGES -> SOURCE_IMAGES
                TRIGGER_TILE_FOLDER -> SOURCE_FOLDER
                else -> if (source in listOf(SOURCE_ANY, SOURCE_IMAGES, SOURCE_FOLDER)) source else d.source
            },
            order = if (order == ORDER_RANDOM) ORDER_RANDOM else ORDER_SEQUENTIAL,
            sameOnBoth = p.getBoolean("${trigger}_same", d.sameOnBoth)
        )
    }

    fun save(context: Context, trigger: String, rule: Rule) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${trigger}_target", rule.target)
            .putString("${trigger}_source", rule.source)
            .putString("${trigger}_order", rule.order)
            .putBoolean("${trigger}_same", rule.sameOnBoth)
            .apply()
    }

    /** Plain-language summary shown under the rule editor. */
    fun describe(trigger: String, rule: Rule): String {
        val screens = when (rule.target) {
            TARGET_HOME -> "only the Home screen"
            TARGET_LOCK -> "only the Lock screen"
            else -> if (rule.sameOnBoth) "both Home and Lock screen with the same picture"
            else "both Home and Lock screen, each from its own pictures"
        }
        val from = when (rule.source) {
            SOURCE_IMAGES -> "your selected images"
            SOURCE_FOLDER -> "your selected folder"
            else -> "your selected images and folder"
        }
        return when (trigger) {
            TRIGGER_TILE_IMAGES, TRIGGER_TILE_FOLDER -> {
                val tile = if (trigger == TRIGGER_TILE_IMAGES) "Wallpaper: Images" else "Wallpaper: Folder"
                val order = if (rule.order == ORDER_RANDOM) "a random one" else "the next one in order"
                "Tapping the \"$tile\" tile changes $screens, picking $order from $from."
            }
            else -> "Next / Previous / Random Wallpaper gestures change $screens, using $from. " +
                "Gestures named \"Home\" or \"Lock Screen\" always change just that screen."
        }
    }
}
