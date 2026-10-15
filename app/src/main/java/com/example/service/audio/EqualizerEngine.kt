package com.example.service.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class SessionInfo(
    val sessionId: Int,
    val packageName: String?,
    val label: String,
    val attached: Boolean,
    /** Preset this app is really playing with, e.g. "Rock" or "Rock (rule: Spotify)". */
    val profile: String = ""
)

data class EngineStatus(
    /** The global output-mix (session 0) effect is attached and working. */
    val globalActive: Boolean = false,
    /** The device refused to create effects on session 0. */
    val globalFailed: Boolean = false,
    /** Apps that announced their audio session and have effects attached. */
    val apps: List<SessionInfo> = emptyList(),
    /** App rules exist, so every app gets its own preset and the system-wide output is paused. */
    val perAppMode: Boolean = false,
    /** Preset used for sound that is not covered by an app rule, and where it came from. */
    val defaultProfile: String = "",
    /** True if [defaultProfile] comes from a device rule instead of the user's default settings. */
    val defaultFromRule: Boolean = false
)

/**
 * Owns every android.media.audiofx object. All public methods are synchronized and safe to call
 * from any thread (effect creation is a binder call, so keep it off the main thread).
 */
object EqualizerEngine {
    private const val TAG = "EqualizerEngine"
    private const val PRIORITY = 1000
    private const val GLOBAL_SESSION = 0

    private class Chain(val sessionId: Int) {
        var eq: Equalizer? = null
        var bass: BassBoost? = null
        var virt: Virtualizer? = null
        var loud: LoudnessEnhancer? = null

        val alive: Boolean get() = eq != null || bass != null || virt != null || loud != null

        fun release() {
            try { eq?.release() } catch (_: Throwable) {}
            try { bass?.release() } catch (_: Throwable) {}
            try { virt?.release() } catch (_: Throwable) {}
            try { loud?.release() } catch (_: Throwable) {}
            eq = null; bass = null; virt = null; loud = null
        }
    }

    private val chains = HashMap<Int, Chain>()
    private val knownSessions = LinkedHashMap<Int, String?>()
    private val failed = HashSet<Int>()
    private val labelCache = HashMap<String, String>()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** Read what the device supports. Creates and immediately releases temporary effects. */
    @Synchronized
    fun queryCaps(): DeviceCaps? {
        var eq: Equalizer? = null
        return try {
            eq = Equalizer(0, 0)
            val n = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val hz = List(n) { eq.getCenterFreq(it.toShort()) / 1000 }
            DeviceCaps(
                bands = n,
                minMb = range[0].toInt(),
                maxMb = range[1].toInt(),
                centerHz = hz,
                bassSupported = supported { BassBoost(0, 0).also { b -> val ok = b.strengthSupported; b.release(); if (!ok) throw IllegalStateException() } },
                virtualizerSupported = supported { Virtualizer(0, 0).also { v -> val ok = v.strengthSupported; v.release(); if (!ok) throw IllegalStateException() } },
                loudnessSupported = supported { LoudnessEnhancer(0).also { it.release() } },
                fromDevice = true
            )
        } catch (t: Throwable) {
            Log.w(TAG, "queryCaps failed: ${t.message}")
            null
        } finally {
            try { eq?.release() } catch (_: Throwable) {}
        }
    }

    private inline fun supported(block: () -> Unit): Boolean =
        try { block(); true } catch (_: Throwable) { false }

    @Synchronized
    fun sessionOpened(sessionId: Int, packageName: String?) {
        if (sessionId <= 0) return
        knownSessions[sessionId] = packageName
        failed.remove(sessionId)
    }

    @Synchronized
    fun sessionClosed(sessionId: Int) {
        knownSessions.remove(sessionId)
        failed.remove(sessionId)
        chains.remove(sessionId)?.release()
    }

    /** Forget earlier failures so the next sync tries to attach again. */
    @Synchronized
    fun retry() {
        failed.clear()
    }

    @Synchronized
    fun releaseAll() {
        chains.values.forEach { it.release() }
        chains.clear()
        failed.clear()
        _status.value = EngineStatus()
    }

