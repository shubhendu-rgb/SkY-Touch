package com.example.service.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONObject

/**
 * Edits for ONE static wallpaper picture: the same options the live wallpapers have
 * (rotate, crop / pinch, crop mode, blur and color filter).
 */
data class StaticImageEdit(
    val rotationDegrees: Int = 0,
    val cropMode: LiveCropMode = LiveCropMode.FILL,
    val colorFilter: LiveColorFilter = LiveColorFilter.NONE,
    val blurRadius: Int = 0,
    val scaleFactor: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val isDefault: Boolean
        get() = rotationDegrees % 360 == 0 &&
            cropMode == LiveCropMode.FILL &&
            colorFilter == LiveColorFilter.NONE &&
            blurRadius == 0 &&
            scaleFactor == 1.0f &&
            offsetX == 0f &&
            offsetY == 0f
}

/**
 * Remembers the edit of every static picture (key = the picture's uri string) and renders it.
 * WallpaperWorker.processBitmap asks this store, so an edited picture looks the same everywhere it is used
 * (Apply icon, auto-change, schedules, tiles, gestures).
 */
object StaticImageEditStore {
    private const val PREFS = "static_image_edits"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, uri: String): StaticImageEdit? {
        val raw = prefs(context).getString(uri, null) ?: return null
        return try {
            val j = JSONObject(raw)
            StaticImageEdit(
                rotationDegrees = j.optInt("rot", 0),
                cropMode = try { LiveCropMode.valueOf(j.optString("crop", "FILL")) } catch (_: Exception) { LiveCropMode.FILL },
                colorFilter = try { LiveColorFilter.valueOf(j.optString("filter", "NONE")) } catch (_: Exception) { LiveColorFilter.NONE },
                blurRadius = j.optInt("blur", 0),
                scaleFactor = j.optDouble("scale", 1.0).toFloat(),
                offsetX = j.optDouble("ox", 0.0).toFloat(),
                offsetY = j.optDouble("oy", 0.0).toFloat()
            ).takeIf { !it.isDefault }
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, uri: String, edit: StaticImageEdit) {
        val e = prefs(context).edit()
        if (edit.isDefault) {
            e.remove(uri)
        } else {
            val j = JSONObject().apply {
                put("rot", edit.rotationDegrees)
                put("crop", edit.cropMode.name)
                put("filter", edit.colorFilter.name)
                put("blur", edit.blurRadius)
                put("scale", edit.scaleFactor.toDouble())
                put("ox", edit.offsetX.toDouble())
                put("oy", edit.offsetY.toDouble())
            }
            e.putString(uri, j.toString())
        }
        e.apply()
    }

    fun clear(context: Context, uri: String) {
        prefs(context).edit().remove(uri).apply()
    }

    /**
     * Draws [source] onto a [targetW] x [targetH] picture the same way the Fullscreen Crop & Pinch screen
     * shows it: base fit by crop mode, then scale and rotate around the centre, then move by the offset.
     */
    fun render(source: Bitmap, edit: StaticImageEdit, targetW: Int, targetH: Int): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        if (edit.colorFilter != LiveColorFilter.NONE) {
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(LiveWallpaperManager.getColorMatrix(edit.colorFilter))
            )
        }

        val w = targetW.toFloat()
        val h = targetH.toFloat()
        val bw = source.width.toFloat()
        val bh = source.height.toFloat()

        val cover = maxOf(w / bw, h / bh)
        val contain = minOf(w / bw, h / bh)
        val dst = when (edit.cropMode) {
            LiveCropMode.STRETCH -> RectF(0f, 0f, w, h)
            LiveCropMode.FIT -> {
                val dw = bw * contain
                val dh = bh * contain
                RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            }
            LiveCropMode.LEFT -> {
                val dw = bw * cover
                val dh = bh * cover
                RectF(0f, (h - dh) / 2f, dw, (h + dh) / 2f)
            }
            LiveCropMode.RIGHT -> {
                val dw = bw * cover
                val dh = bh * cover
                RectF(w - dw, (h - dh) / 2f, w, (h + dh) / 2f)
            }
            else -> {
                val dw = bw * cover
                val dh = bh * cover
                RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            }
        }

        canvas.save()
        canvas.translate(edit.offsetX, edit.offsetY)
        canvas.rotate(edit.rotationDegrees.toFloat(), w / 2f, h / 2f)
        val s = edit.scaleFactor.coerceIn(0.2f, 6.0f)
        canvas.scale(s, s, w / 2f, h / 2f)
        canvas.drawBitmap(source, null, dst, paint)
        canvas.restore()
        return out
    }
}
