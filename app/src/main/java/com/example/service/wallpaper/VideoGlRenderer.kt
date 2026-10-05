package com.example.service.wallpaper

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Hardware-accelerated OpenGL ES 2.0 renderer that renders MediaPlayer video frames
 * as well as Canvas/Bitmap presets to a Wallpaper SurfaceHolder with support for:
 * - Arbitrary rotation (0, 90, 180, 270 degrees)
 * - Scaling and crop modes (Fill center-crop, Fit letterbox, Left align, Right align, Stretch)
 * - Real-time GPU color filters (Monochrome, Sepia, Invert, Vivid, Cool, High Contrast, Sunset)
 * - Direct 2D Bitmap rendering for presets/GIFs so holder.surface is NEVER locked in CPU mode.
 */
class VideoGlRenderer(
    private val holder: SurfaceHolder,
    private var config: LiveWallpaperConfig
) : SurfaceTexture.OnFrameAvailableListener {

    private val tag = "VideoGlRenderer"

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null

    // Video texture (OES)
    private var videoTextureId: Int = -1
    private var surfaceTexture: SurfaceTexture? = null
    var videoSurface: Surface? = null
        private set

    // Bitmap texture (2D) for presets / GIFs
    private var bitmapTextureId: Int = -1
    private var lastBitmapWidth: Int = -1
    private var lastBitmapHeight: Int = -1
    private var isContextMadeCurrent: Boolean = false

    // Video shader program (External OES)
    private var videoProgram: Int = 0
    private var aPositionHandle: Int = -1
    private var aTextureCoordHandle: Int = -1
    private var uMVPMatrixHandle: Int = -1
    private var uSTMatrixHandle: Int = -1
    private var uFilterTypeHandle: Int = -1
    private var uBlurRadiusHandle: Int = -1

    // Bitmap shader program (2D)
    private var bitmapProgram: Int = 0
    private var aBitmapPositionHandle: Int = -1
    private var aBitmapTextureCoordHandle: Int = -1
    private var uBitmapMVPMatrixHandle: Int = -1
    private var uBitmapTextureHandle: Int = -1

    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    private val bitmapMvpMatrix = FloatArray(16)

    private var viewWidth: Int = 1080
    private var viewHeight: Int = 1920
    private var rawVideoWidth: Int = 1080
    private var rawVideoHeight: Int = 1080
    private var videoRotation: Int = 0

    val visualVideoWidth: Int
        get() = if (videoRotation == 90 || videoRotation == 270) rawVideoHeight else rawVideoWidth

    val visualVideoHeight: Int
        get() = if (videoRotation == 90 || videoRotation == 270) rawVideoWidth else rawVideoHeight

    @Volatile
    private var frameAvailable = false
    @Volatile
    private var isRunning = true

    val isInitialized: Boolean
        get() = isRunning && eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE && videoSurface != null && videoSurface?.isValid == true

    // Quad coordinates for Video (matching SurfaceTexture's transform matrix)
    private val triangleVerticesData = floatArrayOf(
        // X, Y, Z, U, V
        0.0f, 0.0f, 0.0f, 0.0f, 1.0f,  // Top-Left (U=0, V=1)
        1.0f, 0.0f, 0.0f, 1.0f, 1.0f,  // Top-Right (U=1, V=1)
        0.0f, 1.0f, 0.0f, 0.0f, 0.0f,  // Bottom-Left (U=0, V=0)
        1.0f, 1.0f, 0.0f, 1.0f, 0.0f   // Bottom-Right (U=1, V=0)
    )
    private val triangleVertices: FloatBuffer = ByteBuffer.allocateDirect(
        triangleVerticesData.size * 4
    ).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(triangleVerticesData)
        position(0)
    }

    // Quad coordinates for 2D Bitmaps (standard UV coordinates)
    private val bitmapVerticesData = floatArrayOf(
        // X, Y, Z, U, V
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f,  // Top-Left
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f,  // Top-Right
        0.0f, 1.0f, 0.0f, 0.0f, 1.0f,  // Bottom-Left
        1.0f, 1.0f, 0.0f, 1.0f, 1.0f   // Bottom-Right
    )
    private val bitmapVertices: FloatBuffer = ByteBuffer.allocateDirect(
        bitmapVerticesData.size * 4
    ).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(bitmapVerticesData)
        position(0)
    }

    // Video Shaders (OES External Texture)
    private val vertexShaderSource = """
        precision highp float;
        uniform mat4 uMVPMatrix;
        uniform mat4 uSTMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #extension GL_OES_EGL_image_external : require
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform int uFilterType; // 0=None, 1=Grayscale, 2=Sepia, 3=Invert, 4=Vivid, 5=Cool, 6=Contrast, 7=Sunset
        uniform float uBlurRadius;

        void main() {
            vec4 color;
            if (uBlurRadius > 0.5) {
                float r = uBlurRadius * 0.0018;
                vec4 sum = texture2D(sTexture, vTextureCoord) * 0.16;
                vec2 d1 = vec2(r * 0.35, 0.0);
                vec2 d2 = vec2(0.0, r * 0.35);
                sum += texture2D(sTexture, vTextureCoord + d1) * 0.09;
                sum += texture2D(sTexture, vTextureCoord - d1) * 0.09;
                sum += texture2D(sTexture, vTextureCoord + d2) * 0.09;
                sum += texture2D(sTexture, vTextureCoord - d2) * 0.09;
                vec2 d3 = vec2(r * 0.65, r * 0.65);
                vec2 d4 = vec2(-r * 0.65, r * 0.65);
                sum += texture2D(sTexture, vTextureCoord + d3) * 0.06;
                sum += texture2D(sTexture, vTextureCoord - d3) * 0.06;
                sum += texture2D(sTexture, vTextureCoord + d4) * 0.06;
                sum += texture2D(sTexture, vTextureCoord - d4) * 0.06;
                vec2 d5 = vec2(r, 0.0);
                vec2 d6 = vec2(0.0, r);
                sum += texture2D(sTexture, vTextureCoord + d5) * 0.04;
                sum += texture2D(sTexture, vTextureCoord - d5) * 0.04;
                sum += texture2D(sTexture, vTextureCoord + d6) * 0.04;
                sum += texture2D(sTexture, vTextureCoord - d6) * 0.04;
                color = sum;
            } else {
                color = texture2D(sTexture, vTextureCoord);
            }

            if (uFilterType == 1) {
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                gl_FragColor = vec4(vec3(gray), color.a);
            } else if (uFilterType == 2) {
                float r = dot(color.rgb, vec3(0.393, 0.769, 0.189));
                float g = dot(color.rgb, vec3(0.349, 0.686, 0.168));
                float b = dot(color.rgb, vec3(0.272, 0.534, 0.131));
                gl_FragColor = vec4(clamp(vec3(r, g, b), 0.0, 1.0), color.a);
            } else if (uFilterType == 3) {
                gl_FragColor = vec4(vec3(1.0) - color.rgb, color.a);
            } else if (uFilterType == 4) {
                vec3 c = (color.rgb - 0.5) * 1.35 + 0.5;
                float gray = dot(c, vec3(0.299, 0.587, 0.114));
                vec3 sat = mix(vec3(gray), c, 1.4);
                gl_FragColor = vec4(clamp(sat, 0.0, 1.0), color.a);
            } else if (uFilterType == 5) {
                vec3 cool = color.rgb * vec3(0.8, 1.05, 1.35);
                gl_FragColor = vec4(clamp(cool, 0.0, 1.0), color.a);
            } else if (uFilterType == 6) {
                vec3 c = (color.rgb - 0.5) * 1.6 + 0.5;
                gl_FragColor = vec4(clamp(c, 0.0, 1.0), color.a);
            } else if (uFilterType == 7) {
                vec3 warm = color.rgb * vec3(1.35, 1.05, 0.75);
                gl_FragColor = vec4(clamp(warm, 0.0, 1.0), color.a);
            } else {
                gl_FragColor = color;
            }
        }
    """.trimIndent()

    // 2D Bitmap Shaders
    private val bitmapVertexShaderSource = """
        precision highp float;
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = aTextureCoord;
        }
    """.trimIndent()

    private val bitmapFragmentShaderSource = """
        precision highp float;
        varying vec2 vTextureCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTextureCoord);
        }
    """.trimIndent()

    fun initialize(): Boolean {
        try {
            initEGL()
            initGL()
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize GL renderer: ${e.message}", e)
            release()
            return false
        }
    }

    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }

        val attribList8888 = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        var chooseOk = EGL14.eglChooseConfig(eglDisplay, attribList8888, 0, configs, 0, configs.size, numConfigs, 0)
        if (!chooseOk || numConfigs[0] <= 0) {
            val attribList565 = intArrayOf(
                EGL14.EGL_RED_SIZE, 5,
                EGL14.EGL_GREEN_SIZE, 6,
                EGL14.EGL_BLUE_SIZE, 5,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            )
            chooseOk = EGL14.eglChooseConfig(eglDisplay, attribList565, 0, configs, 0, configs.size, numConfigs, 0)
        }
        if (!chooseOk || numConfigs[0] <= 0) {
            throw RuntimeException("eglChooseConfig failed")
        }
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext failed")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], holder.surface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun initGL() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // 1. External OES Texture for Video
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        videoTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(videoTextureId).apply {
            setOnFrameAvailableListener(this@VideoGlRenderer, Handler(Looper.getMainLooper()))
        }
        videoSurface = Surface(surfaceTexture)

        videoProgram = createProgram(vertexShaderSource, fragmentShaderSource)
        if (videoProgram == 0) {
            throw RuntimeException("Could not create video shader program")
        }

        aPositionHandle = GLES20.glGetAttribLocation(videoProgram, "aPosition")
        aTextureCoordHandle = GLES20.glGetAttribLocation(videoProgram, "aTextureCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(videoProgram, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(videoProgram, "uSTMatrix")
        uFilterTypeHandle = GLES20.glGetUniformLocation(videoProgram, "uFilterType")
        uBlurRadiusHandle = GLES20.glGetUniformLocation(videoProgram, "uBlurRadius")

        // 2. Standard 2D Texture for Canvas / Bitmap presets
        val bTextures = IntArray(1)
        GLES20.glGenTextures(1, bTextures, 0)
        bitmapTextureId = bTextures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        bitmapProgram = createProgram(bitmapVertexShaderSource, bitmapFragmentShaderSource)
        aBitmapPositionHandle = GLES20.glGetAttribLocation(bitmapProgram, "aPosition")
        aBitmapTextureCoordHandle = GLES20.glGetAttribLocation(bitmapProgram, "aTextureCoord")
        uBitmapMVPMatrixHandle = GLES20.glGetUniformLocation(bitmapProgram, "uMVPMatrix")
        uBitmapTextureHandle = GLES20.glGetUniformLocation(bitmapProgram, "uTexture")

        Matrix.setIdentityM(stMatrix, 0)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(bitmapMvpMatrix, 0)
    }

    fun updateDimensions(vWidth: Int, vHeight: Int) {
        this.viewWidth = if (vWidth > 0) vWidth else 1080
        this.viewHeight = if (vHeight > 0) vHeight else 1920
        updateMatrices()
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglConfig != null && holder.surface?.isValid == true) {
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, holder.surface, surfaceAttribs, 0)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "updateDimensions error: ${e.message}")
        }
        requestRender(force = true)
    }

    fun setVideoSize(rawW: Int, rawH: Int, rotation: Int = videoRotation) {
        if (rawW > 0 && rawH > 0) {
            this.rawVideoWidth = rawW
            this.rawVideoHeight = rawH
            this.videoRotation = rotation
            try {
                // Ensure SurfaceTexture buffer size matches raw video dimensions for full 1:1 hardware decoding fidelity
                surfaceTexture?.setDefaultBufferSize(rawW, rawH)
            } catch (_: Exception) {}
            updateMatrices()
            requestRender(force = true)
        }
    }

    fun updateConfig(newConfig: LiveWallpaperConfig) {
        this.config = newConfig
        updateMatrices()
        requestRender(force = true)
    }

    private var xOffsetFrac: Float = 0.5f

    fun setOffset(xOffset: Float) {
        this.xOffsetFrac = xOffset
        updateMatrices()
    }

    private val modelMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)

    private fun updateMatrices() {
        val screenW = if (viewWidth > 0) viewWidth.toFloat() else 1080f
        val screenH = if (viewHeight > 0) viewHeight.toFloat() else 1920f

        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val screenRatio = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val singlePageW = minOf(screenW, screenH * screenRatio)
        val singlePageH = screenH

        val isSideways = config.rotationDegrees == 90 || config.rotationDegrees == 270
        // Use full screen height and aspect-ratio singlePageW for scale and bounds calculation
        val childW = if (isSideways) singlePageH else singlePageW
        val childH = if (isSideways) singlePageW else singlePageH

        // Use rotation-aware visual dimensions so videos are not stretched, squashed, or zoomed
        val mW = if (visualVideoWidth > 0) visualVideoWidth.toFloat() else childW
        val mH = if (visualVideoHeight > 0) visualVideoHeight.toFloat() else childH

        val scaleX: Float
        val scaleY: Float
        val posX: Float
        val posY: Float

        when (config.cropMode) {
            LiveCropMode.FILL -> {
                val s = maxOf(childW / mW, childH / mH)
                scaleX = s
                scaleY = s
                posX = (childW - mW * s) / 2f
                posY = (childH - mH * s) / 2f
            }
            LiveCropMode.FIT -> {
                val s = minOf(childW / mW, childH / mH)
                scaleX = s
                scaleY = s
                posX = (childW - mW * s) / 2f
                posY = (childH - mH * s) / 2f
            }
            LiveCropMode.LEFT -> {
                val s = childH / mH
                scaleX = s
                scaleY = s
                posX = 0f
                posY = (childH - mH * s) / 2f
            }
            LiveCropMode.RIGHT -> {
                val s = childH / mH
                scaleX = s
                scaleY = s
                posX = childW - mW * s
                posY = (childH - mH * s) / 2f
            }
            LiveCropMode.STRETCH -> {
                scaleX = childW / mW
                scaleY = childH / mH
                posX = 0f
                posY = 0f
            }
        }

        val extraWidth = (screenW - singlePageW).coerceAtLeast(0f)
        val parallaxShift = -extraWidth * (xOffsetFrac - 0.5f)
        val centerX = (screenW / 2f) + parallaxShift + config.offsetX
        val centerY = (screenH / 2f) + config.offsetY

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, centerX, centerY, 0f)
        val userScale = config.scaleFactor.coerceIn(0.2f, 5.0f)
        Matrix.scaleM(modelMatrix, 0, userScale, userScale, 1.0f)
        Matrix.rotateM(modelMatrix, 0, config.rotationDegrees.toFloat(), 0f, 0f, 1f)
        Matrix.translateM(modelMatrix, 0, -childW / 2f, -childH / 2f, 0f)
        Matrix.translateM(modelMatrix, 0, posX, posY, 0f)
        Matrix.scaleM(modelMatrix, 0, mW * scaleX, mH * scaleY, 1.0f)

        Matrix.orthoM(projMatrix, 0, 0f, screenW, screenH, 0f, -1f, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelMatrix, 0)

        Matrix.orthoM(bitmapMvpMatrix, 0, 0f, 1f, 1f, 0f, -1f, 1f)
    }

    fun ensureSurfaceValid(): Boolean {
        if (!isRunning || eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT) return false
        if (holder.surface?.isValid != true) return false

        try {
            var surfaceRecreated = false
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                if (eglConfig == null) return false
                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, holder.surface, surfaceAttribs, 0)
                if (eglSurface == EGL14.EGL_NO_SURFACE) return false
                surfaceRecreated = true
                isContextMadeCurrent = false
            }

            if (surfaceRecreated || !isContextMadeCurrent) {
                val ok = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                isContextMadeCurrent = ok
                return ok
            }
            return true
        } catch (e: Exception) {
            Log.w(tag, "ensureSurfaceValid warning: ${e.message}")
            return false
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameAvailable = true
        requestRender()
    }

    fun requestRender(force: Boolean = false) {
        if (!isRunning || eglDisplay == EGL14.EGL_NO_DISPLAY) return
        if (holder.surface?.isValid != true) return

        try {
            if (!ensureSurfaceValid()) {
                return
            }

            if (frameAvailable) {
                try {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(stMatrix)
                    // If video rotation wasn't provided or differed, detect orientation from transform matrix
                    val isMatrixRotated = kotlin.math.abs(stMatrix[1]) > kotlin.math.abs(stMatrix[0])
                    val detectedRot = if (isMatrixRotated) 90 else 0
                    if (videoRotation == 0 && detectedRot != 0) {
                        videoRotation = detectedRot
                        updateMatrices()
                    }
                } catch (e: Exception) {
                    Log.w(tag, "updateTexImage warning: ${e.message}")
                } finally {
                    frameAvailable = false
                }
            } else if (!force) {
                return
            }

            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(videoProgram)

            triangleVertices.position(0)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, triangleVertices)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            triangleVertices.position(3)
            GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, triangleVertices)
            GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)

            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

            val filterCode = when (config.colorFilter) {
                LiveColorFilter.NONE -> 0
                LiveColorFilter.GRAYSCALE -> 1
                LiveColorFilter.SEPIA -> 2
                LiveColorFilter.INVERT -> 3
                LiveColorFilter.VIVID -> 4
                LiveColorFilter.COOL -> 5
                LiveColorFilter.CONTRAST -> 6
                LiveColorFilter.SUNSET -> 7
            }
            GLES20.glUniform1i(uFilterTypeHandle, filterCode)
            GLES20.glUniform1f(uBlurRadiusHandle, config.blurRadius.toFloat())

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            val swapped = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            if (!swapped) {
                val err = EGL14.eglGetError()
                if (err == EGL14.EGL_BAD_SURFACE || err == 0x300E /* EGL_CONTEXT_LOST */) {
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                        eglSurface = EGL14.EGL_NO_SURFACE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Render frame error: ${e.message}", e)
        }
    }

    /**
     * Renders a 2D Bitmap (Canvas preset, procedural animation, or decoded GIF frame)
     * onto the wallpaper surface using OpenGL ES. This ensures holder.surface NEVER
     * gets locked by lockCanvas() or CPU mode.
     */
    fun renderBitmap(bitmap: Bitmap) {
        if (!isRunning || eglDisplay == EGL14.EGL_NO_DISPLAY) return
        if (holder.surface?.isValid != true) return
        if (bitmap.isRecycled) return

        try {
            if (!ensureSurfaceValid()) return

            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(bitmapProgram)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextureId)
            if (lastBitmapWidth != bitmap.width || lastBitmapHeight != bitmap.height) {
                lastBitmapWidth = bitmap.width
                lastBitmapHeight = bitmap.height
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            }
            GLES20.glUniform1i(uBitmapTextureHandle, 1)

            GLES20.glUniformMatrix4fv(uBitmapMVPMatrixHandle, 1, false, bitmapMvpMatrix, 0)

            bitmapVertices.position(0)
            GLES20.glVertexAttribPointer(aBitmapPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, bitmapVertices)
            GLES20.glEnableVertexAttribArray(aBitmapPositionHandle)

            bitmapVertices.position(3)
            GLES20.glVertexAttribPointer(aBitmapTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, bitmapVertices)
            GLES20.glEnableVertexAttribArray(aBitmapTextureCoordHandle)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        } catch (e: Exception) {
            Log.e(tag, "renderBitmap error: ${e.message}", e)
        }
    }

    fun release() {
        isRunning = false
        isContextMadeCurrent = false
        lastBitmapWidth = -1
        lastBitmapHeight = -1
        try {
            surfaceTexture?.release()
            videoSurface?.release()
            if (videoProgram != 0) {
                GLES20.glDeleteProgram(videoProgram)
                videoProgram = 0
            }
            if (bitmapProgram != 0) {
                GLES20.glDeleteProgram(bitmapProgram)
                bitmapProgram = 0
            }
            if (videoTextureId != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(videoTextureId), 0)
                videoTextureId = -1
            }
            if (bitmapTextureId != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(bitmapTextureId), 0)
                bitmapTextureId = -1
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing GL: ${e.message}", e)
        }
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val info = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader $shaderType: $info")
            }
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val prog = GLES20.glCreateProgram()
        if (prog != 0) {
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val info = GLES20.glGetProgramInfoLog(prog)
                GLES20.glDeleteProgram(prog)
                throw RuntimeException("Could not link program: $info")
            }
        }
        return prog
    }
}
