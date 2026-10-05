package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.data.AutoClickConfigEntity
import com.example.data.AutoClickPointEntity
import com.example.data.NotchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

class AutoClickEngine(
    private val service: AccessibilityService,
    private val repository: NotchRepository,
    private val scope: CoroutineScope
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _clicksThisSession = MutableStateFlow(0)
    val clicksThisSession: StateFlow<Int> = _clicksThisSession

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown

    private var config = AutoClickConfigEntity()
    private var points = emptyList<AutoClickPointEntity>()
    
    private var clickJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    val overlay = AutoClickOverlay(service, repository, scope, this)

    val isOverlayShowing: Boolean
        get() = overlay.isPanelShowing

    fun ensureOverlayVisible() {
        overlay.ensureVisible()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_USER_BACKGROUND) {
                if (_isRunning.value) {
                    stop("Device locked - Auto-click stopped for safety")
                    handler.post {
                        Toast.makeText(service, "Auto-click stopped for device safety (device locked)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_BACKGROUND)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            service.registerReceiver(screenOffReceiver, filter)
        }

        scope.launch {
            launch {
                repository.autoClickConfigFlow.collectLatest { config = it }
            }
            launch {
                repository.autoClickPointsFlow.collectLatest { points = it }
            }
        }
    }

    fun start() {
        if (_isRunning.value) return
        
        var activePoints = points.filter { it.enabled }
        if (activePoints.isEmpty()) {
            scope.launch {
                val cx = 540
                val cy = 960
                repository.insertAutoClickPoint(AutoClickPointEntity(xPx = cx, yPx = cy, gestureType = "SINGLE_TAP"))
                handler.post {
                    android.widget.Toast.makeText(service, "Target #1 created at center! Starting...", android.widget.Toast.LENGTH_SHORT).show()
                }
                delay(300)
                start()
            }
            return
        }

        _isRunning.value = true
        _clicksThisSession.value = 0
        (service as? NotchAccessibilityService)?.showAutoClickNotification()
        
        clickJob = scope.launch {
            if (config.countdownEnabled) {
                for (i in 3 downTo 1) {
                    _countdown.value = i
                    (service as? NotchAccessibilityService)?.vibrateSoft()
                    delay(1000)
                }
                _countdown.value = 0
                (service as? NotchAccessibilityService)?.vibrateHeavy()
            } else {
                _countdown.value = 0
            }
            
            val startTime = System.currentTimeMillis()
            val stopDurationMs: Long = when (config.stopConditionType) {
                "TIME" -> {
                    val totalSec = config.stopHours * 3600L + config.stopMinutes * 60L + config.stopSeconds
                    if (totalSec <= 0) 300_000L else totalSec * 1000L
                }
                else -> Long.MAX_VALUE
            }
            val targetLoops = if (config.stopConditionType == "LOOPS") config.stopLoopCount.coerceAtLeast(1) else Int.MAX_VALUE
            var currentLoop = 0

            while (_isRunning.value) {
                val km = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (km?.isKeyguardLocked == true) {
                    stop("Device locked - Auto-click stopped for safety")
                    handler.post {
                        Toast.makeText(service, "Auto-click stopped for safety (device locked)", Toast.LENGTH_SHORT).show()
                    }
                    break
                }

                if (config.stopConditionType == "TIME" && System.currentTimeMillis() - startTime >= stopDurationMs) {
                    stop("Time limit reached")
                    break
                }
                if (config.stopConditionType == "LOOPS" && currentLoop >= targetLoops) {
                    stop("Loop count reached")
                    break
                }

                val currentActive = points.filter { it.enabled }
                if (currentActive.isEmpty()) {
                    delay(200)
                    continue
                }

                if (config.clickMode == "SINGLE") {
                    val point = currentActive.first()
                    if (!_isRunning.value) break
                    val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (keyguard?.isKeyguardLocked == true) {
                        stop("Device locked - Auto-click stopped for safety")
                        break
                    }
                    dispatchGesture(point)
                    _clicksThisSession.value += 1
                    val rawDelay = if (point.delayAfterMs > 0) point.delayAfterMs else config.delayMs
                    val pointDelay = rawDelay.coerceAtLeast(100L)
                    delay(pointDelay)
                    // Safety rule: Guarantee a touch breathing gap every 5 fast clicks (< 200ms)
                    // so physical touch events and the stop button are never locked out
                    if (pointDelay < 200L && _clicksThisSession.value % 5 == 0) {
                        delay(120L)
                    }
                } else {
                    for (point in currentActive) {
                        if (!_isRunning.value) break
                        val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (keyguard?.isKeyguardLocked == true) {
                            stop("Device locked - Auto-click stopped for safety")
                            break
                        }
                        dispatchGesture(point)
                        _clicksThisSession.value += 1
                        val rawDelay = if (point.delayAfterMs > 0) point.delayAfterMs else config.delayMs
                        val pointDelay = rawDelay.coerceAtLeast(100L)
                        delay(pointDelay)
                        if (pointDelay < 200L && _clicksThisSession.value % 5 == 0) {
                            delay(120L)
                        }
                    }
                }
                currentLoop++
            }
        }
    }

    fun stop(reason: String = "User stopped") {
        if (!_isRunning.value) return
        _isRunning.value = false
        clickJob?.cancel()
        _countdown.value = 0
        (service as? NotchAccessibilityService)?.hideAutoClickNotification()
        (service as? NotchAccessibilityService)?.vibrateSoft()
        Log.d("AutoClickEngine", "Stopped: $reason")
    }

    private fun dispatchGesture(point: AutoClickPointEntity) {
        val path = Path().apply { moveTo(point.xPx.toFloat(), point.yPx.toFloat()) }
        val stroke: GestureDescription.StrokeDescription
        val isSwipe = point.gestureType == "SWIPE" || config.clickMode == "SWIPE"
        
        if (isSwipe) {
            val ex = if (point.endXPx != 0) point.endXPx.toFloat() else point.xPx.toFloat()
            val ey = if (point.endYPx != 0) point.endYPx.toFloat() else (point.yPx + 300).toFloat()
            path.lineTo(ex, ey)
            val slideTime = config.slidingTimeMs.coerceAtLeast(300L)
            stroke = GestureDescription.StrokeDescription(path, 0, slideTime)
        } else {
            when (point.gestureType) {
                "LONG_PRESS" -> {
                    stroke = GestureDescription.StrokeDescription(path, 0, max(600L, point.delayAfterMs))
                }
                "DOUBLE_TAP" -> {
                    stroke = GestureDescription.StrokeDescription(path, 0, 30)
                    val stroke2 = GestureDescription.StrokeDescription(path, 80, 30)
                    val gesture = GestureDescription.Builder().addStroke(stroke).addStroke(stroke2).build()
                    service.dispatchGesture(gesture, null, null)
                    return
                }
                else -> { // SINGLE_TAP: 25ms stroke allows quick touch dispatch and prevents input pipeline freeze
                    stroke = GestureDescription.StrokeDescription(path, 0, 25)
                }
            }
        }
        
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    fun onAppChanged(pkg: String) {
        // We can check package excludes here if necessary.
    }
    
    fun enterCaptureMode(gestureType: String) {
        val cx = 540
        val cy = 960
        scope.launch {
            repository.insertAutoClickPoint(
                AutoClickPointEntity(xPx = cx, yPx = cy, gestureType = gestureType)
            )
            repository.updateAutoClickConfig(config.copy(isMenuVisible = true))
        }
    }

    fun destroy() {
        stop("Destroyed")
        overlay.destroy()
        try {
            service.unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}
    }
}
