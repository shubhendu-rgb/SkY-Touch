package com.example.service

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.text.TextUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.app.WallpaperManager
import com.example.service.wallpaper.LiveWallpaperManager
import com.example.data.AppDatabase
import com.example.data.AutoClickConfigEntity
import com.example.data.GestureActionEntity
import com.example.data.NotchConfigEntity
import com.example.data.NotchRepository
import com.example.data.SideDeckConfigEntity
import com.example.data.TextAssistantConfigEntity
import com.example.data.TextSnippetEntity
import com.example.util.GeminiTextHelper
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AirplanemodeInactive
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import com.example.ui.island.CapsuleType
import com.example.ui.island.DynamicNotchManager
import com.example.ui.island.MiniCapsuleEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.island.DynamicIslandPill
import com.example.ui.island.IslandState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.math.abs
import android.hardware.Camera
import android.media.MediaRecorder
import android.graphics.SurfaceTexture
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.os.Environment
import android.os.StatFs
import java.io.File
import android.content.pm.PackageManager
import android.service.quicksettings.TileService
import android.content.ComponentName

class NotchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NotchService"
        var isRunning = false
            private set
        var instance: NotchAccessibilityService? = null
            private set
        val isAutoClickRunning: Boolean
            get() = instance?.autoClickEngine?.isRunning?.value == true
        var isAutoClickMenuVisible: Boolean = false
            private set

        val isSilentRecording: Boolean
            get() = instance?.let { it.isRecordingBack || it.isRecordingFront } ?: false

        val isRecordingBackActive: Boolean
            get() = instance?.isRecordingBack == true

        val isRecordingFrontActive: Boolean
            get() = instance?.isRecordingFront == true

        fun stopSilentRecording(context: Context? = null): Boolean {
            val inst = instance
            if (inst == null) {
                context?.sendBroadcast(Intent("com.example.ACTION_STOP_RECORDING").apply {
                    setPackage(context.packageName)
                })
                return false
            }
            var stopped = false
            if (inst.isRecordingBack) {
                inst.stopBackCameraRecord()
                stopped = true
            }
            if (inst.isRecordingFront) {
                inst.stopFrontCameraRecord()
                stopped = true
            }
            updateStopRecordingTile(inst)
            return stopped
        }

        fun updateStopRecordingTile(context: Context) {
            try {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, com.example.service.tiles.StopRecordingTileService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request tile listening state", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var overlayContainer: FrameLayout? = null
    private var overlayView: View? = null
    private var overlayLifecycleOwner: ServiceOverlayLifecycleOwner? = null

    var autoClickEngine: com.example.service.AutoClickEngine? = null
        private set

    // Side Deck state
    private var sideDeckConfig = SideDeckConfigEntity()
    private var sideDeckHandleContainer: FrameLayout? = null
    private var sideDeckPanelContainer: FrameLayout? = null

    // Text Assistant & AI Snippets state
    private var textAssistantConfig = TextAssistantConfigEntity()
    private var textSnippets: List<TextSnippetEntity> = emptyList()
    private var isProcessingTextTransform = false
    private var lastReplacedText: String? = null
    private var lastReplacedTime: Long = 0L
    private var previousTextBeforeReplacement: String? = null

    private var inlineSpinnerJob: Job? = null
    private val spinnerFrames = listOf("◐", "◓", "◑", "◒")

    private lateinit var appPrefs: android.content.SharedPreferences
    private var notchMasterEnabled = true
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "notch_master_toggle") {
            notchMasterEnabled = sharedPreferences.getBoolean("notch_master_toggle", true)
            updateOverlayVisibility()
        }
    }

    private lateinit var repository: NotchRepository
    private var notchConfig = NotchConfigEntity()
    private var gestureActions = mapOf<String, GestureActionEntity>()

    // Gesture detection variables
    private var startX = 0f
    private var startY = 0f
    private val swipeThreshold = 50f
    private val handler = Handler(Looper.getMainLooper())

    // Tap detection state machine
    private var tapCount = 0
    private var lastTapUpTime = 0L
    private val multiTapTimeout = 320L
    private var pendingTapRunnable: Runnable? = null

    // Long press and Swipe & Hold state machine
    private var longPressRunnable: Runnable? = null
    private var isLongPressed = false
    private var swipeHoldRunnable: Runnable? = null
    private var isSwipeHoldTriggered = false
    private var currentSwipeHoldDirection: String? = null
    private val swipeHoldTimeout = 450L

    // Continuous repeating hold action for volume and brightness
    private var continuousHoldRunnable: Runnable? = null
    private var holdActionStartTime: Long = 0L
    private var brightnessToast: Toast? = null

    private var isFlashlightOn = false

    // Spy Cam recording state
    private var backCameraRecorder: MediaRecorder? = null
    private var backCamera: Camera? = null
    private var isRecordingBack = false

    private var frontCameraRecorder: MediaRecorder? = null
    private var frontCamera: Camera? = null
    private var isRecordingFront = false

    private var currentForegroundPackage: String? = null

    private val stopRecordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_STOP_RECORDING") {
                stopBackCameraRecord()
                stopFrontCameraRecord()
            }
        }
    }

    private val capsuleSystemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || context == null) return
            try {
                if (!DynamicNotchManager.isDynamicNotchEnabled.value) return
                val behavior = DynamicNotchManager.activeBehavior.value
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        if (behavior.batteryChargingStarted) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.BATTERY_CHARGING_STARTED,
                                    title = "Charging",
                                    subtitle = "Fast Charger Connected",
                                    iconVector = Icons.Default.BatteryChargingFull,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFF00E676),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        if (behavior.batteryChargingStopped) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.BATTERY_CHARGING_STOPPED,
                                    title = "Unplugged",
                                    subtitle = "Running on Battery",
                                    iconVector = Icons.Default.PowerOff,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFFFFA000),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                    Intent.ACTION_BATTERY_LOW -> {
                        if (behavior.batteryLowWarning) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.BATTERY_LOW,
                                    title = "Battery Low",
                                    subtitle = "Connect Charger Soon",
                                    iconVector = Icons.Default.BatteryAlert,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFFFF3D00),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 1 && behavior.headphonesConnected) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.HEADPHONES_CONNECTED,
                                    title = "Headphones",
                                    subtitle = "Audio Connected",
                                    iconVector = Icons.Default.Headphones,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFFA855F7),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        } else if (state == 0 && behavior.headphonesDisconnected) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.HEADPHONES_DISCONNECTED,
                                    title = "Headphones",
                                    subtitle = "Disconnected",
                                    iconVector = Icons.Default.Headphones,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        val isAirplane = intent.getBooleanExtra("state", false)
                        if (isAirplane && behavior.airplaneModeOn) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.AIRPLANE_ON,
                                    title = "Airplane Mode",
                                    subtitle = "Radios Disabled",
                                    iconVector = Icons.Default.AirplanemodeActive,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFFF59E0B),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        } else if (!isAirplane && behavior.airplaneModeOff) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.AIRPLANE_OFF,
                                    title = "Airplane Mode Off",
                                    subtitle = "Reconnecting Networks",
                                    iconVector = Icons.Default.AirplanemodeInactive,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFF10B981),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                    @Suppress("DEPRECATION")
                    ConnectivityManager.CONNECTIVITY_ACTION -> {
                        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        @Suppress("DEPRECATION")
                        val activeNet = cm?.activeNetworkInfo
                        val isConnected = activeNet?.isConnected == true
                        @Suppress("DEPRECATION")
                        val isWifi = activeNet?.type == ConnectivityManager.TYPE_WIFI
                        if (isConnected && isWifi && behavior.wifiConnected) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.WIFI_CONNECTED,
                                    title = "Wi-Fi Connected",
                                    subtitle = "Online",
                                    iconVector = Icons.Default.Wifi,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        } else if (!isConnected && behavior.wifiDisconnected) {
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.WIFI_DISCONNECTED,
                                    title = "Wi-Fi Disconnected",
                                    subtitle = "Offline",
                                    iconVector = Icons.Default.WifiOff,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFFEF4444),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        if (behavior.volumeModeChanged) {
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            val ringerMode = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                            val subtitle = when (ringerMode) {
                                AudioManager.RINGER_MODE_SILENT -> "Silent Mode"
                                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate Mode"
                                else -> "Normal Sound"
                            }
                            DynamicNotchManager.postCapsuleEvent(
                                MiniCapsuleEvent(
                                    type = CapsuleType.VOLUME_CHANGED,
                                    title = "Sound Mode",
                                    subtitle = subtitle,
                                    iconVector = Icons.Default.VolumeUp,
                                    accentColor = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                                    durationSeconds = behavior.collapseAfterSeconds
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore receiver error
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val db = AppDatabase.getDatabase(this)
        repository = NotchRepository(db)
        
        val filter = IntentFilter("com.example.ACTION_STOP_RECORDING")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopRecordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopRecordingReceiver, filter)
        }

        val capsuleFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        try {
            registerReceiver(capsuleSystemEventsReceiver, capsuleFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register capsule receiver", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
        Log.d(TAG, "Service Connected")
        DynamicNotchManager.initialize(this)
        autoClickEngine = com.example.service.AutoClickEngine(this, repository, serviceScope)

        appPrefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        notchMasterEnabled = appPrefs.getBoolean("notch_master_toggle", true)
        appPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        serviceScope.launch {
            // Ensure repository has default configuration
            repository.initializeDefaultsIfNeeded()

            // Observe notch config updates
            launch {
                repository.configFlow.collectLatest { config ->
                    Log.d(TAG, "Config updated: $config")
                    notchConfig = config
                    updateOverlay()
                }
            }

            // Observe side deck config updates
            launch {
                repository.sideDeckConfigFlow.collectLatest { config ->
                    Log.d(TAG, "Side Deck config updated: $config")
                    sideDeckConfig = config
                    updateSideDeckHandle()
                }
            }

            // Observe gesture actions
            launch {
                repository.actionsFlow.collectLatest { actions ->
                    Log.d(TAG, "Actions updated: ${actions.size}")
                    gestureActions = actions.associateBy { it.gestureName }
                }
            }

            // Observe Text Assistant config
            launch {
                repository.textAssistantConfigFlow.collectLatest { config ->
                    Log.d(TAG, "Text Assistant config updated: enabled=${config.enabled}, prefix=${config.triggerPrefix}")
                    textAssistantConfig = config
                }
            }

            // Observe Text Snippets
            launch {
                repository.snippetsFlow.collectLatest { snippets ->
                    Log.d(TAG, "Text Snippets updated: count=${snippets.size}")
                    textSnippets = snippets
                }
            }

            // Observe AutoClick config for menu visibility
            launch {
                repository.autoClickConfigFlow.collectLatest { config ->
                    isAutoClickMenuVisible = config.isMenuVisible
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOverlayVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        event.packageName?.toString()?.let { pkg ->
            if (pkg.isNotEmpty() && pkg != "android" && pkg != currentForegroundPackage) {
                currentForegroundPackage = pkg
                autoClickEngine?.onAppChanged(pkg)
                updateOverlayVisibility()
            }
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (textAssistantConfig.enabled) {
                    handleTextInterception(event)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (textAssistantConfig.enabled && !isProcessingTextTransform) {
                    stopInlineSpinner()
                }
                updateOverlayVisibility()
            }
        }
    }

    private fun handleTextInterception(event: AccessibilityEvent) {
        if (!textAssistantConfig.enabled) return
        if (isProcessingTextTransform) return

        val sourceNode = event.source ?: return
        try {
            if (!sourceNode.isEditable) {
                sourceNode.recycle()
                return
            }

            val text = sourceNode.text?.toString() ?: ""
            if (text.isEmpty()) {
                sourceNode.recycle()
                return
            }

            val now = System.currentTimeMillis()
            if (text == lastReplacedText && now - lastReplacedTime < 1500L) {
                sourceNode.recycle()
                return
            }

            val prefix = textAssistantConfig.triggerPrefix.ifEmpty { "?" }

            if (textAssistantConfig.enableNumericTriggers && handleDynamicNumericTrigger(text, prefix, sourceNode)) {
                return
            }

            if (textAssistantConfig.enableEditingCommands && handleEditingCommands(text, prefix, sourceNode)) {
                return
            }

            val activeSnippets = textSnippets.filter { it.isEnabled }

            for (snippet in activeSnippets) {
                val keyword = snippet.triggerKeyword.trim()
                if (keyword.isEmpty()) continue

                val fullTrigger = "$prefix$keyword"
                // Loosen trigger matching for keyboard autocorrect spaces and trailing punctuation
                val triggerRegex = Regex("${Pattern.quote(fullTrigger)}[\\s,.:;!?\\n\\t]*$", RegexOption.IGNORE_CASE)
                val matchResult = triggerRegex.find(text)

                if (matchResult != null) {
                    val triggerStartIndex = matchResult.range.first
                    val precedingText = text.substring(0, triggerStartIndex).trimEnd()

                    // Record input node screen bounds before clearing trigger or running async work
                    val inputBounds = Rect()
                    sourceNode.getBoundsInScreen(inputBounds)

                    if (!snippet.isAiAction) {
                        // Local instant snippet replacement
                        previousTextBeforeReplacement = precedingText
                        val replacement = snippet.replacementText
                        val newText = if (precedingText.isNotEmpty()) {
                            "$precedingText $replacement"
                        } else {
                            replacement
                        }

                        isProcessingTextTransform = true
                        lastReplacedText = newText
                        lastReplacedTime = System.currentTimeMillis()

                        softHapticPulse(isCompletion = true)
                        replaceTextInTargetNode(sourceNode, newText)
                        handler.postDelayed({ isProcessingTextTransform = false }, 400L)
                        sourceNode.recycle()
                        return
                    } else {
                        // AI LLM text transformation (Gemini)
                        val inputTextToRefine = precedingText.trim()
                        previousTextBeforeReplacement = precedingText

                        isProcessingTextTransform = true
                        softHapticPulse(isCompletion = false)
                        
                        startInlineSpinner(precedingText, sourceNode)

                        // Release original node reference before async network call
                        sourceNode.recycle()

                        serviceScope.launch {
                            val result = GeminiTextHelper.transformText(
                                apiKey = textAssistantConfig.apiKey,
                                modelName = textAssistantConfig.modelName,
                                inputText = inputTextToRefine,
                                instruction = snippet.aiPromptInstruction,
                                context = this@NotchAccessibilityService
                            )

                            withContext(Dispatchers.Main) {
                                stopInlineSpinner()

                                result.onSuccess { generatedText ->
                                    val newText = generatedText
                                    lastReplacedText = newText
                                    lastReplacedTime = System.currentTimeMillis()

                                    // Dynamic Node Re-acquisition
                                    val targetNode = findActiveEditableNode()
                                    replaceTextInTargetNode(targetNode, newText)
                                    softHapticPulse(isCompletion = true)
                                    targetNode?.recycle()
                                }.onFailure { error ->
                                    Log.e(TAG, "Gemini transform failed", error)
                                    
                                    val targetNode = findActiveEditableNode()
                                    replaceTextInTargetNode(targetNode, precedingText)
                                    targetNode?.recycle()
                                    
                                    Toast.makeText(
                                        this@NotchAccessibilityService,
                                        "AI Assistant: ${error.localizedMessage ?: "Failed"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                handler.postDelayed({ isProcessingTextTransform = false }, 600L)
                            }
                        }
                        return
                    }
                }
            }
            sourceNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error in text interception", e)
            try {
                sourceNode.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun handleDynamicNumericTrigger(text: String, prefix: String, sourceNode: AccessibilityNodeInfo): Boolean {
        val numericRegex = Regex("${Pattern.quote(prefix)}(\\d+)([_|,])?[\\s.:;!?\\n\\t]*$", RegexOption.IGNORE_CASE)
        val matchResult = numericRegex.find(text) ?: return false

        val count = matchResult.groupValues[1].toIntOrNull() ?: return false
        if (count <= 0) return false
        
        val suffix = matchResult.groups.get(2)?.value
        val isLineByLine = when (suffix) {
            "_" -> true
            "," -> false
            else -> textAssistantConfig.isDefaultMultiplierLineByLine
        }

        val triggerStartIndex = matchResult.range.first
        val precedingText = text.substring(0, triggerStartIndex).trimEnd()

        if (precedingText.isEmpty()) return false

        val wordCount = precedingText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val separator = if (isLineByLine) "\n" else " "

        if (precedingText.length == 1 && !isLineByLine) {
            val newText = precedingText.repeat(count)
            isProcessingTextTransform = true
            lastReplacedText = newText
            lastReplacedTime = System.currentTimeMillis()
            softHapticPulse(isCompletion = true)
            replaceTextInTargetNode(sourceNode, newText)
            handler.postDelayed({ isProcessingTextTransform = false }, 400L)
            sourceNode.recycle()
            return true
        } else if (precedingText.length == 1 || wordCount <= 3) {
            val newText = List(count) { precedingText }.joinToString(separator)
            isProcessingTextTransform = true
            lastReplacedText = newText
            lastReplacedTime = System.currentTimeMillis()
            softHapticPulse(isCompletion = true)
            replaceTextInTargetNode(sourceNode, newText)
            handler.postDelayed({ isProcessingTextTransform = false }, 400L)
            sourceNode.recycle()
            return true
        } else {
            val inputTextToRefine = precedingText.trim()
            val instruction = "Please rewrite or expand the following text so that the output is exactly or approximately $count words long."

            isProcessingTextTransform = true
            softHapticPulse(isCompletion = false)
            startInlineSpinner(precedingText, sourceNode)
            sourceNode.recycle()

            serviceScope.launch {
                val result = GeminiTextHelper.transformText(
                    apiKey = textAssistantConfig.apiKey,
                    modelName = textAssistantConfig.modelName,
                    inputText = inputTextToRefine,
                    instruction = instruction,
                    context = this@NotchAccessibilityService
                )

                withContext(Dispatchers.Main) {
                    stopInlineSpinner()

                    result.onSuccess { generatedText ->
                        val newText = generatedText
                        lastReplacedText = newText
                        lastReplacedTime = System.currentTimeMillis()

                        val targetNode = findActiveEditableNode()
                        replaceTextInTargetNode(targetNode, newText)
                        softHapticPulse(isCompletion = true)
                        targetNode?.recycle()
                    }.onFailure { error ->
                        Log.e(TAG, "Gemini transform failed for numeric trigger", error)
                        val targetNode = findActiveEditableNode()
                        replaceTextInTargetNode(targetNode, precedingText)
                        targetNode?.recycle()
                        Toast.makeText(this@NotchAccessibilityService, "AI Assistant: ${error.localizedMessage ?: "Failed"}", Toast.LENGTH_LONG).show()
                    }
                    handler.postDelayed({ isProcessingTextTransform = false }, 600L)
                }
            }
            return true
        }
    }

    private fun handleEditingCommands(text: String, prefix: String, sourceNode: AccessibilityNodeInfo): Boolean {
        // Check for ?translate:xx first (e.g. ?translate:es, ?translate:fr, ?translate:de, ?translate:en)
        val translateRegex = Regex("${Pattern.quote(prefix)}translate:([a-zA-Z]{2,5})[\\s,.:;!?\\n\\t]*$", RegexOption.IGNORE_CASE)
        val translateMatch = translateRegex.find(text)
        if (translateMatch != null) {
            val langCode = translateMatch.groupValues[1].lowercase()
            val precedingText = text.substring(0, translateMatch.range.first).trim()
            if (precedingText.isNotEmpty()) {
                isProcessingTextTransform = true
                previousTextBeforeReplacement = precedingText
                softHapticPulse(isCompletion = false)
                startInlineSpinner(precedingText, sourceNode)
                sourceNode.recycle()

                serviceScope.launch {
                    val result = GeminiTextHelper.transformText(
                        apiKey = textAssistantConfig.apiKey,
                        modelName = textAssistantConfig.modelName,
                        inputText = precedingText,
                        instruction = "Translate this text accurately into language code '$langCode'. Output ONLY the translated text without quotes or explanations.",
                        context = this@NotchAccessibilityService
                    )
                    withContext(Dispatchers.Main) {
                        stopInlineSpinner()
                        result.onSuccess { translated ->
                            lastReplacedText = translated
                            lastReplacedTime = System.currentTimeMillis()
                            val target = findActiveEditableNode()
                            replaceTextInTargetNode(target, translated)
                            softHapticPulse(isCompletion = true)
                            target?.recycle()
                        }.onFailure {
                            val target = findActiveEditableNode()
                            replaceTextInTargetNode(target, precedingText)
                            target?.recycle()
                        }
                        handler.postDelayed({ isProcessingTextTransform = false }, 500L)
                    }
                }
                return true
            }
        }

        val commandRegex = Regex("${Pattern.quote(prefix)}(replace|undo|copy|c|paste|v|cut|x|all|select|sel|clear|cls)[\\s,.:;!?\\n\\t]*$", RegexOption.IGNORE_CASE)
        val matchResult = commandRegex.find(text) ?: return false

        val command = matchResult.groupValues[1].lowercase()
        val precedingText = text.substring(0, matchResult.range.first)

        isProcessingTextTransform = true
        replaceTextInTargetNode(sourceNode, precedingText)
        softHapticPulse(isCompletion = true)

        val selStart = sourceNode.textSelectionStart
        val selEnd = sourceNode.textSelectionEnd
        val hasSelection = selStart >= 0 && selEnd >= 0 && selStart != selEnd

        when (command) {
            "replace" -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
                if (clipText.isNotEmpty()) {
                    previousTextBeforeReplacement = precedingText
                    replaceTextInTargetNode(sourceNode, clipText)
                }
            }
            "undo" -> {
                val toRestore = previousTextBeforeReplacement
                if (!toRestore.isNullOrEmpty()) {
                    previousTextBeforeReplacement = precedingText
                    replaceTextInTargetNode(sourceNode, toRestore)
                }
            }
            "copy", "c" -> {
                if (hasSelection) {
                    sourceNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Copied Text", precedingText)
                    clipboard.setPrimaryClip(clip)
                }
            }
            "paste", "v" -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
                if (clipText.isNotEmpty()) {
                    val newText = if (precedingText.isNotEmpty()) "$precedingText $clipText" else clipText
                    replaceTextInTargetNode(sourceNode, newText)
                } else {
                    sourceNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
            }
            "cut", "x" -> {
                if (hasSelection) {
                    sourceNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Cut Text", precedingText)
                    clipboard.setPrimaryClip(clip)
                    replaceTextInTargetNode(sourceNode, "")
                }
            }
            "all", "select", "sel" -> {
                val arguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, precedingText.length)
                }
                sourceNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
            }
            "clear", "cls" -> {
                replaceTextInTargetNode(sourceNode, "")
            }
        }

        handler.postDelayed({ isProcessingTextTransform = false }, 400L)
        sourceNode.recycle()
        return true
    }

    private fun startInlineSpinner(initialText: String, initialNode: AccessibilityNodeInfo) {
        inlineSpinnerJob?.cancel()
        
        // Initial set before coroutine starts
        val firstText = "$initialText ${spinnerFrames[0]}"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, firstText)
        }
        initialNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        
        inlineSpinnerJob = serviceScope.launch {
            var frameIndex = 1
            while (isActive) {
                delay(120)
                val currentFrame = spinnerFrames[frameIndex % spinnerFrames.size]
                val spinnerText = "$initialText $currentFrame"
                val targetNode = findActiveEditableNode()
                if (targetNode != null) {
                    val updateArgs = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            spinnerText
                        )
                    }
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, updateArgs)
                    
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, spinnerText.length)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, spinnerText.length)
                    }
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    targetNode.recycle()
                }
                frameIndex++
            }
        }
    }

    private fun stopInlineSpinner() {
        inlineSpinnerJob?.cancel()
        inlineSpinnerJob = null
    }

    private fun findActiveEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        try {
            // 1. Search focused input
            val focusedInput = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedInput != null && focusedInput.isEditable) {
                return focusedInput
            }
            focusedInput?.recycle()

            // 2. Search focused accessibility node
            val focusedA11y = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (focusedA11y != null && focusedA11y.isEditable) {
                return focusedA11y
            }
            focusedA11y?.recycle()

            // 3. Search tree hierarchy recursively for editable node
            val recursiveResult = findEditableNodeInHierarchy(root)
            if (recursiveResult != null) {
                return recursiveResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding active editable node", e)
        }
        return null
    }

    private fun findEditableNodeInHierarchy(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && (node.isFocused || node.isSelected)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNodeInHierarchy(child)
            child.recycle()
            if (found != null) {
                return found
            }
        }
        if (node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        return null
    }

    private fun replaceTextInTargetNode(targetNode: AccessibilityNodeInfo?, newText: String) {
        if (targetNode == null) {
            copyToClipboardDirectly(newText)
            return
        }

        try {
            // 1. Attempt standard ACTION_SET_TEXT
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
            val setTextSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setTextSuccess) {
                val selectionArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newText.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newText.length)
                }
                targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            }

            // 2. Fallback for messaging apps that suppress ACTION_SET_TEXT (WhatsApp, Signal, Telegram)
            if (!setTextSuccess) {
                performClipboardPasteReplacement(targetNode, newText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing text in node, executing clipboard fallback", e)
            performClipboardPasteReplacement(targetNode, newText)
        }
    }

    private fun performClipboardPasteReplacement(node: AccessibilityNodeInfo, text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Swiftslate Text", text)
            clipboard.setPrimaryClip(clip)

            // Dispatch Paste
            val pasteSuccess = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (!pasteSuccess) {
                Toast.makeText(this, "AI text copied to clipboard! Long-press to paste.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Paste replacement failed", e)
            copyToClipboardDirectly(text)
        }
    }

    private fun copyToClipboardDirectly(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Swiftslate AI", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "AI text copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun softHapticPulse(isCompletion: Boolean = false) {
        if (!textAssistantConfig.hapticFeedback) return
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = if (isCompletion) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = if (isCompletion) 16L else 10L
                val amplitude = if (isCompletion) 120 else 60
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                vibrator.vibrate(if (isCompletion) 16L else 10L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Soft haptic error", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        stopContinuousHoldAction()
        stopInlineSpinner()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (instance == this) {
            instance = null
        }
        
        if (::appPrefs.isInitialized) {
            appPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }

        stopContinuousHoldAction()
        stopInlineSpinner()
        handler.removeCallbacksAndMessages(null)
        autoClickEngine?.destroy()
        autoClickEngine = null
        removeOverlay()
        removeSideDeckHandle()
        removeSideDeckPanel()
        
        try {
            unregisterReceiver(stopRecordingReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        try {
            unregisterReceiver(capsuleSystemEventsReceiver)
        } catch (_: Exception) {}

        // Release recorders & cameras
        try { stopBackCameraRecord() } catch (_: Exception) {}
        try { stopFrontCameraRecord() } catch (_: Exception) {}

        serviceScope.cancel()
        Log.d(TAG, "Service Destroyed")
    }

    private fun removeOverlay() {
        stopContinuousHoldAction()
        overlayContainer?.let { container ->
            try {
                windowManager.removeView(container)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
        overlayLifecycleOwner?.let { owner ->
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        overlayLifecycleOwner = null
        overlayContainer = null
        overlayView = null
    }

    fun notifyDynamicNotchChanged() {
        handler.post {
            updateOverlay()
        }
    }

    private fun updateOverlay() {
        val isDynamicNotch = DynamicNotchManager.isDynamicNotchEnabled.value
        val behavior = DynamicNotchManager.activeBehavior.value
        val density = resources.displayMetrics.density
        val widthPx = (notchConfig.widthDp * density).toInt().coerceAtLeast(60)
        val heightPx = (notchConfig.heightDp * density).toInt().coerceAtLeast(24)
        val xOffsetPx = (notchConfig.xOffsetDp * density).toInt()
        val yOffsetPx = (notchConfig.yOffsetDp * density).toInt()

        // Calculate dynamic dimensions ensuring full touchable area
        val activeCapsule = DynamicNotchManager.currentCapsuleEvent.value ?: DynamicNotchManager.liveActivityEvent.value
        val effectiveWidthPx = if (isDynamicNotch) {
            if (activeCapsule != null) {
                (240 * density).toInt().coerceAtLeast(widthPx)
            } else {
                widthPx.coerceAtLeast((140 * density).toInt())
            }
        } else {
            widthPx
        }
        val effectiveHeightPx = if (isDynamicNotch) {
            if (activeCapsule != null && activeCapsule.actionButtons.isNotEmpty()) {
                (48 * density).toInt().coerceAtLeast(heightPx)
            } else {
                heightPx.coerceAtLeast((36 * density).toInt())
            }
        } else {
            heightPx
        }

        // Remove previous overlay before re-attaching
        removeOverlay()

        val layoutParams = WindowManager.LayoutParams().apply {
            width = effectiveWidthPx
            height = effectiveHeightPx
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            var flagMask = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            flags = flagMask
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = xOffsetPx
            y = yOffsetPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        val newContainer = object : FrameLayout(this) {
            init {
                isClickable = true
                isFocusable = false
                clipChildren = false
                clipToPadding = false
            }

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (isOverlayHidden) return false

                // Process gesture touch event first so notch gestures always work even if dynamic notch is enabled
                handleGestureTouchEvent(overlayView ?: this, ev)

                // Also allow children (Compose action buttons) to receive touch events
                try {
                    super.dispatchTouchEvent(ev)
                } catch (_: Exception) {}
                return true
            }
        }

        val lifecycleOwner = ServiceOverlayLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        overlayLifecycleOwner = lifecycleOwner

        // Set lifecycle and view tree owners on the root FrameLayout before children or WindowManager attach
        newContainer.setViewTreeLifecycleOwner(lifecycleOwner)
        newContainer.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        newContainer.setViewTreeViewModelStoreOwner(lifecycleOwner)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val isDynamic = DynamicNotchManager.isDynamicNotchEnabled.value
                val skin = DynamicNotchManager.activeSkin.value
                val currentBehavior = DynamicNotchManager.activeBehavior.value

                if (isDynamic) {
                    // Synchronize dimensions directly with calibrated notch position settings
                    val alignedSkin = skin.copy(
                        customWidthDp = notchConfig.widthDp,
                        customHeightDp = notchConfig.heightDp,
                        cornerRadiusDp = notchConfig.cornerRadiusDp
                    )

                    DynamicIslandPill(
                        skin = alignedSkin,
                        state = IslandState.COLLAPSED,
                        behavior = currentBehavior,
                        onStateChange = { /* Dynamic notch visual overlay is permanently unexpandable */ }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(notchConfig.widthDp.dp)
                            .height(notchConfig.heightDp.dp)
                    )
                }
            }
        }
        newContainer.addView(
            composeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        overlayView = composeView

        try {
            windowManager.addView(newContainer, layoutParams)
            overlayContainer = newContainer
            Log.d(TAG, "Overlay added successfully: isDynamic=$isDynamicNotch w=$effectiveWidthPx h=$effectiveHeightPx y=$yOffsetPx")
            updateOverlayVisibility()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window", e)
        }
    }

    private fun handleGestureTouchEvent(view: View, event: MotionEvent): Boolean {
        if (isOverlayHidden) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                isLongPressed = false
                isSwipeHoldTriggered = false
                currentSwipeHoldDirection = null
                stopContinuousHoldAction()

                // Visual touch scale bounce feedback
                view.animate()
                    .scaleX(1.10f)
                    .scaleY(1.10f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()

                swipeHoldRunnable?.let { handler.removeCallbacks(it) }
                swipeHoldRunnable = null

                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = Runnable {
                    isLongPressed = true
                    vibrateFeedback()
                    view.animate()
                        .scaleX(1.20f)
                        .scaleY(1.20f)
                        .setDuration(100)
                        .withEndAction {
                            view.animate().scaleX(1.10f).scaleY(1.10f).setDuration(100).start()
                        }
                        .start()
                    triggerGestureAction("LONG_PRESS")
                }
                handler.postDelayed(longPressRunnable!!, 500)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSwipeHoldTriggered) return true

                val deltaX = event.rawX - startX
                val deltaY = event.rawY - startY

                view.translationX = (deltaX * 0.35f).coerceIn(-45f, 45f)

                if (abs(deltaX) > swipeThreshold || abs(deltaY) > swipeThreshold) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > swipeThreshold) {
                        val direction = if (deltaX > 0) "SWIPE_RIGHT_AND_HOLD" else "SWIPE_LEFT_AND_HOLD"
                        if (currentSwipeHoldDirection != direction && !isSwipeHoldTriggered) {
                            currentSwipeHoldDirection = direction
                            swipeHoldRunnable?.let { handler.removeCallbacks(it) }
                            swipeHoldRunnable = Runnable {
                                isSwipeHoldTriggered = true
                                view.animate().scaleX(1.20f).scaleY(1.20f).setDuration(120).start()
                                triggerGestureAction(direction)
                            }
                            handler.postDelayed(swipeHoldRunnable!!, swipeHoldTimeout)
                        }
                    } else {
                        if (!isSwipeHoldTriggered) {
                            swipeHoldRunnable?.let { handler.removeCallbacks(it) }
                            swipeHoldRunnable = null
                            currentSwipeHoldDirection = null
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                view.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()

                stopContinuousHoldAction()
                longPressRunnable?.let { handler.removeCallbacks(it) }
                swipeHoldRunnable?.let { handler.removeCallbacks(it) }

                if (isSwipeHoldTriggered) {
                    isSwipeHoldTriggered = false
                    currentSwipeHoldDirection = null
                } else if (!isLongPressed) {
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY

                    if (abs(deltaX) > swipeThreshold && abs(deltaX) > abs(deltaY)) {
                        if (deltaX > 0) {
                            triggerGestureAction("SWIPE_RIGHT")
                        } else {
                            triggerGestureAction("SWIPE_LEFT")
                        }
                    } else if (abs(deltaY) > swipeThreshold && abs(deltaY) > abs(deltaX)) {
                        if (deltaY > 0) {
                            triggerGestureAction("SWIPE_DOWN")
                        } else {
                            triggerGestureAction("SWIPE_UP")
                        }
                    } else if (abs(deltaX) <= swipeThreshold && abs(deltaY) <= swipeThreshold) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapUpTime < multiTapTimeout) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapUpTime = currentTime

                        pendingTapRunnable?.let { handler.removeCallbacks(it) }

                        if (tapCount >= 3) {
                            tapCount = 0
                            pendingTapRunnable = null
                            triggerGestureAction("TRIPLE_TAP")
                        } else {
                            val currentCount = tapCount
                            pendingTapRunnable = Runnable {
                                if (currentCount == 2) {
                                    triggerGestureAction("DOUBLE_TAP")
                                } else if (currentCount == 1) {
                                    val singleTapAction = gestureActions["SINGLE_TAP"]
                                    if (singleTapAction != null && singleTapAction.actionType != "NONE") {
                                        triggerGestureAction("SINGLE_TAP")
                                    }
                                }
                                tapCount = 0
                                pendingTapRunnable = null
                            }
                            handler.postDelayed(pendingTapRunnable!!, multiTapTimeout)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                view.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                stopContinuousHoldAction()
                longPressRunnable?.let { handler.removeCallbacks(it) }
                swipeHoldRunnable?.let { handler.removeCallbacks(it) }
                pendingTapRunnable?.let { handler.removeCallbacks(it) }
                isSwipeHoldTriggered = false
                currentSwipeHoldDirection = null
                tapCount = 0
                return true
            }
        }
        return false
    }

    private fun isContinuousAction(actionType: String): Boolean {
        return actionType == "VOLUME_UP" ||
                actionType == "VOLUME_DOWN" ||
                actionType == "BRIGHTNESS_UP" ||
                actionType == "BRIGHTNESS_DOWN"
    }

    private fun isHoldGesture(gestureName: String): Boolean {
        return gestureName == "LONG_PRESS" ||
                gestureName == "SWIPE_LEFT_AND_HOLD" ||
                gestureName == "SWIPE_RIGHT_AND_HOLD"
    }

    private fun startContinuousHoldAction(actionEntity: GestureActionEntity) {
        stopContinuousHoldAction()
        holdActionStartTime = System.currentTimeMillis()

        val runnable = object : Runnable {
            override fun run() {
                // Execute repeating action step
                executeAction(actionEntity)
                vibrateFeedback()

                // Slowly speed up over 1 second (1000ms)
                val elapsed = (System.currentTimeMillis() - holdActionStartTime).coerceIn(0L, 1000L)
                val progress = elapsed / 1000f // 0.0 at start -> 1.0 after 1 second

                // Starts at 300ms interval, smoothly accelerates down to 60ms interval over 1 sec
                val nextDelay = (300L - (progress * (300L - 60L))).toLong().coerceIn(60L, 300L)

                handler.postDelayed(this, nextDelay)
            }
        }
        continuousHoldRunnable = runnable
        // First repeat step occurs after 300ms
        handler.postDelayed(runnable, 300L)
    }

    private fun stopContinuousHoldAction() {
        continuousHoldRunnable?.let {
            handler.removeCallbacks(it)
        }
        continuousHoldRunnable = null
    }

    private fun triggerGestureAction(gestureName: String) {
        val actionEntity = gestureActions[gestureName] ?: return
        if (actionEntity.actionType == "NONE") return

        Log.d(TAG, "Triggering gesture: $gestureName -> ${actionEntity.actionType}")
        vibrateFeedback()

        // Increment stats in background
        serviceScope.launch {
            repository.incrementStat(gestureName)
        }

        executeAction(actionEntity)

        // If it is a hold gesture and mapped to continuous actions (volume up/down, brightness up/down),
        // trigger again and again with accelerating speed over 2 seconds
        if (isHoldGesture(gestureName) && isContinuousAction(actionEntity.actionType)) {
            startContinuousHoldAction(actionEntity)
        }
    }

    private fun executeAction(action: GestureActionEntity) {
        when (action.actionType) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "SCREENSHOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    handler.post {
                        Toast.makeText(this, "Screenshot requires Android 9 (Pie) or higher", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "FLASHLIGHT" -> toggleFlashlight()
            "MEDIA_PLAY_PAUSE" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "MEDIA_NEXT" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "MEDIA_PREVIOUS" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "SPY_CAM_BACK" -> toggleBackCameraRecord()
            "SPY_CAM_FRONT" -> toggleFrontCameraRecord()
            "VOLUME_UP" -> adjustVolume(AudioManager.ADJUST_RAISE)
            "VOLUME_DOWN" -> adjustVolume(AudioManager.ADJUST_LOWER)
            "BRIGHTNESS_UP" -> adjustBrightness(8)
            "BRIGHTNESS_DOWN" -> adjustBrightness(-8)
            "CUSTOM_BRIGHTNESS" -> setCustomBrightness(action.extraValue?.toIntOrNull() ?: 50)
            "LAUNCH_APP" -> launchApp(action.packageToLaunch)
            "SHORTCUT" -> launchShortcut(action)
            "AUTOCLICK_TOGGLE" -> toggleAutoClick()
            "AUTOCLICK_START" -> autoClickEngine?.start()
            "AUTOCLICK_STOP" -> autoClickEngine?.stop()
            "WALLPAPER_NEXT" -> cycleWallpaperAction("NEXT")
            "WALLPAPER_PREVIOUS" -> cycleWallpaperAction("PREV")
            "WALLPAPER_RANDOM" -> cycleWallpaperAction("RANDOM")
            "WALLPAPER_HOME_NEXT" -> cycleWallpaperAction("NEXT", screenType = "HOME")
            "WALLPAPER_LOCK_NEXT" -> cycleWallpaperAction("NEXT", screenType = "LOCK")
            "WALLPAPER_IMAGES_NEXT" -> cycleWallpaperAction("NEXT", sourceFilter = "IMAGES_ONLY")
            "WALLPAPER_FOLDER_NEXT" -> cycleWallpaperAction("NEXT", sourceFilter = "FOLDER_ONLY")
            else -> {}
        }
    }

    private fun cycleWallpaperAction(
        direction: String,
        screenType: String? = null,
        sourceFilter: String? = null
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val wallpaperManager = try { WallpaperManager.getInstance(this@NotchAccessibilityService) } catch (_: Exception) { null }
            val isLiveActive = try { wallpaperManager?.wallpaperInfo != null } catch (_: Exception) { false }

            if (isLiveActive) {
                // Live wallpaper is currently active on phone: cycle through configured live wallpapers & presets
                val newConfig = LiveWallpaperManager.cycleLiveWallpaper(
                    context = this@NotchAccessibilityService,
                    direction = direction
                )
                withContext(Dispatchers.Main) {
                    vibrateFeedback()
                    Toast.makeText(
                        this@NotchAccessibilityService,
                        "Live Wallpaper: ${newConfig.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Static wallpaper is active on phone: cycle static image/folder wallpaper
                val changed = WallpaperWorker.cycleWallpaper(
                    context = this@NotchAccessibilityService,
                    direction = direction,
                    preferredScreenType = screenType,
                    sourceFilter = sourceFilter
                )
                withContext(Dispatchers.Main) {
                    if (changed) {
                        vibrateFeedback()
                    } else {
                        Toast.makeText(
                            this@NotchAccessibilityService,
                            "No wallpapers configured in Wallpaper Changer. Please select images or a folder first.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun canWriteSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(this)
        } else {
            true
        }
    }

    private fun promptWriteSettingsPermission() {
        handler.post {
            Toast.makeText(
                this,
                "Please allow 'Modify system settings' for SkY Touch to control brightness",
                Toast.LENGTH_LONG
            ).show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot launch write settings", e)
            }
        }
    }

    private fun adjustBrightness(deltaPercent: Int) {
        if (!canWriteSettings()) {
            promptWriteSettingsPermission()
            return
        }
        try {
            val currentBrightness = try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) {
                128
            }
            val deltaValue = (deltaPercent * 255) / 100
            val newBrightness = (currentBrightness + deltaValue).coerceIn(15, 255)

            try {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            } catch (_: Exception) {}

            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
            val percent = (newBrightness * 100) / 255
            handler.post {
                val symbol = if (deltaPercent > 0) "+" else "-"
                brightnessToast?.cancel()
                brightnessToast = Toast.makeText(this, "$symbol Brightness: $percent%", Toast.LENGTH_SHORT)
                brightnessToast?.show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting brightness", e)
        }
    }

    private fun setCustomBrightness(percent: Int) {
        if (!canWriteSettings()) {
            promptWriteSettingsPermission()
            return
        }
        try {
            val safePercent = percent.coerceIn(5, 100)
            val newBrightness = ((safePercent * 255) / 100).coerceIn(15, 255)

            try {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            } catch (_: Exception) {}

            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
            handler.post {
                Toast.makeText(this, "Brightness set to $safePercent%", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting custom brightness", e)
        }
    }

    private fun launchShortcut(action: GestureActionEntity) {
        val extra = action.extraValue
        if (extra.isNullOrEmpty()) {
            handler.post {
                Toast.makeText(this, "No shortcut target found", Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            when {
                extra == "SHORTCUT_SELFIE" -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        putExtra("android.intent.extras.CAMERA_FACING", 1)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                extra == "SHORTCUT_SEARCH" -> {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                extra == "SHORTCUT_ALARM" -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                extra == "SHORTCUT_EMAIL" -> {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                extra == "SHORTCUT_WIFI" -> {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                extra == "SHORTCUT_BLUETOOTH" -> {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                extra == "SHORTCUT_BATTERY" -> {
                    val intent = try {
                        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    } catch (_: Exception) {
                        Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                extra == "SHORTCUT_DISPLAY" -> {
                    startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                extra == "SHORTCUT_SOUND" -> {
                    startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                extra == "SHORTCUT_APPS" -> {
                    startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                else -> {
                    val intent = Intent.parseUri(extra, Intent.URI_INTENT_SCHEME).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                    } catch (se: SecurityException) {
                        val targetPkg = intent.`package` ?: intent.component?.packageName ?: action.packageToLaunch
                        if (targetPkg != null) {
                            val fallbackIntent = Intent(intent.action ?: Intent.ACTION_VIEW).apply {
                                data = intent.data
                                `package` = targetPkg
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                startActivity(fallbackIntent)
                            } catch (_: Exception) {
                                val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                } else {
                                    throw se
                                }
                            }
                        } else {
                            throw se
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shortcut: $extra", e)
            handler.post {
                Toast.makeText(this, "Shortcut target is unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.getOrNull(0)
            if (cameraId != null) {
                isFlashlightOn = !isFlashlightOn
                cameraManager.setTorchMode(cameraId, isFlashlightOn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error", e)
            handler.post {
                Toast.makeText(this, "Camera/Flashlight is currently busy or unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun launchApp(packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                handler.post {
                    Toast.makeText(this, "Application cannot be launched", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: $packageName", e)
        }
    }

    private fun vibrateFeedback() {
        if (notchConfig.vibrationStrength == 0) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            val duration = when (notchConfig.vibrationStrength) {
                1 -> 15L // Light
                2 -> 40L // Medium
                3 -> 85L // Heavy
                else -> 0L
            }
            if (duration > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = when (notchConfig.vibrationStrength) {
                        1 -> 80
                        2 -> 160
                        else -> 255
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    vibrator.vibrate(duration)
                }
            }
        }
    }

    // ==========================================
    // SIDE DECK FLOATING HANDLE & PANEL METHODS
    // ==========================================

    private fun updateSideDeckHandle() {
        if (!sideDeckConfig.enabled) {
            removeSideDeckHandle()
            closeSideDeckPanel()
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        val widthPx = (sideDeckConfig.handleWidthDp * density).toInt().coerceIn(5, 80)
        val heightPx = (sideDeckConfig.handleHeightDp * density).toInt().coerceIn(40, 400)
        val blockBufferPx = if (sideDeckConfig.blockBackGesture) {
            (sideDeckConfig.backGestureBlockBufferDp * density).toInt().coerceIn(0, 80)
        } else {
            0
        }
        val totalTouchWidthPx = widthPx + blockBufferPx
        val isRight = sideDeckConfig.edge.uppercase() != "LEFT"
        val yPos = ((screenHeight * sideDeckConfig.yOffsetPercent) - heightPx / 2).toInt().coerceIn(0, (screenHeight - heightPx).coerceAtLeast(0))

        val currentParams = sideDeckHandleContainer?.layoutParams as? WindowManager.LayoutParams
        val handleEdgeMargin = (sideDeckConfig.handleEdgeDistanceDp * density).toInt()
        if (sideDeckHandleContainer != null && currentParams != null) {
            currentParams.width = totalTouchWidthPx
            currentParams.height = heightPx
            currentParams.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
            currentParams.x = handleEdgeMargin
            currentParams.y = yPos
            try {
                windowManager.updateViewLayout(sideDeckHandleContainer, currentParams)
                updateSideDeckHandleBackground()
                sideDeckHandleContainer?.let { updateSystemGestureExclusion(it) }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update side deck handle layout, recreating", e)
                removeSideDeckHandle()
            }
        }

        val container = FrameLayout(this)
        val innerView = View(this)

        val layoutParams = WindowManager.LayoutParams().apply {
            width = totalTouchWidthPx
            height = heightPx
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
            x = handleEdgeMargin
            y = yPos
        }

        val innerParams = FrameLayout.LayoutParams(widthPx, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        }
        container.addView(innerView, innerParams)
        sideDeckHandleContainer = container

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            container.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                updateSystemGestureExclusion(v)
            }
        }

        updateSideDeckHandleBackground()
        setupSideDeckHandleTouch(container)

        try {
            windowManager.addView(container, layoutParams)
            updateSystemGestureExclusion(container)
            updateOverlayVisibility()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding side deck handle", e)
        }
    }

    private fun updateSystemGestureExclusion(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.post {
                try {
                    if (sideDeckConfig.enabled && sideDeckConfig.blockBackGesture && view.isAttachedToWindow) {
                        val w = view.width
                        val h = view.height
                        if (w > 0 && h > 0) {
                            val rect = Rect(0, 0, w, h)
                            view.systemGestureExclusionRects = listOf(rect)
                        }
                    } else {
                        view.systemGestureExclusionRects = emptyList()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set system gesture exclusion", e)
                }
            }
        }
    }

    private fun updateSideDeckHandleBackground() {
        val container = sideDeckHandleContainer ?: return
        val innerView = container.getChildAt(0) ?: return
        val isRight = sideDeckConfig.edge.uppercase() != "LEFT"
        val density = resources.displayMetrics.density
        val widthPx = (sideDeckConfig.handleWidthDp * density).toInt().coerceIn(5, 80)

        val innerParams = (innerView.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(widthPx, FrameLayout.LayoutParams.MATCH_PARENT)
        innerParams.width = widthPx
        innerParams.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        innerView.layoutParams = innerParams

        val baseColor = try {
            Color.parseColor(sideDeckConfig.handleColorHex)
        } catch (_: Exception) {
            Color.parseColor("#4F46E5")
        }
        val alphaInt = (sideDeckConfig.handleOpacity * 255).toInt().coerceIn(30, 255)
        val colorWithAlpha = Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val radius = sideDeckConfig.handleRoundnessDp * density
        val radii = if (sideDeckConfig.handleEdgeDistanceDp > 0) {
            floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
        } else if (isRight) {
            floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        } else {
            floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
        }

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(colorWithAlpha)
        }
        innerView.background = drawable
    }

    private fun setupSideDeckHandleTouch(view: View) {
        var downX = 0f
        var downY = 0f
        var hasMoved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    hasMoved = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val isRight = sideDeckConfig.edge.uppercase() != "LEFT"
                    // Swiping inward opens panel
                    if (!hasMoved) {
                        val isSwipingInward = if (isRight) (dx < -15f && Math.abs(dx) > Math.abs(dy) * 0.7f) else (dx > 15f && Math.abs(dx) > Math.abs(dy) * 0.7f)
                        if (isSwipingInward) {
                            hasMoved = true
                            if (sideDeckConfig.vibrateOnTouch) vibrateFeedback()
                            openSideDeckPanel()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }
    }

    fun openSideDeckPanel() {
        if (sideDeckPanelContainer != null) return

        val density = resources.displayMetrics.density
        val isRight = sideDeckConfig.edge.uppercase() != "LEFT"
        val dockWidth = (64 * density).toInt()

        val root = FrameLayout(this)
        val windowParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        // Frosted / dark translucent backdrop
        val backdrop = View(this).apply {
            setBackgroundColor(Color.argb(120, 10, 10, 18))
            setOnClickListener {
                closeSideDeckPanel()
            }
        }
        root.addView(backdrop, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Floating Capsule Dock Container
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val dockBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = sideDeckConfig.deckRoundnessDp * density
                setColor(Color.parseColor("#E012121D"))
            }
            background = dockBg
            elevation = 20f * density
            setPadding((10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt())
        }

        val dockParams = FrameLayout.LayoutParams(dockWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            val edgeMargin = (sideDeckConfig.deckEdgeDistanceDp * density).toInt()
            if (isRight) {
                marginEnd = edgeMargin
            } else {
                marginStart = edgeMargin
            }
        }
        root.addView(dock, dockParams)

        val animatedViews = mutableListOf<View>()

        // 1. Top Quick Action Tools (Circular 42dp x 42dp buttons)
        if (sideDeckConfig.showSystemTools) {
            // Screenshot (Crimson/Red / Dynamic Error)
            val screenshotColor = com.example.util.ColorHelper.getDynamicColorSafe(this, android.R.color.system_accent1_500, "#DC2626")
            val screenshotTool = createDockToolButton(com.example.R.drawable.ic_camera, screenshotColor) {
                closeSideDeckPanel()
                handler.postDelayed({
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    } else {
                        Toast.makeText(this, "Screenshot requires Android 9+", Toast.LENGTH_SHORT).show()
                    }
                }, 180)
            }
            dock.addView(screenshotTool)
            animatedViews.add(screenshotTool)

            // Quick Settings / Controls (Vibrant Blue / Dynamic Secondary)
            val settingsColor = com.example.util.ColorHelper.getDynamicColorSafe(this, android.R.color.system_accent2_500, "#2563EB")
            val settingsTool = createDockToolButton(com.example.R.drawable.ic_settings, settingsColor) {
                closeSideDeckPanel()
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            }
            dock.addView(settingsTool)
            animatedViews.add(settingsTool)

            // Flashlight (Indigo or Amber if On / Dynamic Primary)
            val baseFlashColor = com.example.util.ColorHelper.getDynamicColorSafe(this, android.R.color.system_accent1_500, "#4F46E5")
            val flashColor = if (isFlashlightOn) Color.parseColor("#D97706") else baseFlashColor
            val flashTool = createDockToolButton(com.example.R.drawable.ic_flashlight, flashColor) { view ->
                toggleFlashlight()
                (view.background as? GradientDrawable)?.setColor(
                    if (isFlashlightOn) Color.parseColor("#D97706") else baseFlashColor
                )
            }
            dock.addView(flashTool)
            animatedViews.add(flashTool)

            // Screen Lock / Home (Dark Slate / Dynamic Neutral)
            val lockColor = com.example.util.ColorHelper.getDynamicColorSafe(this, android.R.color.system_neutral1_800, "#334155")
            val lockTool = createDockToolButton(com.example.R.drawable.ic_lock, lockColor) {
                closeSideDeckPanel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            dock.addView(lockTool)
            animatedViews.add(lockTool)

            // Subtle horizontal accent divider separating tools and apps
            val divider = View(this).apply {
                val divParams = LinearLayout.LayoutParams((20 * density).toInt(), (1.5f * density).toInt().coerceAtLeast(1)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = (4 * density).toInt()
                    bottomMargin = (10 * density).toInt()
                }
                layoutParams = divParams
                val divBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 1f * density
                    setColor(Color.parseColor("#30FFFFFF"))
                }
                background = divBg
            }
            dock.addView(divider)
            animatedViews.add(divider)
        }

        // 2. Bottom Group (Pinned Apps)
        if (sideDeckConfig.showShortcutApps) {
            val userPinned = sideDeckConfig.pinnedAppPackages
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val targetPackages = if (userPinned.isNotEmpty()) {
                userPinned.take(5)
            } else {
                val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolveList = packageManager.queryIntentActivities(intent, 0)
                resolveList.map { it.activityInfo.packageName }
                    .filter { it != packageName }
                    .distinct()
                    .take(4)
            }

            targetPackages.forEach { pkg ->
                val appIconView = ImageView(this).apply {
                    val iconSize = (40 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = (8 * density).toInt()
                    }
                    val appIcon = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
                    if (appIcon != null) {
                        setImageDrawable(appIcon)
                    } else {
                        setImageResource(android.R.drawable.sym_def_app_icon)
                    }

                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, 10f * density)
                        }
                    }
                    clipToOutline = true
                    isClickable = true
                    isFocusable = true

                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)

                    setOnTouchListener(object : View.OnTouchListener {
                        private var initialX = 0f
                        private var initialY = 0f
                        private var isDragging = false
                        private var ghostView: ImageView? = null
                        private val density = resources.displayMetrics.density
                        private val thresholdPx = 65 * density
                        private val touchSlopPx = 15 * density
                        private var hasHapticTriggered = false
                        private val iconLocation = IntArray(2)

                        override fun onTouch(v: View, event: MotionEvent): Boolean {
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    initialX = event.rawX
                                    initialY = event.rawY
                                    isDragging = false
                                    hasHapticTriggered = false
                                    v.getLocationOnScreen(iconLocation)
                                    return true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.rawX - initialX
                                    val dy = event.rawY - initialY
                                    val distance = if (isRight) -dx else dx
                                    
                                    if (!isDragging) {
                                        if (Math.abs(dx) > touchSlopPx || Math.abs(dy) > touchSlopPx) {
                                            isDragging = true
                                            createGhostView(pkg, event.rawX, event.rawY)
                                            v.visibility = View.INVISIBLE
                                        }
                                    }
                                    
                                    if (isDragging) {
                                        updateGhostPosition(event.rawX, event.rawY)
                                        if (distance > thresholdPx) {
                                            if (!hasHapticTriggered) {
                                                hasHapticTriggered = true
                                                vibrateLight()
                                            }
                                            ghostView?.alpha = 1.0f
                                        } else {
                                            hasHapticTriggered = false
                                            ghostView?.alpha = 0.85f
                                        }
                                    }
                                    return true
                                }
                                MotionEvent.ACTION_UP -> {
                                    val dx = event.rawX - initialX
                                    val distance = if (isRight) -dx else dx
                                    
                                    if (isDragging) {
                                        if (distance > thresholdPx) {
                                            triggerSplitView(pkg, v)
                                        } else {
                                            animateGhostBack(v)
                                        }
                                    } else {
                                        v.visibility = View.VISIBLE
                                        vibrateFeedback()
                                        closeSideDeckPanel()
                                        launchAppInFloatingWindow(pkg)
                                    }
                                    return true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    if (isDragging) {
                                        animateGhostBack(v)
                                    } else {
                                        v.visibility = View.VISIBLE
                                    }
                                    return true
                                }
                            }
                            return false
                        }

                        private fun createGhostView(pkg: String, x: Float, y: Float) {
                            val ghost = ImageView(this@NotchAccessibilityService).apply {
                                val iconSize = (40 * density).toInt()
                                layoutParams = ViewGroup.LayoutParams(iconSize, iconSize)
                                val appIcon = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
                                if (appIcon != null) {
                                    setImageDrawable(appIcon)
                                } else {
                                    setImageResource(android.R.drawable.sym_def_app_icon)
                                }
                                outlineProvider = object : ViewOutlineProvider() {
                                    override fun getOutline(view: View, outline: Outline) {
                                        outline.setRoundRect(0, 0, view.width, view.height, 10f * density)
                                    }
                                }
                                clipToOutline = true
                                elevation = 25f * density
                                scaleX = 1.15f
                                scaleY = 1.15f
                                alpha = 0.85f
                            }
                            
                            val size = (40 * density).toInt()
                            val params = WindowManager.LayoutParams().apply {
                                width = size
                                height = size
                                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                format = PixelFormat.TRANSLUCENT
                                gravity = Gravity.TOP or Gravity.START
                                this.x = (x - size / 2).toInt()
                                this.y = (y - size / 2).toInt()
                            }
                            
                            try {
                                windowManager.addView(ghost, params)
                                ghostView = ghost
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to add ghost view", e)
                            }
                        }

                        private fun updateGhostPosition(x: Float, y: Float) {
                            val ghost = ghostView ?: return
                            val size = (40 * density).toInt()
                            val params = ghost.layoutParams as WindowManager.LayoutParams
                            params.x = (x - size / 2).toInt()
                            params.y = (y - size / 2).toInt()
                            try {
                                windowManager.updateViewLayout(ghost, params)
                            } catch (_: Exception) {}
                        }

                        private fun animateGhostBack(originalView: View) {
                            val ghost = ghostView ?: return
                            val params = ghost.layoutParams as WindowManager.LayoutParams
                            
                            val startX = params.x.toFloat()
                            val startY = params.y.toFloat()
                            val targetX = iconLocation[0].toFloat()
                            val targetY = iconLocation[1].toFloat()
                            
                            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = 250L
                                interpolator = OvershootInterpolator(1.2f)
                                addUpdateListener { valueAnimator ->
                                    val fraction = valueAnimator.animatedValue as Float
                                    val currentX = startX + (targetX - startX) * fraction
                                    val currentY = startY + (targetY - startY) * fraction
                                    
                                    params.x = currentX.toInt()
                                    params.y = currentY.toInt()
                                    
                                    ghost.scaleX = 1.15f - (0.15f * fraction)
                                    ghost.scaleY = 1.15f - (0.15f * fraction)
                                    
                                    try {
                                        windowManager.updateViewLayout(ghost, params)
                                    } catch (_: Exception) {}
                                }
                                addListener(object : android.animation.AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: android.animation.Animator) {
                                        removeGhost()
                                        originalView.visibility = View.VISIBLE
                                    }
                                })
                            }
                            animator.start()
                        }

                        private fun removeGhost() {
                            ghostView?.let {
                                try {
                                    windowManager.removeView(it)
                                } catch (_: Exception) {}
                            }
                            ghostView = null
                        }

                        private fun triggerSplitView(pkg: String, originalView: View) {
                            val ghost = ghostView
                            if (ghost != null) {
                                ghost.animate()
                                    .scaleX(0.2f)
                                    .scaleY(0.2f)
                                    .alpha(0f)
                                    .setDuration(180)
                                    .withEndAction {
                                        removeGhost()
                                        originalView.visibility = View.VISIBLE
                                    }
                                    .start()
                            } else {
                                originalView.visibility = View.VISIBLE
                            }
                            
                            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                            closeSideDeckPanel()
                            
                            handler.postDelayed({
                                launchAppInSplitView(pkg)
                            }, 300)
                        }
                    })
                }
                dock.addView(appIconView)
                animatedViews.add(appIconView)
            }
        }

        // 3. Bottom Edit Action (Small circular '+' button to open app picker/customizer)
        val editBtn = TextView(this).apply {
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            val btnSize = (34 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (2 * density).toInt()
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#232338"))
            }
            background = bg
            isClickable = true
            isFocusable = true
            setOnClickListener {
                vibrateFeedback()
                closeSideDeckPanel()
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        }
        dock.addView(editBtn)
        animatedViews.add(editBtn)

        sideDeckPanelContainer = root
        try {
            windowManager.addView(root, windowParams)

            // Handle expanding morph animation
            sideDeckHandleContainer?.getChildAt(0)?.let { handleInner ->
                handleInner.animate()
                    .scaleX(3f)
                    .scaleY(4f)
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction {
                        sideDeckHandleContainer?.visibility = View.INVISIBLE
                    }
                    .start()
            }

            // Morph/expand dock from the trigger location
            dock.scaleX = 0.2f
            dock.scaleY = 0.3f
            dock.translationX = if (isRight) (20f * density) else (-20f * density)
            dock.alpha = 0f

            dock.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.15f))
                .start()

            backdrop.animate()
                .alpha(1f)
                .setDuration(220)
                .start()

            // Staggered icon pop-in animation
            animatedViews.forEachIndexed { index, view ->
                view.alpha = 0f
                view.scaleX = 0.5f
                view.scaleY = 0.5f
                view.translationY = 10f * density
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(190)
                    .setStartDelay(index * 22L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing side deck panel", e)
            sideDeckPanelContainer = null
        }
    }

    fun closeSideDeckPanel() {
        val root = sideDeckPanelContainer ?: return
        val dock = root.getChildAt(1) ?: return
        val backdrop = root.getChildAt(0) ?: return
        val isRight = sideDeckConfig.edge.uppercase() != "LEFT"
        val density = resources.displayMetrics.density

        // Restore trigger handle visibility and shrink back
        sideDeckHandleContainer?.visibility = View.VISIBLE
        sideDeckHandleContainer?.getChildAt(0)?.let { handleInner ->
            handleInner.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(170)
                .start()
        }

        // Beautiful Off-screen Translation & Fade Out with AccelerateInterpolator(1.5f)
        val translationOffset = if (isRight) (100f * density) else (-100f * density)
        dock.animate()
            .translationX(translationOffset)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .start()

        backdrop.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                removeSideDeckPanel()
            }
            .start()
    }

    private fun removeSideDeckPanel() {
        sideDeckPanelContainer?.let { root ->
            try {
                windowManager.removeView(root)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing side deck panel", e)
            }
        }
        sideDeckPanelContainer = null
    }

    private fun removeSideDeckHandle() {
        sideDeckHandleContainer?.let { container ->
            try {
                windowManager.removeView(container)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing side deck handle", e)
            }
        }
        sideDeckHandleContainer = null
    }

    private fun createDockToolButton(
        iconResId: Int,
        bgColor: Int,
        onClick: (View) -> Unit
    ): ImageView {
        val density = resources.displayMetrics.density
        val btnSize = (42 * density).toInt()
        val padding = (10 * density).toInt()

        return ImageView(this).apply {
            setImageResource(iconResId)
            setPadding(padding, padding, padding, padding)
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (8 * density).toInt()
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            background = bg
            elevation = 4f * density
            isClickable = true
            isFocusable = true
            setOnClickListener {
                vibrateFeedback()
                onClick(it)
            }
        }
    }

    private fun getCameraId(facing: Int): Int {
        val numCameras = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (i in 0 until numCameras) {
            Camera.getCameraInfo(i, info)
            if (info.facing == facing) {
                return i
            }
        }
        return -1
    }

    private fun toggleBackCameraRecord() {
        if (isRecordingBack) {
            stopBackCameraRecord()
        } else {
            startBackCameraRecord()
        }
    }

    private fun toggleFrontCameraRecord() {
        if (isRecordingFront) {
            stopFrontCameraRecord()
        } else {
            startFrontCameraRecord()
        }
    }

    private fun startBackCameraRecord() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            handler.post {
                Toast.makeText(this, "Camera permission required for Spy Cam feature", Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            val cameraId = getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)
            if (cameraId == -1) {
                handler.post { Toast.makeText(this, "No back camera found", Toast.LENGTH_SHORT).show() }
                return
            }

            stopBackCameraRecord()

            val camera = Camera.open(cameraId)
            backCamera = camera

            val parameters = camera.parameters
            val supportedSizes = parameters.supportedVideoSizes ?: parameters.supportedPreviewSizes
            val videoSize = supportedSizes?.firstOrNull { it.width <= 1280 } ?: supportedSizes?.firstOrNull()
            
            if (videoSize != null) {
                parameters.setPreviewSize(videoSize.width, videoSize.height)
            }
            camera.parameters = parameters

            val dummyTexture = SurfaceTexture(10)
            camera.setPreviewTexture(dummyTexture)
            camera.startPreview()
            camera.unlock()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            backCameraRecorder = recorder

            recorder.setCamera(camera)
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            if (videoSize != null) {
                recorder.setVideoSize(videoSize.width, videoSize.height)
            } else {
                recorder.setVideoSize(640, 480)
            }
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(1500000)

            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            var spyCamDir = File(dcimDir, "spy cam")
            if (!spyCamDir.exists()) {
                spyCamDir.mkdirs()
            }
            if (!spyCamDir.exists() || !spyCamDir.canWrite()) {
                spyCamDir = File(getExternalFilesDir(null), "spy cam")
                if (!spyCamDir.exists()) {
                    spyCamDir.mkdirs()
                }
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outputFile = File(spyCamDir, "SpyCam_BACK_$timeStamp.mp4")

            recorder.setOutputFile(outputFile.absolutePath)
            
            // Set max file size based on available storage to prevent corruption (leave 50MB buffer)
            try {
                val stat = StatFs(spyCamDir.absolutePath)
                val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                if (availableBytes > 50 * 1024 * 1024) {
                    recorder.setMaxFileSize(availableBytes - (50 * 1024 * 1024))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set max file size", e)
            }
            
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    Log.i(TAG, "Max file size reached, stopping back recording")
                    handler.post {
                        Toast.makeText(this, "Storage full. Saving recording...", Toast.LENGTH_SHORT).show()
                    }
                    stopBackCameraRecord()
                }
            }
            recorder.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaRecorder error: $what, $extra")
                stopBackCameraRecord()
            }

            recorder.prepare()
            recorder.start()

            isRecordingBack = true
            handler.post {
                Toast.makeText(this, "Silent Back Recording Started", Toast.LENGTH_SHORT).show()
            }
            updateStopRecordingTile(this)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting back camera record", e)
            handler.post {
                Toast.makeText(this, "Failed to start spy camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            cleanupBackCamera()
        }
    }

    private fun stopBackCameraRecord() {
        if (!isRecordingBack) return
        try {
            backCameraRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping back camera recorder", e)
        } finally {
            backCameraRecorder = null
        }

        try {
            backCamera?.apply {
                stopPreview()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing back camera", e)
        } finally {
            backCamera = null
        }
        isRecordingBack = false
        updateStopRecordingTile(this)
        handler.post {
            Toast.makeText(this, "Silent Back Recording Saved in spy cam", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupBackCamera() {
        try {
            backCameraRecorder?.release()
        } catch (_: Exception) {}
        backCameraRecorder = null

        try {
            backCamera?.release()
        } catch (_: Exception) {}
        backCamera = null
        isRecordingBack = false
        updateStopRecordingTile(this)
    }

    private fun startFrontCameraRecord() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            handler.post {
                Toast.makeText(this, "Camera permission required for Spy Cam feature", Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            val cameraId = getCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT)
            if (cameraId == -1) {
                handler.post { Toast.makeText(this, "No front camera found", Toast.LENGTH_SHORT).show() }
                return
            }

            stopFrontCameraRecord()

            val camera = Camera.open(cameraId)
            frontCamera = camera

            val parameters = camera.parameters
            val supportedSizes = parameters.supportedVideoSizes ?: parameters.supportedPreviewSizes
            val videoSize = supportedSizes?.firstOrNull { it.width <= 1280 } ?: supportedSizes?.firstOrNull()
            
            if (videoSize != null) {
                parameters.setPreviewSize(videoSize.width, videoSize.height)
            }
            camera.parameters = parameters

            val dummyTexture = SurfaceTexture(10)
            camera.setPreviewTexture(dummyTexture)
            camera.startPreview()
            camera.unlock()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            frontCameraRecorder = recorder

            recorder.setCamera(camera)
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            if (videoSize != null) {
                recorder.setVideoSize(videoSize.width, videoSize.height)
            } else {
                recorder.setVideoSize(640, 480)
            }
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(1500000)

            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            var spyCamDir = File(dcimDir, "spy cam")
            if (!spyCamDir.exists()) {
                spyCamDir.mkdirs()
            }
            if (!spyCamDir.exists() || !spyCamDir.canWrite()) {
                spyCamDir = File(getExternalFilesDir(null), "spy cam")
                if (!spyCamDir.exists()) {
                    spyCamDir.mkdirs()
                }
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outputFile = File(spyCamDir, "SpyCam_FRONT_$timeStamp.mp4")

            recorder.setOutputFile(outputFile.absolutePath)
            
            // Set max file size based on available storage to prevent corruption (leave 50MB buffer)
            try {
                val stat = StatFs(spyCamDir.absolutePath)
                val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                if (availableBytes > 50 * 1024 * 1024) {
                    recorder.setMaxFileSize(availableBytes - (50 * 1024 * 1024))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set max file size", e)
            }
            
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    Log.i(TAG, "Max file size reached, stopping front recording")
                    handler.post {
                        Toast.makeText(this, "Storage full. Saving recording...", Toast.LENGTH_SHORT).show()
                    }
                    stopFrontCameraRecord()
                }
            }
            recorder.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaRecorder error: $what, $extra")
                stopFrontCameraRecord()
            }

            recorder.prepare()
            recorder.start()

            isRecordingFront = true
            handler.post {
                Toast.makeText(this, "Silent Front Recording Started", Toast.LENGTH_SHORT).show()
            }
            updateStopRecordingTile(this)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting front camera record", e)
            handler.post {
                Toast.makeText(this, "Failed to start spy camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            cleanupFrontCamera()
        }
    }

    private fun stopFrontCameraRecord() {
        if (!isRecordingFront) return
        try {
            frontCameraRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping front camera recorder", e)
        } finally {
            frontCameraRecorder = null
        }

        try {
            frontCamera?.apply {
                stopPreview()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing front camera", e)
        } finally {
            frontCamera = null
        }
        isRecordingFront = false
        updateStopRecordingTile(this)
        handler.post {
            Toast.makeText(this, "Silent Front Recording Saved in spy cam", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupFrontCamera() {
        try {
            frontCameraRecorder?.release()
        } catch (_: Exception) {}
        frontCameraRecorder = null

        try {
            frontCamera?.release()
        } catch (_: Exception) {}
        frontCamera = null
        isRecordingFront = false
        updateStopRecordingTile(this)
    }

    @Volatile
    private var isOverlayHidden = false
    private var cachedExcludedSet: Set<String>? = null
    private var lastExcludedPrefsCheckTime = 0L

    fun isForegroundAppExcluded(): Boolean {
        val pkg = currentForegroundPackage ?: return false
        if (pkg == packageName) return false // Never exclude our own app so we can configure it
        val now = SystemClock.uptimeMillis()
        if (cachedExcludedSet == null || now - lastExcludedPrefsCheckTime > 4000L) {
            val prefs = getSharedPreferences("excluded_apps_prefs", Context.MODE_PRIVATE)
            cachedExcludedSet = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
            lastExcludedPrefsCheckTime = now
        }
        return cachedExcludedSet?.contains(pkg) == true
    }

    private fun isCurrentWindowFullScreen(): Boolean {
        if (!notchConfig.disableInFullScreen) return false
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val wins = windows
                if (wins.isNullOrEmpty()) return false
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                // Find focused application window or the first application window
                val appWindow = wins.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
                    ?: wins.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                
                if (appWindow != null) {
                    val bounds = Rect()
                    appWindow.getBoundsInScreen(bounds)
                    // If application window is covering at least 98% of the screen width and height, it's fullscreen
                    if (bounds.width() >= screenWidth * 0.98 && bounds.height() >= screenHeight * 0.98) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking fullscreen window bounds", e)
            }
        }
        return false
    }

    private val overlayVisibilityRunnable = Runnable {
        applyOverlayVisibility()
    }

    private fun updateOverlayVisibility() {
        handler.removeCallbacks(overlayVisibilityRunnable)
        handler.postDelayed(overlayVisibilityRunnable, 60L)
    }

    private fun applyOverlayVisibility() {
        val isExcluded = isForegroundAppExcluded() || isCurrentWindowFullScreen() || !notchMasterEnabled
        isOverlayHidden = isExcluded
        handler.post {
            try {
                // Update Notch Overlay Container Layout Params
                val overlay = overlayContainer
                if (overlay != null) {
                    val params = overlay.layoutParams as? WindowManager.LayoutParams
                    if (params != null) {
                        val density = resources.displayMetrics.density
                        val widthPx = (notchConfig.widthDp * density).toInt()
                        val heightPx = (notchConfig.heightDp * density).toInt()
                        
                        if (isExcluded) {
                            if (params.width != 0 || params.height != 0) {
                                params.width = 0
                                params.height = 0
                                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                overlay.visibility = View.GONE
                                windowManager.updateViewLayout(overlay, params)
                                Log.d(TAG, "Notch overlay hidden (excluded/fullscreen)")
                            }
                        } else {
                            val isDynamic = DynamicNotchManager.isDynamicNotchEnabled.value
                            val behavior = DynamicNotchManager.activeBehavior.value
                            val activeCapsule = DynamicNotchManager.currentCapsuleEvent.value ?: DynamicNotchManager.liveActivityEvent.value
                            val targetWidth = if (isDynamic) {
                                if (activeCapsule != null) {
                                    (240 * density).toInt().coerceAtLeast(widthPx)
                                } else {
                                    widthPx.coerceAtLeast((140 * density).toInt())
                                }
                            } else {
                                widthPx
                            }
                            val targetHeight = if (isDynamic) {
                                if (activeCapsule != null && activeCapsule.actionButtons.isNotEmpty()) {
                                    (48 * density).toInt().coerceAtLeast(heightPx)
                                } else {
                                    heightPx.coerceAtLeast((36 * density).toInt())
                                }
                            } else {
                                heightPx
                            }
                            val targetFlags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                            overlay.visibility = View.VISIBLE
                            if (params.width != targetWidth || params.height != targetHeight || params.flags != targetFlags) {
                                params.width = targetWidth
                                params.height = targetHeight
                                params.flags = targetFlags
                                windowManager.updateViewLayout(overlay, params)
                                Log.d(TAG, "Notch overlay restored (dynamic=$isDynamic w=$targetWidth h=$targetHeight)")
                            }
                        }
                    }
                }

                // Update Side Deck Handle Container Layout Params
                val sideDeckHandle = sideDeckHandleContainer
                if (sideDeckHandle != null) {
                    val sParams = sideDeckHandle.layoutParams as? WindowManager.LayoutParams
                    if (sParams != null) {
                        val density = resources.displayMetrics.density
                        val widthPx = (sideDeckConfig.handleWidthDp * density).toInt().coerceIn(5, 80)
                        val heightPx = (sideDeckConfig.handleHeightDp * density).toInt().coerceIn(40, 400)
                        val blockBufferPx = if (sideDeckConfig.blockBackGesture) {
                            (sideDeckConfig.backGestureBlockBufferDp * density).toInt().coerceIn(0, 80)
                        } else {
                            0
                        }
                        val totalTouchWidthPx = widthPx + blockBufferPx

                        if (isExcluded) {
                            if (sParams.width != 0 || sParams.height != 0) {
                                sParams.width = 0
                                sParams.height = 0
                                sParams.flags = sParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                sideDeckHandle.visibility = View.GONE
                                windowManager.updateViewLayout(sideDeckHandle, sParams)
                                Log.d(TAG, "Side Deck handle hidden (excluded/fullscreen)")
                            }
                        } else {
                            if (sParams.width != totalTouchWidthPx || sParams.height != heightPx || (sParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                                sParams.width = totalTouchWidthPx
                                sParams.height = heightPx
                                sParams.flags = sParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                sideDeckHandle.visibility = View.VISIBLE
                                windowManager.updateViewLayout(sideDeckHandle, sParams)
                                Log.d(TAG, "Side Deck handle restored")
                            }
                        }
                    }
                }

                if (isExcluded) {
                    removeSideDeckPanel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateOverlayVisibility", e)
            }
        }
    }

    private fun vibrateLight() {
        if (notchConfig.vibrationStrength == 0) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10, 60))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        }
    }

    private fun launchAppInFloatingWindow(packageName: String) {
        if (packageName.isEmpty()) return
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Add MIUI specific extras for floating/freeform window
            intent.putExtra("miui.intent.extra.APPLICATION_LAUNCH_MODE", 1)
            intent.putExtra("com.android.systemui.extra.FLOATING_WINDOW", true)

            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val width = (screenWidth * 0.75).toInt()
            val height = (screenHeight * 0.60).toInt()
            val left = (screenWidth - width) / 2
            val top = (screenHeight - height) / 2
            val bounds = Rect(left, top, left + width, top + height)

            val options = android.app.ActivityOptions.makeBasic()
            try {
                options.launchBounds = bounds
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set launchBounds directly", e)
            }

            try {
                val setLaunchWindowingModeMethod = options.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                setLaunchWindowingModeMethod.invoke(options, 5) // WINDOWING_MODE_FREEFORM
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set launch windowing mode via reflection", e)
            }

            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app in floating window: $packageName", e)
            launchApp(packageName)
        }
    }

    private fun launchAppInSplitView(packageName: String) {
        if (packageName.isEmpty()) return
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or 
                             Intent.FLAG_ACTIVITY_NEW_TASK or 
                             Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch split app: $packageName", e)
            launchApp(packageName)
        }
    }

    fun toggleAutoClick() {
        if (autoClickEngine?.isRunning?.value == true) {
            autoClickEngine?.stop()
        } else {
            autoClickEngine?.start()
        }
    }

    fun toggleAutoClickFloatingMenu() {
        serviceScope.launch {
            val current = repository.autoClickConfigFlow.firstOrNull() ?: AutoClickConfigEntity()
            val newVisibility = !current.isMenuVisible
            repository.updateAutoClickConfig(current.copy(isMenuVisible = newVisibility))
            vibrateSoft()
        }
    }

    fun vibrateSoft() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(20L)
        }
    }

    fun vibrateHeavy() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator.vibrate(60L)
        }
    }

    fun showAutoClickNotification() {
        val channelId = "autoclick_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "AutoClicker",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val stopIntent = android.content.Intent(this, com.example.service.AutoClickActionReceiver::class.java).apply {
            action = "STOP_AUTOCLICK"
        }
        val stopPendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("AutoClick Studio Running")
            .setContentText("Tap to stop")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .build()
        val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1002, notification)
    }

    fun hideAutoClickNotification() {
        val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(1002)
    }
}
