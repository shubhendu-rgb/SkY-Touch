package com.example.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [WidgetLayout]. The same code paints the home-screen bitmap and the editor preview,
 * so what you see while editing is what the widget shows.
 *
 * @param dp pixels per dp for the surface being drawn (used for corner radius).
 */
object WidgetRenderer {

    private val typefaces = HashMap<String, Typeface>()

    private fun typeface(context: Context, c: WidgetComponent): Typeface {
        val style = when {
            c.bold && c.italic -> Typeface.BOLD_ITALIC
            c.bold -> Typeface.BOLD
            c.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (c.customFont.isNotBlank()) {
            val custom = WidgetAssets.typeface(context, c.customFont)
            if (custom != null) {
                val key = "custom:${c.customFont}/$style"
                synchronized(typefaces) {
                    return typefaces.getOrPut(key) { Typeface.create(custom, style) }
                }
            }
            // file missing or unreadable: fall through to the built-in font
        }
        val key = "${c.font.family}/$style"
        synchronized(typefaces) {
            return typefaces.getOrPut(key) {
                Typeface.create(Typeface.create(c.font.family, Typeface.NORMAL), style)
            }
        }
    }

    /**
     * Calculates the exact bounding box for each component in pixels on the target canvas.
     * When [WidgetLayout.autoAdjust] is true, adjusts component size and distance proportionally
     * according to the widget's actual dimensions on the launcher, while allowing full-bleed backgrounds
     * to fill the widget container completely.
     */
    fun computeComponentBoxes(
        layout: WidgetLayout, widthPx: Float, heightPx: Float, dp: Float
    ): Map<String, RectF> {
        val baseW = if (layout.baseWidthDp > 0f) layout.baseWidthDp * dp else widthPx
        val baseH = if (layout.baseHeightDp > 0f) layout.baseHeightDp * dp else heightPx

        val scale = if (layout.autoAdjust && baseW > 0f && baseH > 0f) {
            minOf(widthPx / baseW, heightPx / baseH)
        } else 1f

        val offsetX = if (layout.autoAdjust) (widthPx - baseW * scale) / 2f else 0f
        val offsetY = if (layout.autoAdjust) (heightPx - baseH * scale) / 2f else 0f

        val map = HashMap<String, RectF>()
        for (c in layout.components) {
            if (!c.visible) continue
            val isFullBg = c.type == ComponentType.BACKGROUND && c.w >= 0.95f && c.h >= 0.95f
            val rect = if (isFullBg) {
                // Background fills 100% of the launcher widget bounds to match launcher dimensions exactly
                RectF(0f, 0f, widthPx, heightPx)
            } else if (layout.autoAdjust) {
                // Auto-adjust component position, size and distance proportionally with launcher resizing
                RectF(
                    offsetX + c.x * baseW * scale,
                    offsetY + c.y * baseH * scale,
                    offsetX + (c.x + c.w) * baseW * scale,
                    offsetY + (c.y + c.h) * baseH * scale
                )
            } else {
                RectF(c.x * widthPx, c.y * heightPx, (c.x + c.w) * widthPx, (c.y + c.h) * heightPx)
            }
            map[c.id] = rect
        }
        return map
    }

    fun renderBitmap(
        context: Context, layout: WidgetLayout, widthPx: Int, heightPx: Int, dp: Float,
        now: Long = System.currentTimeMillis()
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), context, layout, widthPx.toFloat(), heightPx.toFloat(), dp, now)
        return bmp
    }

    fun draw(
        canvas: Canvas, context: Context, layout: WidgetLayout,
        widthPx: Float, heightPx: Float, dp: Float, now: Long,
        placeholders: Boolean = false
    ) {
        val dark = DynamicColors.isDark(context, layout.theme)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val battery by lazy(LazyThreadSafetyMode.NONE) { batteryText(context) }

        val boxes = computeComponentBoxes(layout, widthPx, heightPx, dp)

        for (c in layout.components) {
            if (!c.visible) continue
            val box = boxes[c.id] ?: continue
            val bx = box.left
            val by = box.top
            val bw = box.width()
            val bh = box.height()
            val color = DynamicColors.resolve(context, c.color, dark)

            if (c.type == ComponentType.BACKGROUND) {
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.FILL
                paint.color = color
                val r = (c.cornerDp * dp).coerceAtMost(minOf(bw, bh) / 2f)
                canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), r, r, paint)
                continue
            }

            if (c.type == ComponentType.IMAGE) {
                drawImage(canvas, context, c, color, bx, by, bw, bh, dp, placeholders)
                continue
            }

            var text = when (c.type) {
                ComponentType.TIME -> format(if (c.use24h) "HH:mm" else "h:mm", now)
                ComponentType.AMPM -> format("a", now)
                ComponentType.DATE -> format(c.format.ifBlank { "EEE, d MMM" }, now)
                ComponentType.WEEKDAY -> format(c.format.ifBlank { "EEEE" }, now)
                ComponentType.BATTERY -> battery
                ComponentType.TEXT -> c.text
                ComponentType.BACKGROUND, ComponentType.IMAGE -> ""
            }
            if (c.allCaps) text = text.uppercase()
            if (text.isEmpty()) continue

            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = typeface(context, c)
            paint.color = color

            // Fit to the box. A fixed sample is measured for time / AM-PM so the size doesn't jump every minute.
            val sample = when (c.type) {
                ComponentType.TIME -> "00:00"
                ComponentType.AMPM -> "MM"
                else -> text
            }
            val base = bh * 0.8f
            paint.textSize = base
            val sampleWidth = paint.measureText(sample)
            val fit = if (sampleWidth > bw && sampleWidth > 0f) base * bw / sampleWidth else base
            paint.textSize = (fit * c.textScale.coerceIn(0.3f, 1f)).coerceAtLeast(1f)

            val tw = paint.measureText(text)
            val x = when (c.align) {
                TextAlignment.START -> bx
                TextAlignment.CENTER -> bx + (bw - tw) / 2f
                TextAlignment.END -> bx + bw - tw
            }
            val fm = paint.fontMetrics
            val baseline = by + bh / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, x, baseline, paint)
        }
    }

    private fun drawImage(
        canvas: Canvas, context: Context, c: WidgetComponent, color: Int,
        bx: Float, by: Float, bw: Float, bh: Float, dp: Float, placeholders: Boolean
    ) {
        val bmp = if (c.image.isNotBlank()) WidgetAssets.bitmap(context, c.image) else null
        val box = RectF(bx, by, bx + bw, by + bh)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (bmp == null) {
            if (placeholders) { // editor only: show where an empty / missing image sits
                val r = (c.cornerDp * dp).coerceAtMost(min(bw, bh) / 2f)
                paint.color = 0x33FFFFFF
                canvas.drawRoundRect(box, r, r, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * dp
                paint.color = 0x99FFFFFF.toInt()
                canvas.drawRoundRect(box, r, r, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = min(bh * 0.3f, 14f * dp)
                val label = if (c.image.isBlank()) "Image" else "Missing"
                canvas.drawText(label, bx + (bw - paint.measureText(label)) / 2f, by + bh / 2f + paint.textSize / 3f, paint)
            }
            return
        }

        val bw0 = bmp.width.toFloat()
        val bh0 = bmp.height.toFloat()
        val src: RectF
        val dest: RectF
        when (c.imageFit) {
            ImageFit.STRETCH -> { src = RectF(0f, 0f, bw0, bh0); dest = box }
            ImageFit.FIT -> {
                val s = min(bw / bw0, bh / bh0)
                val dw = bw0 * s
                val dh = bh0 * s
                src = RectF(0f, 0f, bw0, bh0)
                dest = RectF(bx + (bw - dw) / 2f, by + (bh - dh) / 2f, bx + (bw + dw) / 2f, by + (bh + dh) / 2f)
            }
            ImageFit.FILL -> {
                val s = max(bw / bw0, bh / bh0)
                val cw = bw / s
                val ch = bh / s
                src = RectF((bw0 - cw) / 2f, (bh0 - ch) / 2f, (bw0 + cw) / 2f, (bh0 + ch) / 2f)
                dest = box
            }
        }

        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply { setRectToRect(src, dest, Matrix.ScaleToFit.FILL) })
        paint.isFilterBitmap = true
        paint.shader = shader
        if (c.tintImage) {
            paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        } else {
            paint.alpha = Color.alpha(color)
        }
        val r = (c.cornerDp * dp).coerceAtMost(min(dest.width(), dest.height()) / 2f)
        canvas.drawRoundRect(dest, r, r, paint)
    }

    private fun format(pattern: String, now: Long): String = try {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    } catch (_: IllegalArgumentException) {
        "?"
    }

    private fun batteryText(context: Context): String {
        val i = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "--%"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else return "--%"
        return (if (charging) "⚡ " else "") + "$pct%"
    }
}
