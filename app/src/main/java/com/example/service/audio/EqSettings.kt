package com.example.service.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/** What the device's audio effect engine supports (read once from a throw-away effect). */
data class DeviceCaps(
    val bands: Int = 5,
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val centerHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val bassSupported: Boolean = true,
    val virtualizerSupported: Boolean = true,
    val loudnessSupported: Boolean = true,
    /** false = these are fallback values, the real query has not succeeded (yet). */
    val fromDevice: Boolean = false
) {
    val minDb: Float get() = minMb / 100f
    val maxDb: Float get() = maxMb / 100f
}

/** Everything the user can change. Persisted as JSON in SharedPreferences. */
data class EqSettings(
    val enabled: Boolean = false,
    val presetName: String = "Flat",
    /** Which equalizer layout the user sees: 5 or 10 bands (virtual, mapped onto the hardware bands). */
    val bandCount: Int = 5,
    /** One dB value per band of the chosen layout. Empty = flat. */
    val bandDb: List<Float> = emptyList(),
    /** 0..1000 */
    val bassStrength: Int = 0,
    /** 0..1000 */
    val virtualizerStrength: Int = 0,
    /** Loudness enhancer target gain in millibels, 0..1200 */
    val loudnessMb: Int = 0,
    /** Overall gain in dB added to every band (negative = headroom against clipping). -15..+15 */
    val preampDb: Float = 0f,
    /** Also attach to the global output mix (audio session 0). */
    val globalSession: Boolean = true,
    val customPresets: Map<String, List<Float>> = emptyMap(),
    /** Use per-app / per-device presets (see [EqRules]). */
    val autoProfiles: Boolean = true,
    val rules: List<EqRule> = emptyList()
) {
    fun levelsFor(bands: Int): List<Float> = EqPresets.resample(bandDb, bands)

    fun toJson(): String {
        val o = JSONObject()
        o.put("enabled", enabled)
        o.put("presetName", presetName)
        o.put("bandCount", bandCount)
        o.put("bandDb", JSONArray(bandDb.map { it.toDouble() }))
        o.put("bass", bassStrength)
        o.put("virt", virtualizerStrength)
        o.put("loud", loudnessMb)
        o.put("preamp", preampDb.toDouble())
        o.put("global", globalSession)
        val c = JSONObject()
        customPresets.forEach { (k, v) -> c.put(k, JSONArray(v.map { it.toDouble() })) }
        o.put("custom", c)
        o.put("autoProfiles", autoProfiles)
        val r = JSONArray()
        rules.forEach { rule ->
            r.put(JSONObject().put("kind", rule.kind.name).put("key", rule.key).put("label", rule.label).put("preset", rule.preset))
        }
        o.put("rules", r)
        return o.toString()
    }

    companion object {
        fun fromJson(json: String?): EqSettings {
            if (json.isNullOrBlank()) return EqSettings()
            return try {
                val o = JSONObject(json)
                val band = o.optJSONArray("bandDb")
                val bands = if (band != null) List(band.length()) { band.optDouble(it, 0.0).toFloat() } else emptyList()
                val custom = LinkedHashMap<String, List<Float>>()
                o.optJSONObject("custom")?.let { c ->
                    c.keys().forEach { key ->
                        val arr = c.optJSONArray(key) ?: return@forEach
                        custom[key] = List(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
                    }
                }
                val ruleList = ArrayList<EqRule>()
                o.optJSONArray("rules")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val ro = arr.optJSONObject(i) ?: continue
                        val kind = try { RuleKind.valueOf(ro.optString("kind")) } catch (_: Exception) { continue }
                        val key = ro.optString("key", "")
                        val preset = ro.optString("preset", "")
                        if (key.isEmpty() || preset.isEmpty()) continue
                        ruleList += EqRule(kind, key, ro.optString("label", key), preset)
                    }
                }
                EqSettings(
                    enabled = o.optBoolean("enabled", false),
                    presetName = o.optString("presetName", "Flat"),
                    bandCount = if (o.optInt("bandCount", 5) == 10) 10 else 5,
                    bandDb = bands,
                    bassStrength = o.optInt("bass", 0).coerceIn(0, 1000),
                    virtualizerStrength = o.optInt("virt", 0).coerceIn(0, 1000),
                    loudnessMb = o.optInt("loud", 0).coerceIn(0, 1200),
                    preampDb = o.optDouble("preamp", 0.0).toFloat().coerceIn(-15f, 15f),
                    globalSession = o.optBoolean("global", true),
                    customPresets = custom,
                    autoProfiles = o.optBoolean("autoProfiles", true),
                    rules = ruleList
                )
            } catch (_: Exception) {
                EqSettings()
            }
        }
    }
}

