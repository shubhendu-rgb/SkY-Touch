package com.example.service.audio

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/** One line of a parametric EQ file, e.g. `Filter 3: ON PK Fc 94.7 Hz Gain -1.8 dB Q 0.67`. */
data class ParametricFilter(val type: String, val fc: Double, val gainDb: Double, val q: Double)

data class EqProfile(
    val preampDb: Double,
    val filters: List<ParametricFilter>,
    /** Optional "GraphicEQ: 25 -1; 40 -2; ..." points (Hz, dB). */
    val graphic: List<Pair<Double, Double>>,
    /** Filters we could read but not use (unknown type, missing Fc ...). */
    val skipped: Int
)

data class FitResult(val bandDb: List<Float>, val maxErrorDb: Float, val rmsErrorDb: Float)

/** The band layouts the user can choose in the UI. They are virtual: the engine maps them onto the real hardware bands. */
object EqLayout {
    val FIVE: List<Int> = listOf(60, 230, 910, 3600, 14000)
    val TEN: List<Int> = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun hz(bandCount: Int): List<Int> = if (bandCount == 10) TEN else FIVE

    /** Layout for a stored curve with [size] values, or null if it is neither. */
    fun hzOrNull(size: Int): List<Int>? = when (size) {
        5 -> FIVE
        10 -> TEN
        else -> null
    }
}

/**
 * Reads and writes the common plain-text parametric EQ format used by Equalizer APO, AutoEq,
 * Wavelet, Peace, Poweramp and others:
 *
 *   Preamp: -3.84 dB
 *   Filter 1: ON LSC Fc 105.0 Hz Gain -5.9 dB Q 0.70
 *   Filter 2: ON PK Fc 51.1 Hz Gain -0.7 dB Q 1.84
 *
 * No Android classes are used here so the logic can be unit-tested on the JVM.
 */
object EqProfileIO {
    private const val FS = 48000.0
    /** Smoothness used only when a curve is spread onto MORE bands (5 -> 10), to avoid ringing. */
    const val UPSCALE_SMOOTH = 0.02
    private const val NUM = """[-+]?\d+(?:[.,]\d+)?"""

