package com.example.service.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class SkyLiveWallpaperService : WallpaperService() {

    private val tag = "SkyLiveWallpaper"
    companion object {
        val activeEngines = java.util.concurrent.CopyOnWriteArraySet<SkyLiveWallpaperService.LiveWallpaperEngine>()
    }

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            activeEngines.forEach { it.onWallpaperConfigChanged() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val filter = IntentFilter("com.example.LIVE_WALLPAPER_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(wallpaperChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(wallpaperChangeReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to register wallpaperChangeReceiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wallpaperChangeReceiver)
        } catch (_: Exception) {}
    }

    override fun onCreateEngine(): Engine {
        val engine = LiveWallpaperEngine()
        activeEngines.add(engine)
        return engine
    }

    inner class LiveWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private var config: LiveWallpaperConfig = LiveWallpaperConfig()

        fun onWallpaperConfigChanged() {
            handler.post {
                loadConfig()
                restartEngine()
            }
        }

        // Video components
        private var mediaPlayer: MediaPlayer? = null
        private var glRenderer: VideoGlRenderer? = null
        private val drawLock = Any()

        private fun initGlRenderer(): VideoGlRenderer? {
            if (glRenderer != null && glRenderer!!.isInitialized) {
                return glRenderer
            }
            if (surfaceHolder.surface?.isValid != true) return null
            val r = VideoGlRenderer(surfaceHolder, config)
            return if (r.initialize()) {
                r.updateDimensions(surfaceWidth, surfaceHeight)
                glRenderer = r
                r
            } else {
                r.release()
                null
            }
        }

        // GIF components (ImageDecoder-based for full frame fidelity & hardware memory management)
        private var animatedDrawable: Drawable? = null
        private var fallbackBitmap: Bitmap? = null
        private var fallbackToPreset = false

        // Dimensions
        private var surfaceWidth = 1080
        private var surfaceHeight = 1920
        private var isVisibleState = false

        // Paint with color filter and high-quality filtering
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        private val drawFilter = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        // Preallocated reusable objects for zero-allocation rendering loop
        private var activeColorFilter: ColorFilter? = null
        private val reusablePresetPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        private val reusableStarPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            color = Color.WHITE
            isAntiAlias = true
            isDither = true
        }
        private val reusableMatrixPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
            textSize = 28f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            isSubpixelText = true
            isDither = true
        }
        private val reusableAuroraPaths = Array(4) { Path() }
        private val matrixChars = "0123456789ABCDEF$#@*!?".toCharArray()
        private var lastVideoCheckTime = 0L

        // Parallax and touch
        private var xOffsetFrac = 0.5f
        private var touchX = -1f
        private var touchY = -1f
        private var touchIntensity = 0f

        // Procedural animation state
        private var animTime = 0f
        private var lastFrameTime = 0L

        private fun updateDeltaTime(): Float {
            val now = SystemClock.uptimeMillis()
            if (lastFrameTime == 0L) lastFrameTime = now
            val dt = ((now - lastFrameTime) / 1000f).coerceIn(0.005f, 0.1f)
            lastFrameTime = now
            return dt
        }

        private val loopRunnable = object : Runnable {
            override fun run() {
                if (!isVisibleState) return
                val frameStart = SystemClock.uptimeMillis()
                try {
                    drawFrame()
                } catch (t: Throwable) {
                    Log.e(tag, "drawFrame loop error: ${t.message}")
                } finally {
                    if (isVisibleState) {
                        handler.removeCallbacks(this)
                        val elapsed = SystemClock.uptimeMillis() - frameStart
                        val targetInterval = 20L // ~50 FPS target
                        val nextDelay = (targetInterval - elapsed).coerceIn(4L, 24L)
                        handler.postDelayed(this, nextDelay)
                    }
                }
            }
        }

        private var lastVideoPosition = -1
        private var lastPositionChangeTime = 0L

        private val videoWatchdogRunnable = object : Runnable {
            override fun run() {
                if (!isVisibleState || config.mediaType != LiveMediaType.VIDEO || fallbackToPreset) return

                try {
                    val mp = mediaPlayer
                    val now = SystemClock.uptimeMillis()

                    if (mp == null) {
                        Log.w(tag, "Watchdog detected null mediaPlayer, restarting video")
                        startVideo()
                    } else {
                        val isPlaying = try { mp.isPlaying } catch (_: Exception) { false }
                        val currentPos = try { mp.currentPosition } catch (_: Exception) { -1 }
                        val duration = try { mp.duration } catch (_: Exception) { 0 }

                        if (!isPlaying) {
                            Log.w(tag, "Watchdog detected paused video, starting")
                            try {
                                mp.start()
                                glRenderer?.requestRender(force = true)
                            } catch (_: Exception) {
                                startVideo()
                            }
                        } else if (duration > 1000) {
                            if (currentPos == lastVideoPosition) {
                                if (now - lastPositionChangeTime > 2500) {
                                    Log.w(tag, "Watchdog: video playback frozen at $currentPos ms, recovering...")
                                    try {
                                        mp.seekTo(0)
                                        mp.start()
                                        glRenderer?.requestRender(force = true)
                                    } catch (_: Exception) {
                                        startVideo()
                                    }
                                    lastPositionChangeTime = now
                                }
                            } else {
                                lastVideoPosition = currentPos
                                lastPositionChangeTime = now
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(tag, "Watchdog error: ${t.message}")
                } finally {
                    if (isVisibleState && config.mediaType == LiveMediaType.VIDEO && !fallbackToPreset) {
                        handler.removeCallbacks(this)
                        handler.postDelayed(this, 1200)
                    }
                }
            }
        }

        private fun startVideoWatchdog() {
            handler.removeCallbacks(videoWatchdogRunnable)
            lastVideoPosition = -1
            lastPositionChangeTime = SystemClock.uptimeMillis()
            handler.postDelayed(videoWatchdogRunnable, 1200)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            surfaceHolder.setFormat(PixelFormat.RGBA_8888)
            val prefs = getSharedPreferences("sky_live_wallpaper_prefs", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            loadConfig()
        }

        override fun onDestroy() {
            super.onDestroy()
            activeEngines.remove(this)
            val prefs = getSharedPreferences("sky_live_wallpaper_prefs", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            stopAll()
            glRenderer?.release()
            glRenderer = null
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "active_live_config") {
                handler.post {
                    loadConfig()
                    restartEngine()
                }
            }
        }

        private fun loadConfig() {
            config = LiveWallpaperManager.getActiveConfig(this@SkyLiveWallpaperService)
            updatePaintFilter()
        }

        private fun updatePaintFilter() {
            val cm = ColorMatrix(LiveWallpaperManager.getColorMatrix(config.colorFilter))
            val filter = if (config.colorFilter != LiveColorFilter.NONE) ColorMatrixColorFilter(cm) else null
            activeColorFilter = filter
            paint.colorFilter = filter
            reusablePresetPaint.colorFilter = filter
            reusableMatrixPaint.colorFilter = filter
            if (config.blurRadius > 0) {
                try {
                    val r = (config.blurRadius.toFloat() * 1.5f).coerceAtLeast(1f)
                    paint.maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
                } catch (_: Exception) {
                    paint.maskFilter = null
                }
            } else {
                paint.maskFilter = null
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            holder.setFormat(PixelFormat.RGBA_8888)
            // REMOVED: initGlRenderer() - Do not bind EGL blindly!
            startMedia()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            glRenderer?.updateDimensions(width, height)
            // Force Initial Draw: Trigger an immediate frame render inside onSurfaceChanged
            // so the wallpaper doesn't wait for a delayed thread tick to appear.
            if (holder.surface?.isValid == true) {
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopAll()
            glRenderer?.release()
            glRenderer = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            if (visible) {
                // Securely re-fetch the active SurfaceHolder and resume
                val holder = surfaceHolder
                if (holder?.surface?.isValid == true) {
                    if (config.mediaType == LiveMediaType.VIDEO && !fallbackToPreset) {
                        if (mediaPlayer == null) {
                            startVideo()
                        } else {
                            resumeMedia()
                        }
                    } else if (config.mediaType == LiveMediaType.GIF) {
                        if (animatedDrawable == null && fallbackBitmap == null) {
                            startGif()
                        } else {
                            resumeMedia()
                        }
                    } else {
                        resumeMedia()
                    }
                    // Force immediate initial draw
                    drawFrame()
                }
            } else {
                pauseMedia()
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            xOffsetFrac = xOffset
            glRenderer?.setOffset(xOffset)
            if (isVisibleState && (config.mediaType != LiveMediaType.VIDEO || fallbackToPreset)) {
                drawFrame()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                touchX = event.x
                touchY = event.y
                touchIntensity = 1.0f
            }
        }

        private fun startMedia() {
            stopAll()

            // Unbind OpenGL if we are switching to Canvas drawing
            if (config.mediaType != LiveMediaType.VIDEO) {
                glRenderer?.release()
                glRenderer = null
            }

            when (config.mediaType) {
                LiveMediaType.VIDEO -> {
                    startVideo()
                }
                LiveMediaType.GIF -> {
                    startGif()
                }
                LiveMediaType.PRESET_AURORA,
                LiveMediaType.PRESET_NEBULA,
                LiveMediaType.PRESET_MATRIX -> {
                    startPreset()
                }
            }
        }

        private fun restartEngine() {
            if (surfaceHolder.surface?.isValid == true) {
                if (config.mediaType == LiveMediaType.VIDEO) {
                    fallbackToPreset = false
                }
                startMedia()
                if (isVisibleState) {
                    resumeMedia()
                }
            }
        }

        private fun startVideo() {
            val path = config.mediaPath
            if (path.isNullOrEmpty()) {
                Log.w(tag, "Video path is null or empty, starting preset")
                fallbackToPreset = true
                glRenderer?.release()
                glRenderer = null
                startPreset()
                return
            }

            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(tag, "Video file does not exist or is empty: $path, starting preset")
                fallbackToPreset = true
                glRenderer?.release()
                glRenderer = null
                startPreset()
                return
            }

            fallbackToPreset = false

            val renderer = initGlRenderer()
            if (renderer == null || renderer.videoSurface == null) {
                Log.e(tag, "Failed to initialize VideoGlRenderer for video playback")
                fallbackToPreset = true
                glRenderer?.release()
                glRenderer = null
                startPreset()
                return
            }

            renderer.updateConfig(config)
            renderer.updateDimensions(surfaceWidth, surfaceHeight)

            // Extract metadata rotation and dimensions
            var videoRotation = 0
            var rawVideoW = 1080
            var rawVideoH = 1920
            try {
                val fisMeta = java.io.FileInputStream(file)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fisMeta.fd)
                fisMeta.close()
                videoRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                rawVideoW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                rawVideoH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                retriever.release()
            } catch (e: Exception) {
                Log.w(tag, "Failed extracting video metadata: ${e.message}")
            }
            renderer.setVideoSize(rawVideoW, rawVideoH, videoRotation)

            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                mediaPlayer?.release()
                mediaPlayer = null

                val fis = java.io.FileInputStream(file)
                val player = MediaPlayer().apply {
                    setDataSource(fis.fd)
                    fis.close()
                    isLooping = true
                    setSurface(renderer.videoSurface)

                    val vol = if (config.isMuted) 0f else 0.5f
                    setVolume(vol, vol)

                    setOnVideoSizeChangedListener { _, width, height ->
                        if (width > 0 && height > 0) {
                            rawVideoW = width
                            rawVideoH = height
                            renderer.setVideoSize(width, height, videoRotation)
                        }
                    }

                    setOnCompletionListener { mp ->
                        try {
                            mp.seekTo(0)
                            mp.start()
                            renderer.requestRender(force = true)
                        } catch (_: Exception) {
                            startVideo()
                        }
                    }

                    setOnSeekCompleteListener { mp ->
                        try {
                            if (!mp.isPlaying) mp.start()
                            renderer.requestRender(force = true)
                        } catch (_: Exception) {}
                    }

                    setOnPreparedListener { mp ->
                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                            rawVideoW = mp.videoWidth
                            rawVideoH = mp.videoHeight
                            renderer.setVideoSize(mp.videoWidth, mp.videoHeight, videoRotation)
                        }
                        // Only set PlaybackParams when speed differs from normal (1.0x).
                        // Calling playbackParams on 1.0x on muted/silent videos causes NuPlayer
                        // to fall back to software MediaSync clock resulting in severe slow-motion.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kotlin.math.abs(config.playbackSpeed - 1.0f) >= 0.05f) {
                            try {
                                val params = PlaybackParams().apply {
                                    speed = config.playbackSpeed
                                    pitch = 1.0f
                                    audioFallbackMode = PlaybackParams.AUDIO_FALLBACK_MODE_MUTE
                                }
                                mp.playbackParams = params
                            } catch (_: Exception) {}
                        }
                        try {
                            mp.start()
                            renderer.requestRender(force = true)
                        } catch (e: Exception) {
                            Log.e(tag, "mp.start error: ${e.message}")
                        }
                        startVideoWatchdog()
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                        handler.postDelayed({
                            if (config.mediaType == LiveMediaType.VIDEO && !fallbackToPreset) {
                                startVideo()
                            }
                        }, 600)
                        true
                    }

                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                Log.e(tag, "Error setting up MediaPlayer: ${e.message}", e)
                fallbackToPreset = true
                glRenderer?.release()
                glRenderer = null
                startPreset()
            }
        }

        private fun startGif() {
            val path = config.mediaPath
            if (path.isNullOrEmpty() || !File(path).exists()) {
                fallbackToPreset = true
                startPreset()
                return
            }

            fallbackToPreset = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animatedDrawable is AnimatedImageDrawable) {
                try {
                    (animatedDrawable as AnimatedImageDrawable).stop()
                } catch (_: Exception) {}
            }
            animatedDrawable = null
            fallbackBitmap?.recycle()
            fallbackBitmap = null

            try {
                val file = File(path)

                // 1. Primary modern Android ImageDecoder API for full frame fidelity & hardware buffer efficiency
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val source = ImageDecoder.createSource(file)
                        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                            // Use hardware-backed graphic buffers for maximum fidelity and memory efficiency
                            decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                            decoder.isMutableRequired = false
                            try {
                                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            } catch (_: Exception) {}
                            val srcW = info.size.width
                            val srcH = info.size.height
                            // Decode ONCE at the final on-screen size so the decoder (smooth resampling)
                            // does the scaling, instead of the canvas stretching tiny frames with nearest-neighbour.
                            val dm = android.content.res.Resources.getSystem().displayMetrics
                            val sw = minOf(dm.widthPixels, dm.heightPixels).toFloat()
                            val sh = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
                            val sideways = config.rotationDegrees == 90 || config.rotationDegrees == 270
                            val boxW = if (sideways) sh else sw
                            val boxH = if (sideways) sw else sh
                            val fit = when (config.cropMode) {
                                LiveCropMode.FIT -> minOf(boxW / srcW, boxH / srcH)
                                LiveCropMode.LEFT, LiveCropMode.RIGHT -> boxH / srcH
                                LiveCropMode.STRETCH -> 1f
                                else -> maxOf(boxW / srcW, boxH / srcH)
                            }
                            // Cap at 4096px safety ceiling; user zoom is still applied on the canvas
                            val f = minOf(fit, 4096f / srcW, 4096f / srcH)
                            val targetW = (srcW * f).toInt().coerceAtLeast(1)
                            val targetH = (srcH * f).toInt().coerceAtLeast(1)
                            if (targetW != srcW || targetH != srcH) {
                                try { decoder.setTargetSize(targetW, targetH) } catch (_: Exception) {}
                            }
                        }
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.callback = object : Drawable.Callback {
                                override fun invalidateDrawable(who: Drawable) {
                                    if (isVisibleState) {
                                        handler.removeCallbacks(loopRunnable)
                                        handler.post(loopRunnable)
                                    }
                                }
                                override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                    handler.postAtTime(what, who, `when`)
                                }
                                override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                    handler.removeCallbacks(what, who)
                                }
                            }
                            drawable.start()
                            animatedDrawable = drawable
                        } else if (drawable is BitmapDrawable) {
                            fallbackBitmap = drawable.bitmap
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "ImageDecoder decodeDrawable error: ${e.message}")
                    }
                }

                // 2. Fallback to ImageDecoder.decodeBitmap for static / single-frame files
                if (animatedDrawable == null && fallbackBitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val source = ImageDecoder.createSource(file)
                        fallbackBitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                            decoder.isMutableRequired = false
                            try {
                                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "ImageDecoder decodeBitmap error: ${e.message}")
                    }
                }

                // 3. Fallback to full 32-bit ARGB_8888 BitmapFactory
                if (animatedDrawable == null && fallbackBitmap == null) {
                    try {
                        val decodeOpts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inDither = true
                        }
                        fallbackBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                    } catch (_: Exception) {}
                }

                if (animatedDrawable != null || fallbackBitmap != null) {
                    if (isVisibleState) {
                        handler.post(loopRunnable)
                    }
                } else {
                    Log.w(tag, "Failed to decode GIF from $path, fallback to preset")
                    fallbackToPreset = true
                    startPreset()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error starting GIF wallpaper: ${e.message}", e)
                fallbackToPreset = true
                startPreset()
            }
        }

        private fun startPreset() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animatedDrawable is AnimatedImageDrawable) {
                try {
                    (animatedDrawable as AnimatedImageDrawable).stop()
                } catch (_: Exception) {}
            }
            animatedDrawable = null
            fallbackBitmap?.recycle()
            fallbackBitmap = null
            if (isVisibleState) {
                handler.post(loopRunnable)
            }
        }

        private fun pauseMedia() {
            handler.removeCallbacks(loopRunnable)
            handler.removeCallbacks(videoWatchdogRunnable)
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (_: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animatedDrawable is AnimatedImageDrawable) {
                try {
                    (animatedDrawable as AnimatedImageDrawable).stop()
                } catch (_: Exception) {}
            }
        }

        private fun resumeMedia() {
            if (config.mediaType == LiveMediaType.VIDEO && !fallbackToPreset) {
                try {
                    glRenderer?.ensureSurfaceValid()
                    if (mediaPlayer == null) {
                        startVideo()
                    } else {
                        if (!mediaPlayer!!.isPlaying) {
                            mediaPlayer!!.start()
                        }
                        glRenderer?.requestRender(force = true)
                    }
                } catch (_: Exception) {
                    startVideo()
                }
                startVideoWatchdog()
            } else if (config.mediaType == LiveMediaType.GIF) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animatedDrawable is AnimatedImageDrawable) {
                    try {
                        val ad = animatedDrawable as AnimatedImageDrawable
                        if (!ad.isRunning) {
                            ad.start()
                        }
                    } catch (_: Exception) {}
                }
                handler.removeCallbacks(loopRunnable)
                handler.post(loopRunnable)
            } else {
                handler.removeCallbacks(loopRunnable)
                handler.post(loopRunnable)
            }
        }

        private fun stopAll() {
            handler.removeCallbacks(loopRunnable)
            handler.removeCallbacks(videoWatchdogRunnable)
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animatedDrawable is AnimatedImageDrawable) {
                try {
                    (animatedDrawable as AnimatedImageDrawable).stop()
                } catch (_: Exception) {}
            }
            animatedDrawable = null
            fallbackBitmap?.recycle()
            fallbackBitmap = null
            fallbackToPreset = false
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return

            // If playing video normally without error, GL renderer owns the surface and MediaPlayer pushes frames.
            if (config.mediaType == LiveMediaType.VIDEO && !fallbackToPreset) {
                val r = glRenderer
                if (r != null && r.isInitialized) {
                    r.requestRender(force = true)
                    return
                }
            }

            // Ensure thread-safe locking on the SurfaceHolder canvas when drawing
            synchronized(drawLock) {
                if (!holder.surface.isValid) return
                var canvas: Canvas? = null
                try {
                    canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            holder.lockHardwareCanvas()
                        } catch (_: Exception) {
                            holder.lockCanvas()
                        }
                    } else {
                        holder.lockCanvas()
                    }

                    // Safe fallback if hardware canvas failed silently
                    if (canvas == null) {
                        canvas = holder.lockCanvas()
                    }

                    if (canvas != null) {
                        canvas.drawFilter = drawFilter
                        if (fallbackToPreset) {
                            drawAurora(canvas)
                        } else {
                            when (config.mediaType) {
                                LiveMediaType.GIF -> drawGif(canvas)
                                LiveMediaType.PRESET_AURORA -> drawAurora(canvas)
                                LiveMediaType.PRESET_NEBULA -> drawNebula(canvas)
                                LiveMediaType.PRESET_MATRIX -> drawMatrix(canvas)
                                LiveMediaType.VIDEO -> drawAurora(canvas)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "drawFrame canvas error: ${e.message}")
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            Log.w(tag, "unlockCanvasAndPost error: ${e.message}")
                        }
                    }
                }
            }
        }

        private fun drawGif(canvas: Canvas) {
            val ad = animatedDrawable
            val fb = fallbackBitmap

            // If no GIF frames could be decoded, seamlessly fallback to procedural preset
            if (ad == null && (fb == null || fb.isRecycled)) {
                drawAurora(canvas)
                return
            }

            val canvasW = canvas.width.toFloat()
            val canvasH = canvas.height.toFloat()
            canvas.drawColor(Color.rgb(12, 16, 24))

            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val screenRatio = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()

            // Height is always 100% of the canvas; single-screen width is derived from display aspect ratio
            val singlePageW = minOf(canvasW, canvasH * screenRatio)
            val singlePageH = canvasH

            val isSideways = config.rotationDegrees == 90 || config.rotationDegrees == 270
            val childW = if (isSideways) singlePageH else singlePageW
            val childH = if (isSideways) singlePageW else singlePageH

            // Full intrinsic media dimensions from ImageDecoder
            val mediaW = when {
                ad != null -> (if (ad.intrinsicWidth > 0) ad.intrinsicWidth else singlePageW.toInt()).toFloat()
                fb != null && !fb.isRecycled -> fb.width.toFloat()
                else -> childW
            }
            val mediaH = when {
                ad != null -> (if (ad.intrinsicHeight > 0) ad.intrinsicHeight else singlePageH.toInt()).toFloat()
                fb != null && !fb.isRecycled -> fb.height.toFloat()
                else -> childH
            }

            val scaleX: Float
            val scaleY: Float
            val posX: Float
            val posY: Float

            when (config.cropMode) {
                LiveCropMode.FILL -> {
                    val s = maxOf(childW / mediaW, childH / mediaH)
                    scaleX = s
                    scaleY = s
                    posX = (childW - mediaW * s) / 2f
                    posY = (childH - mediaH * s) / 2f
                }
                LiveCropMode.FIT -> {
                    val s = minOf(childW / mediaW, childH / mediaH)
                    scaleX = s
                    scaleY = s
                    posX = (childW - mediaW * s) / 2f
                    posY = (childH - mediaH * s) / 2f
                }
                LiveCropMode.LEFT -> {
                    val s = childH / mediaH
                    scaleX = s
                    scaleY = s
                    posX = 0f
                    posY = (childH - mediaH * s) / 2f
                }
                LiveCropMode.RIGHT -> {
                    val s = childH / mediaH
                    scaleX = s
                    scaleY = s
                    posX = childW - mediaW * s
                    posY = (childH - mediaH * s) / 2f
                }
                LiveCropMode.STRETCH -> {
                    scaleX = childW / mediaW
                    scaleY = childH / mediaH
                    posX = 0f
                    posY = 0f
                }
            }

            val destW = (mediaW * scaleX).toInt().coerceAtLeast(1)
            val destH = (mediaH * scaleY).toInt().coerceAtLeast(1)

            val extraWidth = (canvasW - singlePageW).coerceAtLeast(0f)
            val parallaxShift = -extraWidth * (xOffsetFrac - 0.5f)
            val centerX = Math.round((canvasW / 2f) + parallaxShift + config.offsetX).toFloat()
            val centerY = Math.round((canvasH / 2f) + config.offsetY).toFloat()

            canvas.save()
            canvas.translate(centerX, centerY)
            val userScale = config.scaleFactor.coerceIn(0.2f, 5.0f)
            canvas.scale(userScale, userScale)
            canvas.rotate(config.rotationDegrees.toFloat())
            canvas.translate(Math.round(-childW / 2f).toFloat(), Math.round(-childH / 2f).toFloat())
            canvas.translate(Math.round(posX).toFloat(), Math.round(posY).toFloat())

            val cm = LiveWallpaperManager.getColorMatrix(config.colorFilter)
            val colorFilter = if (config.colorFilter != LiveColorFilter.NONE) {
                ColorMatrixColorFilter(ColorMatrix(cm))
            } else null

            if (ad != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ad is AnimatedImageDrawable) {
                    try {
                        if (!ad.isRunning) {
                            ad.start()
                        }
                    } catch (_: Exception) {}
                }
                ad.colorFilter = colorFilter
                ad.isFilterBitmap = true // bilinear smoothing instead of blocky nearest-neighbour
                // Render full frame data directly into destination bounds for native display sharpness
                ad.setBounds(0, 0, destW, destH)
                ad.draw(canvas)
            } else if (fb != null && !fb.isRecycled) {
                paint.colorFilter = colorFilter
                val srcRect = Rect(0, 0, fb.width, fb.height)
                val dstRect = Rect(0, 0, destW, destH)
                canvas.drawBitmap(fb, srcRect, dstRect, paint)
            }

            canvas.restore()
        }

        private fun drawAurora(canvas: Canvas) {
            val dt = updateDeltaTime()
            val speedMult = if (config.playbackSpeed > 0f) config.playbackSpeed else 1.0f
            // Fluid, dynamic flowing wave: completes a wave oscillation every ~3.5s at normal speed
            animTime += dt * 1.8f * speedMult
            if (touchIntensity > 0f) touchIntensity = (touchIntensity - 0.05f).coerceAtLeast(0f)

            canvas.drawColor(Color.rgb(10, 15, 30))
            canvas.save()

            val canvasW = canvas.width.toFloat()
            val canvasH = canvas.height.toFloat()

            val physicalH = canvasH
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val screenRatio = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
            val physicalW = minOf(canvasW, canvasH * screenRatio)

            val extraWidth = (canvasW - physicalW).coerceAtLeast(0f)
            val parallaxShift = -extraWidth * (xOffsetFrac - 0.5f)
            val centerX = (canvasW / 2f) + parallaxShift + config.offsetX
            val centerY = (canvasH / 2f) + config.offsetY

            canvas.translate(centerX, centerY)
            val userScale = config.scaleFactor.coerceIn(0.2f, 5.0f)
            canvas.scale(userScale, userScale)
            canvas.rotate(config.rotationDegrees.toFloat())
            canvas.translate(-physicalW / 2f, -physicalH / 2f)

            // Draw 4 flowing organic glowing bands based on physical display bounds
            val colors = intArrayOf(
                Color.argb(140, 30, 220, 160),
                Color.argb(120, 20, 140, 255),
                Color.argb(100, 180, 50, 255),
                Color.argb(130, 0, 230, 200)
            )

            for (i in 0 until 4) {
                val path = reusableAuroraPaths[i]
                path.rewind()
                val baseY = physicalH * (0.28f + i * 0.16f)
                val waveOffset = animTime + i * 1.5f + (xOffsetFrac * 2f)

                path.moveTo(0f, physicalH)
                path.lineTo(0f, baseY)

                var x = 0f
                val step = physicalW / 80f
                while (x <= physicalW + step) {
                    val nx = x / physicalW
                    val y = baseY +
                            sin(nx * 4f + waveOffset) * (physicalH * 0.05f) +
                            cos(nx * 7f - waveOffset * 0.8f) * (physicalH * 0.025f) +
                            if (touchIntensity > 0f) sin((x - touchX) * 0.05f) * (physicalH * 0.02f) * touchIntensity else 0f
                    path.lineTo(x, y)
                    x += step
                }

                path.lineTo(physicalW, physicalH)
                path.close()

                val grad = LinearGradient(
                    0f, baseY - (physicalH * 0.06f), 0f, baseY + (physicalH * 0.12f),
                    colors[i], Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                reusablePresetPaint.shader = grad
                canvas.drawPath(path, reusablePresetPaint)
            }

            // Draw subtle starry glow
            for (s in 0 until 24) {
                val sx = ((s * 137.5f) % physicalW)
                val sy = ((s * 257.3f) % (physicalH * 0.7f))
                val alpha = (100 + sin(animTime * 2f + s) * 80).toInt().coerceIn(20, 255)
                reusableStarPaint.alpha = alpha
                canvas.drawCircle(sx, sy, 2.5f + (s % 3), reusableStarPaint)
            }

            canvas.restore()
        }

        private fun drawNebula(canvas: Canvas) {
            val dt = updateDeltaTime()
            val speedMult = if (config.playbackSpeed > 0f) config.playbackSpeed else 1.0f
            // Fluid cosmic rotation and pulsing gas at normal speed
            animTime += dt * 1.2f * speedMult
            canvas.drawColor(Color.rgb(8, 6, 20))
            canvas.save()

            val canvasW = canvas.width.toFloat()
            val canvasH = canvas.height.toFloat()

            val physicalH = canvasH
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val screenRatio = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
            val physicalW = minOf(canvasW, canvasH * screenRatio)

            val extraWidth = (canvasW - physicalW).coerceAtLeast(0f)
            val parallaxShift = -extraWidth * (xOffsetFrac - 0.5f)
            val centerX = (canvasW / 2f) + parallaxShift + config.offsetX
            val centerY = (canvasH / 2f) + config.offsetY

            canvas.translate(centerX, centerY)
            val userScale = config.scaleFactor.coerceIn(0.2f, 5.0f)
            canvas.scale(userScale, userScale)
            canvas.rotate(config.rotationDegrees.toFloat())
            canvas.translate(-physicalW / 2f, -physicalH / 2f)

            val cx = physicalW / 2f + (xOffsetFrac - 0.5f) * (physicalW * 0.1f)
            val cy = physicalH / 2f

            // Radial cosmic gas
            val rad1 = physicalW * 0.85f
            val grad1 = RadialGradient(
                cx + sin(animTime) * (physicalW * 0.06f), cy + cos(animTime * 0.8f) * (physicalH * 0.05f), rad1,
                intArrayOf(Color.argb(160, 220, 50, 180), Color.argb(80, 50, 20, 160), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            reusablePresetPaint.shader = grad1
            canvas.drawCircle(cx, cy, rad1, reusablePresetPaint)

            val rad2 = physicalW * 0.7f
            val grad2 = RadialGradient(
                cx - cos(animTime * 0.7f) * (physicalW * 0.08f), cy - sin(animTime * 0.9f) * (physicalH * 0.04f), rad2,
                intArrayOf(Color.argb(140, 20, 180, 255), Color.argb(60, 10, 80, 180), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            reusablePresetPaint.shader = grad2
            canvas.drawCircle(cx, cy, rad2, reusablePresetPaint)

            canvas.restore()
        }

        private fun drawMatrix(canvas: Canvas) {
            val dt = updateDeltaTime()
            val speedMult = if (config.playbackSpeed > 0f) config.playbackSpeed else 1.0f
            // Brisk matrix digital code stream at normal speed
            animTime += dt * 32f * speedMult
            canvas.drawColor(Color.rgb(5, 12, 8))
            canvas.save()

            val canvasW = canvas.width.toFloat()
            val canvasH = canvas.height.toFloat()

            val physicalH = canvasH
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val screenRatio = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
            val physicalW = minOf(canvasW, canvasH * screenRatio)

            val extraWidth = (canvasW - physicalW).coerceAtLeast(0f)
            val parallaxShift = -extraWidth * (xOffsetFrac - 0.5f)
            val centerX = (canvasW / 2f) + parallaxShift + config.offsetX
            val centerY = (canvasH / 2f) + config.offsetY

            canvas.translate(centerX, centerY)
            val userScale = config.scaleFactor.coerceIn(0.2f, 5.0f)
            canvas.scale(userScale, userScale)
            canvas.rotate(config.rotationDegrees.toFloat())
            canvas.translate(-physicalW / 2f, -physicalH / 2f)

            val colSpacing = 28f
            val cols = (physicalW / colSpacing).toInt().coerceAtLeast(1)

            for (col in 0 until cols) {
                val colX = col * colSpacing
                val colSpeed = ((col * 7) % 5 + 4) * 1.5f
                val headY = ((animTime * colSpeed + col * 70f) % (physicalH + 300f)) - 60f

                for (row in 0 until 14) {
                    val charY = headY - row * 26f
                    if (charY in -30f..physicalH) {
                        val charIdx = ((col * 13 + row * 7 + (animTime / 10).toInt()) % matrixChars.size).coerceIn(0, matrixChars.size - 1)
                        if (row == 0) {
                            reusableMatrixPaint.color = Color.WHITE
                        } else {
                            val alpha = ((14 - row) * 18).coerceIn(20, 240)
                            reusableMatrixPaint.color = Color.argb(alpha, 0, 255, 120)
                        }
                        reusableMatrixPaint.textSize = 24f
                        canvas.drawText(matrixChars, charIdx, 1, colX, charY, reusableMatrixPaint)
                    }
                }
            }

            canvas.restore()
        }
    }
}