    /** Make the real audio effects match [s] (and the profile rules for [route] / each app). */
    @Synchronized
    fun sync(ctx: Context, s: EqSettings, caps: DeviceCaps, route: AudioRoute? = null) {
        if (!s.enabled) {
            if (chains.isNotEmpty()) releaseAll() else _status.value = EngineStatus()
            return
        }

        val perApp = s.autoProfiles && s.rules.any { it.kind == RuleKind.APP }

        // 1. global output mix. Paused in per-app mode, otherwise an app would be processed twice
        //    (once by its own preset, once by the global one).
        if (s.globalSession && !perApp) {
            ensureChain(GLOBAL_SESSION)
        } else {
            chains.remove(GLOBAL_SESSION)?.release()
        }

        // 2. sessions that apps announced
        knownSessions.keys.forEach { ensureChain(it) }
        chains.keys.filter { it != GLOBAL_SESSION && !knownSessions.containsKey(it) }.forEach {
            chains.remove(it)?.release()
        }

        // 3. push parameters into every live chain, each with its own profile
        //    (the curve lives on a virtual 5/10-band layout and is mapped onto the real bands)
        val levelCache = HashMap<String, List<Float>>()
        fun deviceLevels(e: EqSettings): List<Float> =
            levelCache.getOrPut("${e.bandCount}|${e.presetName}|${e.bandDb.hashCode()}") {
                EqProfileIO.mapToDevice(
                    e.levelsFor(e.bandCount), EqLayout.hz(e.bandCount), caps.centerHz, caps.minDb, caps.maxDb
                )
            }

        chains.values.forEach { chain ->
            val pkg = if (chain.sessionId == GLOBAL_SESSION) null else knownSessions[chain.sessionId]
            val effective = EqRules.effective(s, EqRules.find(s, route, pkg))
            applyParams(chain, effective, caps, deviceLevels(effective))
        }

        // 4. publish status for the UI
        val apps = knownSessions.map { (sid, pkg) ->
            val rule = EqRules.find(s, route, pkg)
            SessionInfo(sid, pkg, labelFor(ctx, pkg), chains[sid]?.alive == true, profileLabel(s, rule))
        }
        val defaultRule = EqRules.find(s, route, null)
        _status.value = EngineStatus(
            globalActive = chains[GLOBAL_SESSION]?.alive == true,
            globalFailed = s.globalSession && !perApp && failed.contains(GLOBAL_SESSION),
            apps = apps,
            perAppMode = perApp,
            defaultProfile = profileLabel(s, defaultRule),
            defaultFromRule = defaultRule != null
        )
    }

    private fun profileLabel(s: EqSettings, rule: EqRule?): String =
        if (rule == null) s.presetName else "${presetDisplayName(rule.preset)} (rule: ${rule.label})"

    private fun ensureChain(sessionId: Int) {
        if (chains[sessionId]?.alive == true) return
        if (failed.contains(sessionId)) return
        val c = Chain(sessionId)
        c.eq = create("Equalizer", sessionId) { Equalizer(PRIORITY, sessionId) }
        c.bass = create("BassBoost", sessionId) { BassBoost(PRIORITY, sessionId) }
        c.virt = create("Virtualizer", sessionId) { Virtualizer(PRIORITY, sessionId) }
        c.loud = create("LoudnessEnhancer", sessionId) { LoudnessEnhancer(sessionId) }
        if (c.alive) {
            chains[sessionId] = c
        } else {
            failed.add(sessionId)
            c.release()
        }
    }

    private inline fun <T> create(name: String, sessionId: Int, block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$name unavailable for session $sessionId: ${t.message}")
            null
        }

    private fun applyParams(c: Chain, s: EqSettings, caps: DeviceCaps, levels: List<Float>) {
        c.eq?.let { eq ->
            try {
                val n = minOf(eq.numberOfBands.toInt(), levels.size)
                val range = eq.bandLevelRange
                var allFlat = true
                for (i in 0 until n) {
                    val mb = ((levels[i] + s.preampDb) * 100f).roundToInt().coerceIn(range[0].toInt(), range[1].toInt())
                    if (mb != 0) allFlat = false
                    eq.setBandLevel(i.toShort(), mb.toShort())
                }
                // Flat curve = bypass, saves CPU/battery.
                eq.setEnabled(!allFlat)
            } catch (t: Throwable) {
                Log.w(TAG, "Equalizer apply failed: ${t.message}")
            }
        }
        c.bass?.let { b ->
            try {
                if (caps.bassSupported && b.strengthSupported) {
                    b.setStrength(s.bassStrength.coerceIn(0, 1000).toShort())
                    b.setEnabled(s.bassStrength > 0)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "BassBoost apply failed: ${t.message}")
            }
        }
        c.virt?.let { v ->
            try {
                if (caps.virtualizerSupported && v.strengthSupported) {
                    v.setStrength(s.virtualizerStrength.coerceIn(0, 1000).toShort())
                    v.setEnabled(s.virtualizerStrength > 0)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Virtualizer apply failed: ${t.message}")
            }
        }
        c.loud?.let { l ->
            try {
                l.setTargetGain(s.loudnessMb.coerceIn(0, 1200))
                l.setEnabled(s.loudnessMb > 0)
            } catch (t: Throwable) {
                Log.w(TAG, "LoudnessEnhancer apply failed: ${t.message}")
            }
        }
    }

    private fun labelFor(ctx: Context, pkg: String?): String {
        if (pkg.isNullOrEmpty()) return "Unknown app"
        return labelCache.getOrPut(pkg) {
            try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Throwable) {
                pkg
            }
        }
    }
}