object EqPresets {
    /** Reference frequencies the built-in presets are defined at (Hz). */
    private val REF_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    /** dB values at REF_HZ. They are interpolated onto however many bands the device has. */
    val builtIn: LinkedHashMap<String, FloatArray> = linkedMapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to floatArrayOf(6f, 4f, 1f, 0f, 0f),
        "Treble Boost" to floatArrayOf(0f, 0f, 0f, 3f, 6f),
        "Loudness" to floatArrayOf(5f, 3f, 0f, 3f, 5f),
        "Vocal" to floatArrayOf(-3f, 0f, 3f, 3f, 1f),
        "Rock" to floatArrayOf(4f, 2f, -2f, 2f, 4f),
        "Pop" to floatArrayOf(-1f, 2f, 4f, 2f, -1f),
        "Jazz" to floatArrayOf(3f, 1f, -1f, 2f, 4f),
        "Classical" to floatArrayOf(3f, 2f, -1f, 2f, 3f),
        "Electronic" to floatArrayOf(5f, 3f, 0f, 2f, 4f),
        "Hip-Hop" to floatArrayOf(5f, 4f, 0f, 1f, 2f)
    )

    fun levels(name: String, centerHz: List<Int>, custom: Map<String, List<Float>>): List<Float> {
        custom[name]?.let { src ->
            if (src.size == centerHz.size) return src
            val from = EqLayout.hzOrNull(src.size)
            // a preset saved in the other layout is converted so it keeps its sound
            return if (from != null) EqProfileIO.refit(src, from, centerHz, -15f, 15f) else resample(src, centerHz.size)
        }
        val ref = builtIn[name] ?: return List(centerHz.size) { 0f }
        return centerHz.map { f -> interpolateLog(ref, f.toFloat()) }
    }

    private fun interpolateLog(values: FloatArray, hz: Float): Float {
        if (hz <= REF_HZ.first()) return values.first()
        if (hz >= REF_HZ.last()) return values.last()
        for (i in 0 until REF_HZ.size - 1) {
            if (hz <= REF_HZ[i + 1]) {
                val t = (ln(hz) - ln(REF_HZ[i])) / (ln(REF_HZ[i + 1]) - ln(REF_HZ[i]))
                return values[i] + (values[i + 1] - values[i]) * t
            }
        }
        return values.last()
    }

    /** Stretch/shrink a list of band levels to [n] bands (linear interpolation by position). */
    fun resample(src: List<Float>, n: Int): List<Float> {
        if (n <= 0) return emptyList()
        if (src.isEmpty()) return List(n) { 0f }
        if (src.size == n) return src
        if (src.size == 1) return List(n) { src[0] }
        return List(n) { i ->
            val pos = i.toFloat() * (src.size - 1) / (n - 1).coerceAtLeast(1)
            val lo = pos.toInt().coerceIn(0, src.size - 1)
            val hi = (lo + 1).coerceAtMost(src.size - 1)
            src[lo] + (src[hi] - src[lo]) * (pos - lo)
        }
    }
}

object EqStore {
    private const val PREFS = "equalizer_prefs"
    private const val KEY_STATE = "state"
    private const val KEY_CAPS = "caps"

    fun load(ctx: Context): EqSettings =
        EqSettings.fromJson(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null))

    fun save(ctx: Context, s: EqSettings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATE, s.toJson()).apply()
    }

    private const val KEY_KNOWN = "known_devices"

    /** Output devices seen before, so rules can be created for them while they are disconnected. */
    fun loadKnown(ctx: Context): List<AudioRoute> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_KNOWN, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<AudioRoute>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cat = RouteCategory.fromKey(o.optString("cat", "")) ?: continue
                val key = o.optString("key", "")
                if (key.isNotEmpty()) out += AudioRoute(key, o.optString("label", key), cat)
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveKnown(ctx: Context, list: List<AudioRoute>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("key", it.key).put("label", it.label).put("cat", it.category.key)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_KNOWN, arr.toString()).apply()
    }

    fun loadCaps(ctx: Context): DeviceCaps? {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CAPS, null) ?: return null
        return try {
            val o = JSONObject(json)
            val arr = o.getJSONArray("hz")
            DeviceCaps(
                bands = o.getInt("bands"),
                minMb = o.getInt("min"),
                maxMb = o.getInt("max"),
                centerHz = List(arr.length()) { arr.getInt(it) },
                bassSupported = o.optBoolean("bass", true),
                virtualizerSupported = o.optBoolean("virt", true),
                loudnessSupported = o.optBoolean("loud", true),
                fromDevice = true
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveCaps(ctx: Context, c: DeviceCaps) {
        val o = JSONObject()
        o.put("bands", c.bands)
        o.put("min", c.minMb)
        o.put("max", c.maxMb)
        o.put("hz", JSONArray(c.centerHz))
        o.put("bass", c.bassSupported)
        o.put("virt", c.virtualizerSupported)
        o.put("loud", c.loudnessSupported)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CAPS, o.toString()).apply()
    }
}
