package com.example.service.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single source of truth for the equalizer. The UI, the foreground service, the broadcast
 * receivers and the Quick Settings tile all talk to this object (they live in one process).
 */
object EqualizerController {
    private const val TAG = "EqualizerController"

    private lateinit var app: Context
    private val inited = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(EqSettings())
    val state: StateFlow<EqSettings> = _state.asStateFlow()

    private val _caps = MutableStateFlow(DeviceCaps())
    val caps: StateFlow<DeviceCaps> = _caps.asStateFlow()

    /** The output device media is playing on right now (null until the first check). */
    private val _route = MutableStateFlow<AudioRoute?>(null)
    val route: StateFlow<AudioRoute?> = _route.asStateFlow()

    /** External output devices seen before (for creating rules while they are disconnected). */
    private val _known = MutableStateFlow<List<AudioRoute>>(emptyList())
    val knownDevices: StateFlow<List<AudioRoute>> = _known.asStateFlow()

    fun init(context: Context) {
        if (!inited.compareAndSet(false, true)) return
        app = context.applicationContext
        _state.value = EqStore.load(app)
        EqStore.loadCaps(app)?.let { _caps.value = it }

        // Whenever settings or device caps change: push to the real audio effects + save.
        _known.value = EqStore.loadKnown(app)
        scope.launch {
            combine(_state, _caps, _route) { s, c, r -> Triple(s, c, r) }
                .conflate()
                .collect { (s, c, r) ->
                    try {
                        EqualizerEngine.sync(app, s, c, r)
                    } catch (t: Throwable) {
                        Log.w(TAG, "sync failed: ${t.message}")
                    }
                    EqStore.save(app, s)
                }
        }

        // Follow the output device (speaker / wired / Bluetooth ...) so device rules switch automatically.
        AudioRouteMonitor.start(app) { current, connected ->
            _route.value = current
            rememberDevices(connected)
        }

        // Read the real band layout once, in the background.
        scope.launch {
            val real = EqualizerEngine.queryCaps()
            if (real != null) {
                EqStore.saveCaps(app, real)
                _caps.value = real
            }
        }
    }

    // ---------------------------------------------------------------- master switch

    fun setEnabled(context: Context, enabled: Boolean) {
        init(context)
        if (enabled) EqualizerEngine.retry()
        _state.update { it.copy(enabled = enabled) }
        if (enabled) ensureService(context) else stopService(context)
    }

    fun toggle(context: Context) = setEnabled(context, !_state.value.enabled)

