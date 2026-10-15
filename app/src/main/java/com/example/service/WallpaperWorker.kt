package com.example.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.example.data.AppDatabase
import com.example.data.WallpaperConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val screenType = inputData.getString("screenType") ?: return@withContext Result.failure()
        
        try {
            val db = AppDatabase.getDatabase(context)
            val config = db.wallpaperConfigDao().getConfig(screenType)
            
            if (config == null || !config.isEnabled) {
                return@withContext Result.failure()
            }

            val applied = applyWallpaperForConfig(context, config)
            if (!applied) {
                return@withContext Result.failure()
            }

            // If we are using exact time scheduling, we need to schedule the next day's work manually
            // since PeriodicWorkRequest doesn't easily support exact time of day.
            if (config.useSchedule && config.scheduledTime != null) {
                scheduleWallpaperWork(context, config)
            } else if (!config.useSchedule && config.intervalMinutes < 15) {
                // Workaround for WorkManager's 15 min minimum periodic limit
                scheduleWallpaperWork(context, config)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Error applying wallpaper: ${e.message}", e)
            Result.failure()
        }
    }
    
    companion object {
        fun processBitmap(
            context: Context,
            uri: Uri,
            applyGradient: Boolean,
            applyGrayscale: Boolean,
            cropAlignment: String = "CENTER",
            isScrollable: Boolean = false,
            blurRadius: Int = 0,
            customScale: Float = 1.0f,
            customOffsetX: Float = 0f,
            customOffsetY: Float = 0f
        ): Bitmap? {
            try {
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                // If scrollable, use wide width for launcher parallax scrolling
                val targetWidth = if (isScrollable) (screenWidth * 2) else screenWidth
                val targetHeight = screenHeight

                // Update WallpaperManager desired dimensions
                val wallpaperManager = WallpaperManager.getInstance(context)
                try {
                    wallpaperManager.suggestDesiredDimensions(targetWidth, targetHeight)
                } catch (_: Exception) {}

                // Fast decode with target size / downsampling to prevent OOM and decode instantly
                val originalBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val srcW = info.size.width
                            val srcH = info.size.height
                            if (srcW > targetWidth * 2 || srcH > targetHeight * 2) {
                                val sample = maxOf(1, minOf(srcW / targetWidth, srcH / targetHeight))
                                decoder.setTargetSampleSize(sample)
                            }
                        }
                    } catch (e: Exception) {
                        decodeSampledBitmapFromUri(context, uri, targetWidth, targetHeight) ?: return null
                    }
                } else {
                    decodeSampledBitmapFromUri(context, uri, targetWidth, targetHeight) ?: return null
                }

                // Scale original bitmap to cover target dimensions while preserving aspect ratio and applying user scale
                val baseScale = maxOf(
                    targetWidth.toFloat() / originalBitmap.width.toFloat(),
                    targetHeight.toFloat() / originalBitmap.height.toFloat()
                )
                val effectiveScale = baseScale * customScale.coerceIn(0.5f, 4.0f)
                val scaledWidth = kotlin.math.max(targetWidth, (originalBitmap.width * effectiveScale).toInt())
                val scaledHeight = kotlin.math.max(targetHeight, (originalBitmap.height * effectiveScale).toInt())

                val scaledBitmap = if (scaledWidth == originalBitmap.width && scaledHeight == originalBitmap.height) {
                    originalBitmap
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
                }

                // Calculate crop X and Y offsets according to alignment and user pan
                val extraWidth = scaledWidth - targetWidth
                val baseOffsetX = when (cropAlignment.uppercase()) {
                    "LEFT" -> 0
                    "RIGHT" -> extraWidth.coerceAtLeast(0)
                    else -> (extraWidth / 2).coerceAtLeast(0) // CENTER / MIDDLE
                }
                val baseOffsetY = ((scaledHeight - targetHeight) / 2).coerceAtLeast(0)

                val xOffset = (baseOffsetX - customOffsetX.toInt()).coerceIn(0, (scaledBitmap.width - targetWidth).coerceAtLeast(0))
                val yOffset = (baseOffsetY - customOffsetY.toInt()).coerceIn(0, (scaledBitmap.height - targetHeight).coerceAtLeast(0))

                val cropW = minOf(targetWidth, scaledBitmap.width - xOffset)
                val cropH = minOf(targetHeight, scaledBitmap.height - yOffset)

                var resultBitmap = Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, cropW, cropH)

                // Apply blur if requested
                if (blurRadius > 0) {
                    try {
                        resultBitmap = fastBlur(resultBitmap, blurRadius)
                    } catch (e: Exception) {
                        Log.e("WallpaperWorker", "Blur failed: ${e.message}")
                    }
                }

                if (applyGrayscale) {
                    val grayBitmap = Bitmap.createBitmap(resultBitmap.width, resultBitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(grayBitmap)
                    val paint = Paint()
                    val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                    canvas.drawBitmap(resultBitmap, 0f, 0f, paint)
                    resultBitmap = grayBitmap
                }

                if (applyGradient) {
                    val canvas = Canvas(resultBitmap)
                    val paint = Paint()
                    val w = resultBitmap.width.toFloat()
                    val h = resultBitmap.height.toFloat()

                    // Top Gradient (0 to 20% of height)
                    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.2f,
                        intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h * 0.2f, paint)

                    // Bottom Gradient (80% to 100% of height)
                    paint.shader = LinearGradient(0f, h * 0.8f, 0f, h,
                        intArrayOf(Color.TRANSPARENT, Color.parseColor("#CC000000")),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, h * 0.8f, w, h, paint)
                }

                return resultBitmap
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Error processing wallpaper bitmap: ${e.message}", e)
                return null
            }
        }

        fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
            if (radius < 1) return sentBitmap
            try {
                // Downscale for silky smooth look and rapid processing
                val downscale = when {
                    radius >= 20 -> 4
                    radius >= 10 -> 3
                    else -> 2
                }
                val width = (sentBitmap.width / downscale).coerceAtLeast(1)
                val height = (sentBitmap.height / downscale).coerceAtLeast(1)
                val scaledBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, true)
                val effectiveRadius = ((radius.toFloat() / downscale) * 1.8f).toInt().coerceIn(2, 35)
                val blurred = applyStackBlur(scaledBitmap, effectiveRadius)
                return Bitmap.createScaledBitmap(blurred, sentBitmap.width, sentBitmap.height, true)
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Blur failed: ${e.message}", e)
                return sentBitmap
            }
        }

        /**
         * Proven Mario Klingemann StackBlur implementation.
         * Creates authentic Gaussian blur without pixel drift or accumulator overflow.
         */
        private fun applyStackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
            val bitmap = sentBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return sentBitmap
            if (radius < 1) return bitmap

            val w = bitmap.width
            val h = bitmap.height
            val pix = IntArray(w * h)
            bitmap.getPixels(pix, 0, w, 0, 0, w, h)

            val wm = w - 1
            val hm = h - 1
            val wh = w * h
            val div = radius + radius + 1

            val r = IntArray(wh)
            val g = IntArray(wh)
            val b = IntArray(wh)
            val a = IntArray(wh)
            var rsum: Int
            var gsum: Int
            var bsum: Int
            var asum: Int
            var p: Int
            var yp: Int
            var yi: Int
            var yw: Int
            val vmin = IntArray(maxOf(w, h))

            var divsum = (div + 1) shr 1
            divsum *= divsum
            val dv = IntArray(256 * divsum)
            for (idx in 0 until 256 * divsum) {
                dv[idx] = (idx / divsum)
            }

            yw = 0
            yi = 0

            val stack = Array(div) { IntArray(4) }
            var stackpointer: Int
            var stackstart: Int
            var sir: IntArray
            var rbs: Int
            val r1 = radius + 1
            var routsum: Int
            var goutsum: Int
            var boutsum: Int
            var aoutsum: Int
            var rinsum: Int
            var ginsum: Int
            var binsum: Int
            var ainsum: Int

            for (yIdx in 0 until h) {
                rinsum = 0; ginsum = 0; binsum = 0; ainsum = 0
                routsum = 0; goutsum = 0; boutsum = 0; aoutsum = 0
                rsum = 0; gsum = 0; bsum = 0; asum = 0
                for (iIdx in -radius..radius) {
                    p = pix[yi + minOf(wm, maxOf(iIdx, 0))]
                    sir = stack[iIdx + radius]
                    sir[0] = (p ushr 16) and 0xff
                    sir[1] = (p ushr 8) and 0xff
                    sir[2] = p and 0xff
                    sir[3] = (p ushr 24) and 0xff
                    rbs = r1 - kotlin.math.abs(iIdx)
                    rsum += sir[0] * rbs
                    gsum += sir[1] * rbs
                    bsum += sir[2] * rbs
                    asum += sir[3] * rbs
                    if (iIdx > 0) {
                        rinsum += sir[0]
                        ginsum += sir[1]
                        binsum += sir[2]
                        ainsum += sir[3]
                    } else {
                        routsum += sir[0]
                        goutsum += sir[1]
                        boutsum += sir[2]
                        aoutsum += sir[3]
                    }
                }
                stackpointer = radius

                for (xIdx in 0 until w) {
                    r[yi] = dv[rsum]
                    g[yi] = dv[gsum]
                    b[yi] = dv[bsum]
                    a[yi] = dv[asum]

                    rsum -= routsum
                    gsum -= goutsum
                    bsum -= boutsum
                    asum -= aoutsum

                    stackstart = stackpointer - radius + div
                    sir = stack[stackstart % div]

                    routsum -= sir[0]
                    goutsum -= sir[1]
                    boutsum -= sir[2]
                    aoutsum -= sir[3]

                    if (yIdx == 0) {
                        vmin[xIdx] = minOf(xIdx + radius + 1, wm)
                    }
                    p = pix[yw + vmin[xIdx]]

                    sir[0] = (p ushr 16) and 0xff
                    sir[1] = (p ushr 8) and 0xff
                    sir[2] = p and 0xff
                    sir[3] = (p ushr 24) and 0xff

                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    ainsum += sir[3]

                    rsum += rinsum
                    gsum += ginsum
                    bsum += binsum
                    asum += ainsum

                    stackpointer = (stackpointer + 1) % div
                    sir = stack[stackpointer % div]

                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    aoutsum += sir[3]

                    rinsum -= sir[0]
                    ginsum -= sir[1]
                    binsum -= sir[2]
                    ainsum -= sir[3]

                    yi++
                }
                yw += w
            }

            for (xIdx in 0 until w) {
                rinsum = 0; ginsum = 0; binsum = 0; ainsum = 0
                routsum = 0; goutsum = 0; boutsum = 0; aoutsum = 0
                rsum = 0; gsum = 0; bsum = 0; asum = 0
                yp = -radius * w
                for (iIdx in -radius..radius) {
                    yi = maxOf(0, yp) + xIdx
                    sir = stack[iIdx + radius]
                    sir[0] = r[yi]
                    sir[1] = g[yi]
                    sir[2] = b[yi]
                    sir[3] = a[yi]
                    rbs = r1 - kotlin.math.abs(iIdx)
                    rsum += r[yi] * rbs
                    gsum += g[yi] * rbs
                    bsum += b[yi] * rbs
                    asum += a[yi] * rbs
                    if (iIdx > 0) {
                        rinsum += sir[0]
                        ginsum += sir[1]
                        binsum += sir[2]
                        ainsum += sir[3]
                    } else {
                        routsum += sir[0]
                        goutsum += sir[1]
                        boutsum += sir[2]
                        aoutsum += sir[3]
                    }
                    if (iIdx < hm) {
                        yp += w
                    }
                }
                yi = xIdx
                stackpointer = radius
                for (yIdx in 0 until h) {
                    pix[yi] = (dv[asum] shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                    rsum -= routsum
                    gsum -= goutsum
                    bsum -= boutsum
                    asum -= aoutsum

                    stackstart = stackpointer - radius + div
                    sir = stack[stackstart % div]

                    routsum -= sir[0]
                    goutsum -= sir[1]
                    boutsum -= sir[2]
                    aoutsum -= sir[3]

                    if (xIdx == 0) {
                        vmin[yIdx] = minOf(yIdx + r1, hm) * w
                    }
                    p = xIdx + vmin[yIdx]

                    sir[0] = r[p]
                    sir[1] = g[p]
                    sir[2] = b[p]
                    sir[3] = a[p]

                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    ainsum += sir[3]

                    rsum += rinsum
                    gsum += ginsum
                    bsum += binsum
                    asum += ainsum

                    stackpointer = (stackpointer + 1) % div
                    sir = stack[stackpointer]

                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    aoutsum += sir[3]

                    rinsum -= sir[0]
                    ginsum -= sir[1]
                    binsum -= sir[2]
                    ainsum += sir[3]

                    yi += w
                }
            }

            bitmap.setPixels(pix, 0, w, 0, 0, w, h)
            return bitmap
        }

        private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    val srcW = options.outWidth
                    val srcH = options.outHeight
                    var inSampleSize = 1
                    if (srcH > reqHeight || srcW > reqWidth) {
                        val halfHeight = srcH / 2
                        val halfWidth = srcW / 2
                        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                            inSampleSize *= 2
                        }
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream2 ->
                        val decodeOptions = BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                            inMutable = true
                        }
                        BitmapFactory.decodeStream(stream2, null, decodeOptions)
                    }
                }
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Error decoding sampled bitmap: ${e.message}", e)
                null
            }
        }

        private var cachedFolderUri: String? = null
        private var cachedFolderUris: List<Uri> = emptyList()
        private var cachedFolderTime: Long = 0L

        fun invalidateFolderCache() {
            cachedFolderUri = null
            cachedFolderUris = emptyList()
            cachedFolderTime = 0L
        }

        private fun collectAllCandidateUris(context: Context, config: WallpaperConfigEntity, sourceFilter: String? = null): List<Uri> {
            val candidates = mutableListOf<Uri>()

            // 1. Check folderUri if allowed
            if (sourceFilter != "IMAGES_ONLY" && !config.folderUri.isNullOrEmpty()) {
                val folderUriStr = config.folderUri
                val now = System.currentTimeMillis()
                if (folderUriStr == cachedFolderUri && (now - cachedFolderTime < 120_000L) && cachedFolderUris.isNotEmpty()) {
                    candidates.addAll(cachedFolderUris)
                } else {
                    val folderCandidates = mutableListOf<Uri>()
                    try {
                        val treeUri = Uri.parse(folderUriStr)
                        val docId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                            DocumentsContract.getDocumentId(treeUri)
                        } else {
                            DocumentsContract.getTreeDocumentId(treeUri)
                        }
                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                        val projection = arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        )
                        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            while (cursor.moveToNext()) {
                                val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                                if (mime != null && (mime.startsWith("image/") || mime.equals("application/octet-stream", ignoreCase = true))) {
                                    val childId = cursor.getString(idIndex)
                                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                    folderCandidates.add(docUri)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("WallpaperWorker", "Fast DocumentsContract query failed, falling back: ${e.message}")
                    }

                    // Fallback to DocumentFile if direct cursor returned empty
                    if (folderCandidates.isEmpty()) {
                        try {
                            val folderUri = Uri.parse(folderUriStr)
                            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                            if (documentFile != null && documentFile.exists() && documentFile.isDirectory) {
                                val folderImages = documentFile.listFiles()
                                    .filter { file ->
                                        val type = file.type
                                        type != null && type.startsWith("image/")
                                    }
                                    .map { it.uri }
                                folderCandidates.addAll(folderImages)
                            }
                        } catch (e: Exception) {
                            Log.e("WallpaperWorker", "Error reading folder images: ${e.message}")
                        }
                    }

                    if (folderCandidates.isNotEmpty()) {
                        cachedFolderUri = folderUriStr
                        cachedFolderUris = folderCandidates
                        cachedFolderTime = now
                        candidates.addAll(folderCandidates)
                    }
                }
            }

            // 2. Check individual imageUris list if allowed
            if (sourceFilter != "FOLDER_ONLY") {
                val individualList = config.getImageUriList()
                for (uriStr in individualList) {
                    try {
                        val uri = Uri.parse(uriStr)
                        candidates.add(uri)
                    } catch (_: Exception) {}
                }
            }

            return candidates
        }

        suspend fun applyWallpaperForConfig(
            context: Context,
            config: WallpaperConfigEntity,
            sourceFilter: String? = null,
            preloadedCandidates: List<Uri>? = null
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val candidateUris = preloadedCandidates ?: collectAllCandidateUris(context, config, sourceFilter)
                if (candidateUris.isEmpty()) {
                    Log.w("WallpaperWorker", "No candidate images found for ${config.screenType} filter=$sourceFilter")
                    return@withContext false
                }

                val selectedUri = candidateUris.random()
                val bitmap = processBitmap(
                    context = context,
                    uri = selectedUri,
                    applyGradient = config.applyGradient,
                    applyGrayscale = config.applyGrayscale,
                    cropAlignment = config.cropAlignment,
                    isScrollable = config.isScrollable,
                    blurRadius = config.blurRadius
                )

                if (bitmap != null) {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val flag = if (config.screenType == "LOCK") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                    wallpaperManager.setBitmap(bitmap, null, false, flag)
                    Log.i("WallpaperWorker", "Applied wallpaper for ${config.screenType} (align=${config.cropAlignment}, scroll=${config.isScrollable}) from $selectedUri")
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Failed applying wallpaper for ${config.screenType}: ${e.message}", e)
                false
            }
        }

        /**
         * Rule-driven wallpaper change used by the Quick Settings tiles and the notch gestures.
         *
         * The rule (see [WallpaperRules]) decides which screen(s) change, where the picture comes from
         * and whether Home and Lock share the same picture. Works even when the automatic changer is off.
         *
         * If a target screen has no pictures of its own, it borrows the other screen's pictures, so a
         * "Both" rule can no longer end up changing only the screen that happens to have images.
         *
         * @param direction "NEXT", "PREV" or "RANDOM".
         * @param targetOverride "HOME" or "LOCK" to force a single screen (explicit gesture actions).
         * @param sourceOverride "IMAGES_ONLY" / "FOLDER_ONLY" to force the source (tiles, explicit gesture actions).
         */
        suspend fun applyRule(
            context: Context,
            trigger: String,
            direction: String = "NEXT",
            targetOverride: String? = null,
            sourceOverride: String? = null
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val rule = WallpaperRules.load(context, trigger)
                val target = targetOverride ?: rule.target
                val filter = sourceOverride ?: rule.sourceFilter
                val effectiveDirection =
                    if (direction == "NEXT" &&
                        trigger != WallpaperRules.TRIGGER_GESTURE &&
                        rule.order == WallpaperRules.ORDER_RANDOM
                    ) "RANDOM" else direction

                val dao = AppDatabase.getDatabase(context).wallpaperConfigDao()
                val home = dao.getConfig("HOME") ?: WallpaperConfigEntity(screenType = "HOME")
                val lock = dao.getConfig("LOCK") ?: WallpaperConfigEntity(screenType = "LOCK")

                val homePool = collectAllCandidateUris(context, home, filter)
                val lockPool = collectAllCandidateUris(context, lock, filter)
                if (homePool.isEmpty() && lockPool.isEmpty()) {
                    Log.w("WallpaperWorker", "applyRule($trigger): no pictures for filter=$filter")
                    return@withContext false
                }

                val prefs = context.getSharedPreferences("wallpaper_cycle_prefs", Context.MODE_PRIVATE)
                val filterKey = filter ?: "ALL"
                val screens = when (target) {
                    WallpaperRules.TARGET_HOME -> listOf(home)
                    WallpaperRules.TARGET_LOCK -> listOf(lock)
                    else -> listOf(home, lock)
                }

                if (screens.size == 2 && rule.sameOnBoth) {
                    // One picture for both screens.
                    val pool = (homePool + lockPool).distinct()
                    val uri = pickUri(prefs, "idx_SYNC_$filterKey", pool, effectiveDirection)
                    val ok = applyUriToConfigs(context, uri, screens)
                    Log.i("WallpaperWorker", "applyRule($trigger): synced $effectiveDirection on both screens -> $ok")
                    return@withContext ok
                }

                var appliedAny = false
                for (config in screens) {
                    val ownPool = if (config.screenType == "LOCK") lockPool else homePool
                    val otherPool = if (config.screenType == "LOCK") homePool else lockPool
                    val pool = ownPool.ifEmpty { otherPool }
                    if (pool.isEmpty()) continue
                    val uri = pickUri(prefs, "idx_${config.screenType}_$filterKey", pool, effectiveDirection)
                    if (applyUriToConfigs(context, uri, listOf(config))) appliedAny = true
                }
                Log.i("WallpaperWorker", "applyRule($trigger): $effectiveDirection on $target -> $appliedAny")
                appliedAny
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Error in applyRule($trigger): ${e.message}", e)
                false
            }
        }

        /** Picks the next / previous / random entry and remembers the position under [key]. */
        private fun pickUri(
            prefs: android.content.SharedPreferences,
            key: String,
            pool: List<Uri>,
            direction: String
        ): Uri {
            val last = prefs.getInt(key, -1).takeIf { it in pool.indices } ?: -1
            val newIdx = when (direction) {
                "PREV" -> if (last <= 0) pool.size - 1 else last - 1
                "RANDOM" -> {
                    if (pool.size > 1) {
                        var r = pool.indices.random()
                        if (r == last) r = (r + 1) % pool.size
                        r
                    } else 0
                }
                else -> (last + 1) % pool.size
            }
            prefs.edit().putInt(key, newIdx).apply()
            return pool[newIdx]
        }

        private fun sameLook(a: WallpaperConfigEntity, b: WallpaperConfigEntity): Boolean =
            a.applyGradient == b.applyGradient &&
                a.applyGrayscale == b.applyGrayscale &&
                a.cropAlignment == b.cropAlignment &&
                a.isScrollable == b.isScrollable &&
                a.blurRadius == b.blurRadius

        /**
         * Applies [uri] to the given screens, each with its own visual effects.
         * When both screens use identical effects the picture is processed once and set in a single call.
         */
        private fun applyUriToConfigs(context: Context, uri: Uri, configs: List<WallpaperConfigEntity>): Boolean {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val first = configs.first()

            fun process(config: WallpaperConfigEntity): Bitmap? = processBitmap(
                context = context,
                uri = uri,
                applyGradient = config.applyGradient,
                applyGrayscale = config.applyGrayscale,
                cropAlignment = config.cropAlignment,
                isScrollable = config.isScrollable,
                blurRadius = config.blurRadius
            )

            if (configs.size > 1 && configs.all { sameLook(it, first) }) {
                val bitmap = process(first) ?: return false
                wallpaperManager.setBitmap(
                    bitmap, null, false,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )
                return true
            }

            var ok = false
            for (config in configs) {
                val bitmap = process(config) ?: continue
                val flag = if (config.screenType == "LOCK") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                wallpaperManager.setBitmap(bitmap, null, false, flag)
                ok = true
            }
            return ok
        }

        /**
         * Applies a specific selected URI as wallpaper immediately with the config's visual effects (blur, gradient, grayscale, alignment).
         */
        suspend fun applySpecificUriWallpaper(
            context: Context,
            config: WallpaperConfigEntity,
            uri: Uri,
            customScale: Float = 1.0f,
            customOffsetX: Float = 0f,
            customOffsetY: Float = 0f
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val bitmap = processBitmap(
                    context = context,
                    uri = uri,
                    applyGradient = config.applyGradient,
                    applyGrayscale = config.applyGrayscale,
                    cropAlignment = config.cropAlignment,
                    isScrollable = config.isScrollable,
                    blurRadius = config.blurRadius,
                    customScale = customScale,
                    customOffsetX = customOffsetX,
                    customOffsetY = customOffsetY
                )
                if (bitmap != null) {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val flag = if (config.screenType == "LOCK") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                    wallpaperManager.setBitmap(bitmap, null, false, flag)
                    Log.i("WallpaperWorker", "Successfully applied specific wallpaper for ${config.screenType} with blur=${config.blurRadius}")
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Failed applying specific wallpaper: ${e.message}", e)
                false
            }
        }

        fun scheduleWallpaperWork(context: Context, config: WallpaperConfigEntity) {
            val workManager = WorkManager.getInstance(context)
            val workName = "AutoWallpaperWork_${config.screenType}"
            
            if (!config.isEnabled) {
                workManager.cancelUniqueWork(workName)
                return
            }
            
            val data = Data.Builder().putString("screenType", config.screenType).build()

            if (config.useSchedule && config.scheduledTime != null) {
                // Scheduled exact time
                val parts = config.scheduledTime.split(":")
                val targetHour = parts[0].toInt()
                val targetMin = parts[1].toInt()
                
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                }
                
                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                
                val delayMs = target.timeInMillis - now.timeInMillis
                
                val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()
                    
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            } else {
                // Interval based
                if (config.intervalMinutes >= 15) {
                    val request = PeriodicWorkRequestBuilder<WallpaperWorker>(config.intervalMinutes.toLong(), TimeUnit.MINUTES)
                        .setInputData(data)
                        .build()
                    workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
                } else {
                    // Less than 15 mins (WorkManager minimum) -> Use OneTime chaining
                    val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                        .setInitialDelay(config.intervalMinutes.toLong(), TimeUnit.MINUTES)
                        .setInputData(data)
                        .build()
                    workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
                }
            }
        }
    }
}
