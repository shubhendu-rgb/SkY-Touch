package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Files the user brings into the widget engine: custom fonts (.ttf / .otf / .ttc) and small images.
 * Everything is copied into the app's private storage, so widgets keep working if the original file is moved.
 * Each imported file must be 5 MB or smaller. Images are also shrunk to 1024 px on their long side
 * (kept as PNG, so transparency survives), which keeps the widget light on memory and battery.
 */
object WidgetAssets {
    const val MAX_BYTES = 5L * 1024 * 1024
    private const val MAX_IMAGE_SIDE = 1024

    private fun fontsDir(context: Context) = File(context.applicationContext.filesDir, "widget_fonts").apply { mkdirs() }
    private fun imagesDir(context: Context) = File(context.applicationContext.filesDir, "widget_images").apply { mkdirs() }

    // ───────── fonts ─────────

    fun listFonts(context: Context): List<String> =
        fontsDir(context).listFiles()?.filter { it.isFile }?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()

    fun fontLabel(fileName: String): String = fileName.substringBeforeLast('.').replace('_', ' ')

    private val fontCache = HashMap<String, Typeface>()

    /** The imported font, or null if the file is gone or can't be read (callers fall back to a built-in font). */
    fun typeface(context: Context, fileName: String): Typeface? {
        val f = File(fontsDir(context), File(fileName).name)
        if (!f.isFile) return null
        val key = f.name + "@" + f.lastModified()
        synchronized(fontCache) {
            fontCache[key]?.let { return it }
            val tf = try { Typeface.createFromFile(f) } catch (_: Throwable) { null }
            if (tf != null) fontCache[key] = tf
            return tf
        }
    }

    fun deleteFont(context: Context, fileName: String) {
        File(fontsDir(context), File(fileName).name).delete()
    }

    fun importFont(context: Context, uri: Uri): Result<String> = runCatching {
        val tmp = File(context.cacheDir, "widget_font_${System.nanoTime()}")
        try {
            copyLimited(context, uri, tmp)
            val head = ByteArray(4)
            tmp.inputStream().use { require(it.read(head) == 4) { "That file isn't a font." } }
            val tag = String(head, Charsets.ISO_8859_1)
            val isTtf = head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0
            val ext = when {
                tag == "OTTO" -> "otf"
                tag == "ttcf" -> "ttc"
                isTtf || tag == "true" -> "ttf"
                else -> throw IllegalArgumentException("Please choose a .ttf or .otf font file.")
            }
            val base = sanitize(displayName(context, uri)?.substringBeforeLast('.') ?: "font").ifBlank { "font" }
            val dir = fontsDir(context)
            var name = "$base.$ext"
            var n = 2
            while (File(dir, name).exists()) { name = "${base}_$n.$ext"; n++ }
            val dest = File(dir, name)
            tmp.copyTo(dest, overwrite = false)
            val ok = try { Typeface.createFromFile(dest); true } catch (_: Throwable) { false }
            if (!ok) { dest.delete(); throw IllegalArgumentException("Android couldn't read this font.") }
            name
        } finally {
            tmp.delete()
        }
    }

    // ───────── images ─────────

    private val imageCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun bitmap(context: Context, fileName: String): Bitmap? {
        imageCache.get(fileName)?.let { return it }
        val f = File(imagesDir(context), File(fileName).name)
        if (!f.isFile) return null
        val bmp = try { BitmapFactory.decodeFile(f.path) } catch (_: Throwable) { null } ?: return null
        imageCache.put(fileName, bmp)
        return bmp
    }

    /** width / height of an imported image, or null when it can't be read. */
    fun aspect(context: Context, fileName: String): Float? =
        bitmap(context, fileName)?.let { it.width.toFloat() / it.height.toFloat() }

    fun importImage(context: Context, uri: Uri): Result<String> = runCatching {
        val tmp = File(context.cacheDir, "widget_image_${System.nanoTime()}")
        try {
            copyLimited(context, uri, tmp)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file isn't an image Android can read." }

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE * 2) sample *= 2
            var bmp = BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IllegalArgumentException("That image couldn't be opened.")

            val longSide = max(bmp.width, bmp.height)
            if (longSide > MAX_IMAGE_SIDE) {
                val s = MAX_IMAGE_SIDE.toFloat() / longSide
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true
                )
            }

            val degrees = try {
                when (android.media.ExifInterface(tmp.path).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Throwable) { 0f }
            if (degrees != 0f) {
                bmp = Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true
                )
            }

            val name = "img_" + UUID.randomUUID().toString().take(10) + ".png"
            FileOutputStream(File(imagesDir(context), name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        } finally {
            tmp.delete()
        }
    }

    /** Deletes imported images that no saved layout uses any more. */
    fun cleanupImages(context: Context, keep: Set<String>) {
        imagesDir(context).listFiles()?.forEach {
            if (it.isFile && it.name !in keep) {
                imageCache.remove(it.name)
                it.delete()
            }
        }
    }

    // ───────── helpers ─────────

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Throwable) { null }

    private fun sanitize(s: String) = s.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(40)

    /** Copies the picked file, refusing anything over [MAX_BYTES]. */
    private fun copyLimited(context: Context, uri: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Couldn't open that file.")
        input.use { src ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) {
                        throw IllegalArgumentException("That file is bigger than 5 MB.")
                    }
                    out.write(buf, 0, n)
                }
            }
        }
    }
}