    /** Start the foreground service if the equalizer is on (call from a foreground context). */
    fun ensureService(context: Context) {
        init(context)
        if (!_state.value.enabled) return
        try {
            ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, EqualizerService::class.java))
        } catch (t: Throwable) {
            // Background-start restrictions: effects still work while the process is alive.
            Log.w(TAG, "Could not start service: ${t.message}")
        }
    }

    private fun stopService(context: Context) {
        try {
            context.applicationContext.stopService(Intent(context.applicationContext, EqualizerService::class.java))
        } catch (_: Throwable) {}
    }

    /** Try to attach again (e.g. after another app grabbed the effect). */
    fun refresh() {
        EqualizerEngine.retry()
        // StateFlow ignores equal values, so sync directly instead of re-emitting the state.
        scope.launch {
            try {
                EqualizerEngine.sync(app, _state.value, _caps.value, _route.value)
            } catch (t: Throwable) {
                Log.w(TAG, "refresh failed: ${t.message}")
            }
        }
    }

    // ---------------------------------------------------------------- bands & presets

    fun setBand(index: Int, db: Float) {
        val c = _caps.value
        _state.update { s ->
            val levels = s.levelsFor(s.bandCount).toMutableList()
            if (index !in levels.indices) return@update s
            levels[index] = db.coerceIn(c.minDb, c.maxDb)
            s.copy(bandDb = levels, presetName = "Custom")
        }
    }

    /** Switch between the 5-band and 10-band layout, keeping the current sound as close as possible. */
    fun setBandCount(count: Int) {
        val target = if (count == 10) 10 else 5
        val c = _caps.value
        _state.update { s ->
            if (s.bandCount == target) return@update s
            val toHz = EqLayout.hz(target)
            val isPreset = EqPresets.builtIn.containsKey(s.presetName) || s.customPresets.containsKey(s.presetName)
            val levels = if (isPreset) {
                // a named preset can be rebuilt exactly for the new layout
                EqPresets.levels(s.presetName, toHz, s.customPresets)
            } else {
                EqProfileIO.refit(s.levelsFor(s.bandCount), EqLayout.hz(s.bandCount), toHz, c.minDb, c.maxDb)
            }
            s.copy(bandCount = target, bandDb = levels.map { it.coerceIn(c.minDb, c.maxDb) })
        }
    }

    fun applyPreset(name: String) {
        val c = _caps.value
        _state.update { s ->
            val levels = EqPresets.levels(name, EqLayout.hz(s.bandCount), s.customPresets)
                .map { it.coerceIn(c.minDb, c.maxDb) }
            s.copy(presetName = name, bandDb = levels)
        }
    }

    fun saveCustomPreset(rawName: String): Boolean {
        val name = rawName.trim().take(20)
        if (name.isEmpty() || name == "Custom" || EqPresets.builtIn.containsKey(name)) return false
        val c = _caps.value
        _state.update { s ->
            s.copy(
                customPresets = s.customPresets + (name to s.levelsFor(s.bandCount)),
                presetName = name
            )
        }
        return true
    }

    fun deleteCustomPreset(name: String) {
        _state.update { s ->
            s.copy(
                customPresets = s.customPresets - name,
                presetName = if (s.presetName == name) "Custom" else s.presetName
            )
        }
    }

    // ---------------------------------------------------------------- extras

    fun setBass(strength: Int) = _state.update { it.copy(bassStrength = strength.coerceIn(0, 1000)) }
    fun setVirtualizer(strength: Int) = _state.update { it.copy(virtualizerStrength = strength.coerceIn(0, 1000)) }
    fun setLoudness(mb: Int) = _state.update { it.copy(loudnessMb = mb.coerceIn(0, 1200)) }

    fun setPreamp(db: Float) = _state.update { it.copy(preampDb = (Math.round(db * 10f) / 10f).coerceIn(-15f, 15f)) }

    /** Set the preamp to exactly cancel the loudest boost, so the curve cannot clip. */
    fun autoPreamp() {
        val c = _caps.value
        _state.update { s ->
            // peak of what the hardware will really play, not just of the sliders
            val mapped = EqProfileIO.mapToDevice(
                s.levelsFor(s.bandCount), EqLayout.hz(s.bandCount), c.centerHz, c.minDb, c.maxDb
            )
            val peak = maxOf(mapped.maxOrNull() ?: 0f, s.levelsFor(s.bandCount).maxOrNull() ?: 0f)
            s.copy(preampDb = (-Math.round(peak.coerceAtLeast(0f) * 10f) / 10f).coerceIn(-15f, 0f))
        }
    }

    fun setGlobalSession(on: Boolean) {
        EqualizerEngine.retry()
        _state.update { it.copy(globalSession = on) }
    }

    fun resetAll() {
        _state.update {
            it.copy(presetName = "Flat", bandDb = emptyList(), preampDb = 0f, bassStrength = 0, virtualizerStrength = 0, loudnessMb = 0)
        }
    }

    // ---------------------------------------------------------------- profiles: per app / per device

    fun setAutoProfiles(on: Boolean) = _state.update { it.copy(autoProfiles = on) }

    /** Add a rule or change the preset of an existing one. */
    fun upsertRule(rule: EqRule) = _state.update { it.copy(rules = EqRules.upsert(it.rules, rule)) }

    fun removeRule(rule: EqRule) = _state.update { s ->
        s.copy(rules = s.rules.filterNot { it.kind == rule.kind && it.key == rule.key })
    }

    /** Re-check the output device, e.g. after the Bluetooth permission was granted. */
    fun refreshRoute() = AudioRouteMonitor.refresh()

    private fun rememberDevices(connected: List<AudioRoute>) {
        val external = connected.filter { it.category != RouteCategory.PHONE_SPEAKER }
        if (external.isEmpty()) return
        val current = _known.value
        val merged = LinkedHashMap<String, AudioRoute>()
        current.forEach { merged[it.key] = it }
        external.forEach { merged[it.key] = it } // newer info (name, speaker/headset) wins
        val list = merged.values.toList().takeLast(30)
        if (list != current) {
            _known.value = list
            EqStore.saveKnown(app, list)
        }
    }

    // ---------------------------------------------------------------- export / import (.txt)

    data class ImportOutcome(val ok: Boolean, val message: String)

    /** The current curve as "Preamp: ..." + "Filter n: ON PK Fc .. Hz Gain .. dB Q .." lines. */
    fun exportText(): String {
        val c = _caps.value
        val s = _state.value
        return EqProfileIO.export(s.levelsFor(s.bandCount), EqLayout.hz(s.bandCount), s.preampDb)
    }

    /** Read a parametric EQ text file and apply it to this phone's bands. */
    fun importText(text: String): ImportOutcome {
        val profile = EqProfileIO.parse(text)
            ?: return ImportOutcome(false, "No EQ filters found in this file.")
        val c = _caps.value
        val bandCount = _state.value.bandCount
        val layout = EqLayout.hz(bandCount)
        val fit = EqProfileIO.fitToBands(profile, layout, c.minDb, c.maxDb)
        val preamp = profile.preampDb.toFloat().coerceIn(-15f, 15f)
        _state.update { it.copy(bandDb = fit.bandDb, preampDb = preamp, presetName = "Custom") }

        val count = if (profile.filters.isNotEmpty()) "${profile.filters.size} filters" else "graphic curve"
        val sb = StringBuilder("Imported $count")
        if (profile.filters.size != layout.size) sb.append(", fitted to $bandCount bands")
        if (fit.maxErrorDb > 1.5f) {
            sb.append(" (approximate")
            if (bandCount == 5 && profile.filters.size > 6) sb.append(" – switch to 10 bands for a closer match")
            sb.append(")")
        }
        if (profile.skipped > 0) sb.append(". ${profile.skipped} unsupported line(s) skipped")
        return ImportOutcome(true, sb.toString())
    }

    // ---------------------------------------------------------------- audio sessions from apps

    /** Called by the broadcast receivers. Runs [onDone] when the work is finished. */
    fun onSessionEvent(context: Context, opened: Boolean, sessionId: Int, packageName: String?, onDone: () -> Unit = {}) {
        init(context)
        if (sessionId <= 0 || packageName == context.packageName) {
            onDone(); return
        }
        scope.launch {
            try {
                if (opened) EqualizerEngine.sessionOpened(sessionId, packageName) else EqualizerEngine.sessionClosed(sessionId)
                // Re-run the sync with current settings so the new session gets its effects now.
                EqualizerEngine.sync(app, _state.value, _caps.value, _route.value)
            } catch (t: Throwable) {
                Log.w(TAG, "session event failed: ${t.message}")
            } finally {
                onDone()
            }
        }
        if (opened && _state.value.enabled) ensureService(context)
    }
}
