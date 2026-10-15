package com.example.service.audio

/** What kind of output the sound is going to. */
enum class RouteCategory(val key: String, val title: String) {
    PHONE_SPEAKER("speaker", "Phone speaker"),
    WIRED("wired", "Wired headset / headphones"),
    BT_SPEAKER("bt_speaker", "Bluetooth speaker"),
    BT_HEADSET("bt_headset", "Bluetooth headphones / neckband / earbuds"),
    BT_ANY("bt_any", "Any Bluetooth device"),
    USB("usb", "USB audio"),
    OTHER("other", "Other output (HDMI, dock …)");

    companion object {
        fun fromKey(key: String): RouteCategory? = values().firstOrNull { it.key == key }

        /** The categories a user can pick when adding a "device type" rule. */
        val selectable: List<RouteCategory> = listOf(PHONE_SPEAKER, WIRED, BT_SPEAKER, BT_HEADSET, BT_ANY, USB)
    }
}

/** The output device the media is currently routed to (or one we have seen before). */
data class AudioRoute(val key: String, val label: String, val category: RouteCategory) {
    /** Category rules that apply to this device, most specific first. */
    fun matchKeys(): List<String> = when (category) {
        RouteCategory.BT_SPEAKER -> listOf("bt_speaker", "bt_any")
        RouteCategory.BT_HEADSET -> listOf("bt_headset", "bt_any")
        else -> listOf(category.key)
    }
}

enum class RuleKind(val title: String) { APP("App"), DEVICE("Device"), CATEGORY("Type") }

/** "When [kind]/[key] is active, use preset [preset]". */
data class EqRule(val kind: RuleKind, val key: String, val label: String, val preset: String)

/** Rule preset meaning "no equalizer effects at all". */
const val PRESET_BYPASS = "__bypass__"

fun presetDisplayName(preset: String): String = if (preset == PRESET_BYPASS) "Off (bypass)" else preset

object EqRules {
    /**
     * Pick the rule that applies. Priority: the app playing the sound > the exact device >
     * the device type > nothing (the user's default settings).
     */
    fun find(s: EqSettings, route: AudioRoute?, pkg: String?): EqRule? {
        if (!s.autoProfiles || s.rules.isEmpty()) return null
        if (pkg != null) {
            s.rules.firstOrNull { it.kind == RuleKind.APP && it.key == pkg }?.let { return it }
        }
        if (route != null) {
            s.rules.firstOrNull { it.kind == RuleKind.DEVICE && it.key == route.key }?.let { return it }
            for (k in route.matchKeys()) {
                s.rules.firstOrNull { it.kind == RuleKind.CATEGORY && it.key == k }?.let { return it }
            }
        }
        return null
    }

    /**
     * The settings that are really played when [rule] applies. Only the curve comes from the
     * rule's preset; preamp, bass, surround and loudness stay as in the user's settings.
     */
    fun effective(s: EqSettings, rule: EqRule?): EqSettings {
        if (rule == null) return s
        if (rule.preset == PRESET_BYPASS) {
            return s.copy(
                presetName = presetDisplayName(PRESET_BYPASS), bandDb = emptyList(),
                preampDb = 0f, bassStrength = 0, virtualizerStrength = 0, loudnessMb = 0
            )
        }
        val exists = EqPresets.builtIn.containsKey(rule.preset) || s.customPresets.containsKey(rule.preset)
        if (!exists) return s // preset was deleted: fall back to the default instead of going flat
        return s.copy(
            presetName = rule.preset,
            bandDb = EqPresets.levels(rule.preset, EqLayout.hz(s.bandCount), s.customPresets)
        )
    }

    /** Add a rule, replacing an existing one for the same app/device/type. */
    fun upsert(rules: List<EqRule>, rule: EqRule): List<EqRule> =
        rules.filterNot { it.kind == rule.kind && it.key == rule.key } + rule
}