    private val PREAMP_RE = Regex("""^Preamp\s*:\s*($NUM)""", RegexOption.IGNORE_CASE)
    private val FILTER_RE = Regex("""^Filter\s*\d*\s*:\s*(ON|OFF)\s+(\S+)(.*)$""", RegexOption.IGNORE_CASE)
    private val FC_RE = Regex("""\bFc\s+($NUM)""", RegexOption.IGNORE_CASE)
    private val GAIN_RE = Regex("""\bGain\s+($NUM)""", RegexOption.IGNORE_CASE)
    private val Q_RE = Regex("""\bQ\s+($NUM)""", RegexOption.IGNORE_CASE)
    private val BW_RE = Regex("""\bBW\s+Oct\s+($NUM)""", RegexOption.IGNORE_CASE)
    private val GRAPHIC_RE = Regex("""^GraphicEQ\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)

    // ------------------------------------------------------------------------------ export

    /** [levels] are the per-band gains in dB, [centerHz] the band centre frequencies. */
    fun export(levels: List<Float>, centerHz: List<Int>, preampDb: Float): String {
        val n = minOf(levels.size, centerHz.size)
        val hz = centerHz.take(n).map { it.toDouble().coerceAtLeast(1.0) }
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "Preamp: %.2f dB\n", clean(preampDb.toDouble())))
        for (i in 0 until n) {
            val type = bandType(i, n)
            val q = if (type == "PK") bandQ(hz, i) else 0.70
            sb.append(
                String.format(
                    Locale.US, "Filter %d: ON %s Fc %.1f Hz Gain %.1f dB Q %.2f\n",
                    i + 1, type, hz[i], clean(levels[i].toDouble()), q
                )
            )
        }
        return sb.toString()
    }

    /** Avoid printing "-0.0". */
    private fun clean(v: Double): Double = if (abs(v) < 0.05) 0.0 else v

    /** Lowest band = low shelf, highest = high shelf, the rest = peaking filters. */
    private fun bandType(i: Int, n: Int): String = when {
        n >= 3 && i == 0 -> "LSC"
        n >= 3 && i == n - 1 -> "HSC"
        else -> "PK"
    }

    /** Q that makes neighbouring peaking bands touch (derived from the octave spacing). */
    private fun bandQ(hz: List<Double>, i: Int): Double {
        val n = hz.size
        val f = hz[i]
        var lo = if (i > 0) sqrt(hz[i - 1] * f) else Double.NaN
        var up = if (i < n - 1) sqrt(f * hz[i + 1]) else Double.NaN
        if (lo.isNaN() && up.isNaN()) {
            lo = f / sqrt(2.0); up = f * sqrt(2.0)
        } else if (lo.isNaN()) {
            lo = f * f / up
        } else if (up.isNaN()) {
            up = f * f / lo
        }
        val bwOct = ln(up / lo) / ln(2.0)
        return (1.0 / (2.0 * sinh(bwOct * ln(2.0) / 2.0))).coerceIn(0.3, 10.0)
    }

    // ------------------------------------------------------------------------------ parse

    /** Returns null when the text contains no usable filters at all. */
    fun parse(text: String): EqProfile? {
        var preamp = 0.0
        val filters = ArrayList<ParametricFilter>()
        var graphic: List<Pair<Double, Double>> = emptyList()
        var skipped = 0

        text.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF').trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith("*")) return@forEach

            PREAMP_RE.find(line)?.let { m ->
                preamp = num(m.groupValues[1]) ?: 0.0
                return@forEach
            }

            GRAPHIC_RE.find(line)?.let { m ->
                graphic = m.groupValues[1].split(';').mapNotNull { part ->
                    val t = part.trim().split(Regex("""\s+"""))
                    val f = t.getOrNull(0)?.let { num(it) }
                    val g = t.getOrNull(1)?.let { num(it) }
                    if (f != null && g != null && f > 0) f to g else null
                }.sortedBy { it.first }
                return@forEach
            }

            val fm = FILTER_RE.find(line) ?: return@forEach
            if (fm.groupValues[1].equals("OFF", ignoreCase = true)) return@forEach

            val type = normalizeType(fm.groupValues[2])
            val rest = fm.groupValues[3]
            val fc = FC_RE.find(rest)?.let { num(it.groupValues[1]) }
            if (type == null || fc == null || fc <= 0.0) {
                skipped++
                return@forEach
            }
            val gain = GAIN_RE.find(rest)?.let { num(it.groupValues[1]) } ?: 0.0
            val q = Q_RE.find(rest)?.let { num(it.groupValues[1]) }
                ?: BW_RE.find(rest)?.let { num(it.groupValues[1]) }?.let { bw ->
                    if (bw > 0) 1.0 / (2.0 * sinh(bw * ln(2.0) / 2.0)) else null
                }
                ?: if (type == "PK") 1.0 else 0.707
            filters += ParametricFilter(type, fc.coerceIn(10.0, 23000.0), gain.coerceIn(-40.0, 40.0), q.coerceIn(0.1, 100.0))
        }

        if (filters.isEmpty() && graphic.isEmpty()) return null
        return EqProfile(preamp.coerceIn(-60.0, 60.0), filters, graphic, skipped)
    }

    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    private fun normalizeType(t: String): String? = when (t.uppercase(Locale.US)) {
        "PK", "PEQ", "PEAK", "PEAKING" -> "PK"
        "LS", "LSC", "LSQ", "LOWSHELF" -> "LS"
        "HS", "HSC", "HSQ", "HIGHSHELF" -> "HS"
        "LP", "LPQ" -> "LP"
        "HP", "HPQ" -> "HP"
        "NO", "NOTCH" -> "NO"
        else -> null
    }

    // ------------------------------------------------------------------------------ fit to device bands

    /**
     * The phone's equalizer has a fixed set of bands (often 5), the file can have any number of
     * filters. We compute the file's real frequency response and find the band gains that
     * reproduce it as closely as possible (least squares on a log-frequency grid).
     */
    fun fitToBands(profile: EqProfile, centerHz: List<Int>, minDb: Float, maxDb: Float, smooth: Double = 0.0): FitResult {
        val n = centerHz.size
        if (n == 0) return FitResult(emptyList(), 0f, 0f)
        val hz = centerHz.map { it.toDouble().coerceAtLeast(1.0) }

        val m = 200
        val grid = DoubleArray(m) { 20.0 * (20000.0 / 20.0).pow(it / (m - 1.0)) }

        // target response (dB) of the file on the grid
        val target = DoubleArray(m) { k ->
            var db = 0.0
            for (f in profile.filters) db += responseDb(f.type, f.fc, f.gainDb, f.q, grid[k])
            if (profile.graphic.isNotEmpty()) db += graphicAt(profile.graphic, grid[k])
            db.coerceIn(-60.0, 60.0)
        }

        // basis: response of band j for +1 dB (linearised from a 6 dB reference)
        val basis = Array(n) { j ->
            val type = bandType(j, n)
            val q = if (type == "PK") bandQ(hz, j) else 0.70
            val t = if (type == "LSC") "LS" else if (type == "HSC") "HS" else "PK"
            DoubleArray(m) { k -> responseDb(t, hz[j], 6.0, q, grid[k]) / 6.0 }
        }

        // normal equations (BtB + lambda I) g = Bt t
        val a = Array(n) { DoubleArray(n) }
        val b = DoubleArray(n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                var s = 0.0
                for (k in 0 until m) s += basis[i][k] * basis[j][k]
                a[i][j] = s
            }
            a[i][i] += 1e-3 * m
            // optional smoothness: penalise the second difference of neighbouring band gains,
            // so converting a smooth curve onto more (heavily overlapping) bands does not ring.
            if (smooth > 0.0 && n >= 3) {
                val w = smooth * m
                for (j in 0 until n) {
                    var dd = 0.0
                    for (r in 1 until n - 1) {
                        val di = (if (i == r - 1) 1.0 else 0.0) - (if (i == r) 2.0 else 0.0) + (if (i == r + 1) 1.0 else 0.0)
                        val dj = (if (j == r - 1) 1.0 else 0.0) - (if (j == r) 2.0 else 0.0) + (if (j == r + 1) 1.0 else 0.0)
                        dd += di * dj
                    }
                    a[i][j] += w * dd
                }
            }
            var s = 0.0
            for (k in 0 until m) s += basis[i][k] * target[k]
            b[i] = s
        }
        val g = solve(a, b)

        val gains = DoubleArray(n) { (Math.round(g[it] * 10.0) / 10.0).coerceIn(minDb.toDouble(), maxDb.toDouble()) }

        var maxErr = 0.0
        var sq = 0.0
        for (k in 0 until m) {
            var p = 0.0
            for (j in 0 until n) p += gains[j] * basis[j][k]
            val e = abs(p - target[k])
            if (e > maxErr) maxErr = e
            sq += e * e
        }
        return FitResult(gains.map { it.toFloat() }, maxErr.toFloat(), sqrt(sq / m).toFloat())
    }

    /** Express a set of band gains as the filters they stand for (same shapes the export writes). */
    fun levelsToProfile(levels: List<Float>, hz: List<Int>): EqProfile {
        val n = minOf(levels.size, hz.size)
        val h = hz.take(n).map { it.toDouble().coerceAtLeast(1.0) }
        val filters = (0 until n).map { i ->
            val t = when (bandType(i, n)) { "LSC" -> "LS"; "HSC" -> "HS"; else -> "PK" }
            ParametricFilter(t, h[i], levels[i].toDouble(), if (t == "PK") bandQ(h, i) else 0.70)
        }
        return EqProfile(0.0, filters, emptyList(), 0)
    }

    /** True when two band layouts are (nearly) the same frequencies, so no conversion is needed. */
    fun sameLayout(a: List<Int>, b: List<Int>): Boolean {
        if (a.size != b.size || a.isEmpty()) return false
        for (i in a.indices) {
            if (abs(ln(a[i].toDouble().coerceAtLeast(1.0) / b[i].toDouble().coerceAtLeast(1.0))) > 0.12) return false
        }
        return true
    }

    /** Convert a curve from one band layout to another while keeping its overall shape. */
    fun refit(
        levels: List<Float>, fromHz: List<Int>, toHz: List<Int>, minDb: Float, maxDb: Float,
        smooth: Double = if (toHz.size > fromHz.size) UPSCALE_SMOOTH else 0.0
    ): List<Float> {
        if (toHz.isEmpty()) return emptyList()
        if (sameLayout(fromHz, toHz) && levels.size == toHz.size) return levels.map { it.coerceIn(minDb, maxDb) }
        if (levels.all { it == 0f }) return List(toHz.size) { 0f }
        return fitToBands(levelsToProfile(levels, fromHz), toHz, minDb, maxDb, smooth).bandDb
    }

    /** Gains for the real hardware bands that best reproduce the curve the user drew on [layoutHz]. */
    fun mapToDevice(levels: List<Float>, layoutHz: List<Int>, deviceHz: List<Int>, minDb: Float, maxDb: Float): List<Float> =
        refit(levels, layoutHz, deviceHz, minDb, maxDb, smooth = 0.0) // no smoothing: match the drawn curve as exactly as the hardware allows

    private fun graphicAt(points: List<Pair<Double, Double>>, f: Double): Double {
        if (f <= points.first().first) return points.first().second
        if (f >= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val (f0, g0) = points[i]
            val (f1, g1) = points[i + 1]
            if (f <= f1) {
                val t = (ln(f) - ln(f0)) / (ln(f1) - ln(f0))
                return g0 + (g1 - g0) * t
            }
        }
        return points.last().second
    }

    /** Gaussian elimination with partial pivoting. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            val tmpRow = a[c]; a[c] = a[piv]; a[piv] = tmpRow
            val tmpB = b[c]; b[c] = b[piv]; b[piv] = tmpB
            val d = a[c][c]
            if (abs(d) < 1e-12) continue
            for (r in c + 1 until n) {
                val f = a[r][c] / d
                if (f == 0.0) continue
                for (k in c until n) a[r][k] -= f * a[c][k]
                b[r] -= f * b[c]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = b[r]
            for (k in r + 1 until n) s -= a[r][k] * x[k]
            x[r] = if (abs(a[r][r]) < 1e-12) 0.0 else s / a[r][r]
        }
        return x
    }

    // ------------------------------------------------------------------------------ biquad maths (RBJ cookbook)

    /** Magnitude response in dB of one filter at frequency [f] (fs = 48 kHz). */
    internal fun responseDb(type: String, fc: Double, gainDb: Double, q: Double, f: Double): Double {
        val w0 = 2.0 * PI * fc / FS
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val aa = 10.0.pow(gainDb / 40.0)
        val sa = sqrt(aa)

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (type) {
            "PK" -> {
                b0 = 1 + alpha * aa; b1 = -2 * cosW; b2 = 1 - alpha * aa
                a0 = 1 + alpha / aa; a1 = -2 * cosW; a2 = 1 - alpha / aa
            }
            "LS" -> {
                b0 = aa * ((aa + 1) - (aa - 1) * cosW + 2 * sa * alpha)
                b1 = 2 * aa * ((aa - 1) - (aa + 1) * cosW)
                b2 = aa * ((aa + 1) - (aa - 1) * cosW - 2 * sa * alpha)
                a0 = (aa + 1) + (aa - 1) * cosW + 2 * sa * alpha
                a1 = -2 * ((aa - 1) + (aa + 1) * cosW)
                a2 = (aa + 1) + (aa - 1) * cosW - 2 * sa * alpha
            }
            "HS" -> {
                b0 = aa * ((aa + 1) + (aa - 1) * cosW + 2 * sa * alpha)
                b1 = -2 * aa * ((aa - 1) + (aa + 1) * cosW)
                b2 = aa * ((aa + 1) + (aa - 1) * cosW - 2 * sa * alpha)
                a0 = (aa + 1) - (aa - 1) * cosW + 2 * sa * alpha
                a1 = 2 * ((aa - 1) - (aa + 1) * cosW)
                a2 = (aa + 1) - (aa - 1) * cosW - 2 * sa * alpha
            }
            "LP" -> {
                b0 = (1 - cosW) / 2; b1 = 1 - cosW; b2 = (1 - cosW) / 2
                a0 = 1 + alpha; a1 = -2 * cosW; a2 = 1 - alpha
            }
            "HP" -> {
                b0 = (1 + cosW) / 2; b1 = -(1 + cosW); b2 = (1 + cosW) / 2
                a0 = 1 + alpha; a1 = -2 * cosW; a2 = 1 - alpha
            }
            "NO" -> {
                b0 = 1.0; b1 = -2 * cosW; b2 = 1.0
                a0 = 1 + alpha; a1 = -2 * cosW; a2 = 1 - alpha
            }
            else -> return 0.0
        }

        val w = 2.0 * PI * f / FS
        val c1 = cos(w); val s1 = sin(w)
        val c2 = cos(2 * w); val s2 = sin(2 * w)
        // H(e^jw) = (b0 + b1 e^-jw + b2 e^-j2w) / (a0 + a1 e^-jw + a2 e^-j2w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = a0 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        val mag2 = (nr * nr + ni * ni) / (dr * dr + di * di).coerceAtLeast(1e-30)
        return (10.0 * log10(mag2.coerceAtLeast(1e-12))).coerceIn(-60.0, 60.0)
    }
}
