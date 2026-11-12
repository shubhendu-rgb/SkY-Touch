package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Position & size of one notch gesture area.
 * Area 1 is the original notch (stored in [NotchConfigEntity]); areas 2 and 3 are stored by [NotchAreas].
 */
data class NotchAreaGeometry(
    val id: Int,
    val widthDp: Int = 100,
    val heightDp: Int = 36,
    val xOffsetDp: Int = 0,
    val yOffsetDp: Int = 0,
    val cornerRadiusDp: Int = 18
)

fun NotchConfigEntity.toAreaGeometry() = NotchAreaGeometry(
    id = 1,
    widthDp = widthDp,
    heightDp = heightDp,
    xOffsetDp = xOffsetDp,
    yOffsetDp = yOffsetDp,
    cornerRadiusDp = cornerRadiusDp
)

fun NotchConfigEntity.withAreaGeometry(g: NotchAreaGeometry) = copy(
    widthDp = g.widthDp,
    heightDp = g.heightDp,
    xOffsetDp = g.xOffsetDp,
    yOffsetDp = g.yOffsetDp,
    cornerRadiusDp = g.cornerRadiusDp
)

/**
 * Extra notch gesture areas (up to [MAX_AREAS] in total, counting the original notch).
 *
 * Geometry of areas 2 and 3 lives in SharedPreferences, and their gesture actions live in the normal
 * gesture_actions table under the keys "A2_SINGLE_TAP", "A3_LONG_PRESS", ... so no database schema
 * change (and therefore no data wipe) is needed.
 */
object NotchAreas {
    const val MAX_AREAS = 3

    private const val PREFS = "notch_areas_prefs"
    private const val KEY_AREAS = "extra_areas"

    /** Every gesture an area can be configured with. */
    val GESTURE_NAMES = listOf(
        "SINGLE_TAP",
        "DOUBLE_TAP",
        "TRIPLE_TAP",
        "LONG_PRESS",
        "SWIPE_LEFT",
        "SWIPE_RIGHT",
        "SWIPE_LEFT_AND_HOLD",
        "SWIPE_RIGHT_AND_HOLD"
    )

    private val _extraAreas = MutableStateFlow<List<NotchAreaGeometry>>(emptyList())
    val extraAreas: StateFlow<List<NotchAreaGeometry>> = _extraAreas.asStateFlow()

    private var loaded = false

    /** Key used in the gesture_actions table for [gesture] of area [areaId]. */
    fun actionKey(areaId: Int, gesture: String): String =
        if (areaId <= 1) gesture else "A${areaId}_$gesture"

    /** Area id a gesture_actions key belongs to (1 for the original notch). */
    fun areaOfKey(key: String): Int {
        if (key.length > 3 && key[0] == 'A' && key[1].isDigit() && key[2] == '_') {
            return key[1].digitToInt()
        }
        return 1
    }

    /** Plain gesture name of a gesture_actions key ("A2_LONG_PRESS" -> "LONG_PRESS"). */
    fun gestureOfKey(key: String): String =
        if (areaOfKey(key) > 1) key.substring(3) else key

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        _extraAreas.value = decode(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AREAS, null)
        )
        loaded = true
    }

    /** Default placement for a new area: to the left of the notch for area 2, to the right for area 3. */
    fun defaultGeometry(id: Int) = NotchAreaGeometry(
        id = id,
        widthDp = 80,
        heightDp = 36,
        xOffsetDp = if (id == 2) -130 else 130,
        yOffsetDp = 0,
        cornerRadiusDp = 18
    )

    /** Adds the next free area (2 or 3). Returns it, or null when the maximum is already reached. */
    @Synchronized
    fun add(context: Context): NotchAreaGeometry? {
        init(context)
        val current = _extraAreas.value
        if (current.size + 1 >= MAX_AREAS) return null
        val freeId = (2..MAX_AREAS).firstOrNull { id -> current.none { it.id == id } } ?: return null
        val area = defaultGeometry(freeId)
        save(context, (current + area).sortedBy { it.id })
        return area
    }

    @Synchronized
    fun update(context: Context, area: NotchAreaGeometry) {
        init(context)
        if (area.id <= 1) return
        val current = _extraAreas.value
        if (current.none { it.id == area.id }) return
        save(context, current.map { if (it.id == area.id) area else it })
    }

    @Synchronized
    fun remove(context: Context, areaId: Int) {
        init(context)
        save(context, _extraAreas.value.filter { it.id != areaId })
    }

    /** Replaces all extra areas (used by Restore). */
    @Synchronized
    fun replaceAll(context: Context, areas: List<NotchAreaGeometry>) {
        init(context)
        save(
            context,
            areas.filter { it.id in 2..MAX_AREAS }.distinctBy { it.id }.sortedBy { it.id }
        )
    }

    private fun save(context: Context, areas: List<NotchAreaGeometry>) {
        _extraAreas.value = areas
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AREAS, encode(areas))
            .apply()
    }

    fun encode(areas: List<NotchAreaGeometry>): String {
        val arr = JSONArray()
        areas.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("widthDp", a.widthDp)
                    put("heightDp", a.heightDp)
                    put("xOffsetDp", a.xOffsetDp)
                    put("yOffsetDp", a.yOffsetDp)
                    put("cornerRadiusDp", a.cornerRadiusDp)
                }
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<NotchAreaGeometry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<NotchAreaGeometry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optInt("id", -1)
                if (id !in 2..MAX_AREAS) continue
                val d = defaultGeometry(id)
                out.add(
                    NotchAreaGeometry(
                        id = id,
                        widthDp = o.optInt("widthDp", d.widthDp),
                        heightDp = o.optInt("heightDp", d.heightDp),
                        xOffsetDp = o.optInt("xOffsetDp", d.xOffsetDp),
                        yOffsetDp = o.optInt("yOffsetDp", d.yOffsetDp),
                        cornerRadiusDp = o.optInt("cornerRadiusDp", d.cornerRadiusDp)
                    )
                )
            }
            out.distinctBy { it.id }.sortedBy { it.id }.take(MAX_AREAS - 1)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
