package com.example.service

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.example.MainActivity
import com.example.R
import com.example.data.AutoClickConfigEntity
import com.example.data.AutoClickPointEntity
import com.example.data.NotchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class AutoClickOverlay(
    private val service: AccessibilityService,
    private val repository: NotchRepository,
    private val scope: CoroutineScope,
    private val engine: AutoClickEngine
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var config = AutoClickConfigEntity()
    private var points = emptyList<AutoClickPointEntity>()
    private var isRunning = false

    private var panelView: View? = null
    private var miniSettingsView: View? = null
    private var modeSelectionView: View? = null
    private var targetDialogView: View? = null
    private var swipeConnectorOverlay: SwipeConnectorOverlayView? = null
    private var countdownOverlayView: View? = null

    private var pointMarkers = mutableMapOf<Int, View>()
    private var swipeEndMarkers = mutableMapOf<Int, View>()

    private var observerJob: Job? = null
    private var autoFoldJob: Job? = null
    private var isFolded = false
    private var isDockedToEdge = false

    val isPanelShowing: Boolean
        get() = panelView != null

    fun isDeviceLockedOrScreenOff(): Boolean {
        val pm = service.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        if (!isInteractive) return true

        val km = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private val lockscreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.post {
                        if (isRunning) {
                            engine.stop("Screen turned off - Auto-click stopped for safety")
                        }
                        dismissCountdownOverlay()
                        removePanel()
                        removeMarkers()
                        dismissMiniSettings()
                        dismissModeSelectionDialog()
                        dismissTargetDialog()
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    handler.post {
                        checkAndRestoreOverlayIfUnlocked()
                    }
                    handler.postDelayed({
                        checkAndRestoreOverlayIfUnlocked()
                    }, 300L)
                }
            }
        }
    }

    fun checkAndRestoreOverlayIfUnlocked() {
        if (!isDeviceLockedOrScreenOff() && config.isMenuVisible) {
            if (panelView == null) {
                createPanel()
            }
            recreatePointMarkers()
        }
    }

    fun ensureVisible() {
        handler.post {
            checkAndRestoreOverlayIfUnlocked()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            service.resources.displayMetrics
        ).toInt()
    }

    private fun getScreenWidth(): Int {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels
    }

    private fun getScreenHeight(): Int {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm.heightPixels
    }

    init {
        val lockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(lockscreenReceiver, lockFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            service.registerReceiver(lockscreenReceiver, lockFilter)
        }

        observerJob = scope.launch {
            launch {
                repository.autoClickConfigFlow.collectLatest { newConfig ->
                    val wasVisible = config.isMenuVisible
                    val oldConfig = config
                    config = newConfig

                    handler.post {
                        if (config.isMenuVisible) {
                            if (panelView == null) {
                                createPanel()
                                syncPointMarkers()
                            } else {
                                updatePanelPosition()
                                if (oldConfig.menuSize != newConfig.menuSize) {
                                    createPanel()
                                }
                                if (oldConfig.targetSize != newConfig.targetSize ||
                                    oldConfig.selectedSkin != newConfig.selectedSkin ||
                                    oldConfig.targetTransparency != newConfig.targetTransparency ||
                                    oldConfig.showPointMarkers != newConfig.showPointMarkers ||
                                    oldConfig.clickMode != newConfig.clickMode
                                ) {
                                    syncPointMarkers()
                                }
                            }
                        } else {
                            if (wasVisible) {
                                // Reset targets on menu close as requested by user
                                scope.launch { resetTargetsToSavedPreset() }
                            }
                            removePanel()
                            removeMarkers()
                            dismissMiniSettings()
                            dismissModeSelectionDialog()
                            dismissTargetDialog()
                        }
                    }
                }
            }
            launch {
                repository.autoClickPointsFlow.collectLatest { newPoints ->
                    points = newPoints
                    handler.post {
                        syncPointMarkers()
                    }
                }
            }
            launch {
                engine.isRunning.collectLatest { running ->
                    isRunning = running
                    handler.post {
                        updatePlayPauseState()
                        if (running && config.foldMenu) {
                            foldMenu(true)
                        }
                    }
                }
            }
            launch {
                engine.countdown.collectLatest { count ->
                    handler.post {
                        updateCountdownVisual(count)
                    }
                }
            }
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (!config.showInLandscape && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            removePanel()
            removeMarkers()
            dismissMiniSettings()
            dismissModeSelectionDialog()
            dismissTargetDialog()
        } else if (config.isMenuVisible) {
            handler.post {
                updatePanelDimensions()
                recreatePointMarkers()
            }
        }
    }

    // =========================================================================
    // PRESET SERIALIZATION & TARGET RESET ON MENU CLOSE
    // =========================================================================

    private fun serializePoints(pts: List<AutoClickPointEntity>): String {
        val arr = JSONArray()
        for (p in pts) {
            val obj = JSONObject().apply {
                put("x", p.xPx)
                put("y", p.yPx)
                put("endX", p.endXPx)
                put("endY", p.endYPx)
                put("type", p.gestureType)
                put("delay", p.delayAfterMs)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializePoints(json: String): List<AutoClickPointEntity> {
        val list = mutableListOf<AutoClickPointEntity>()
        if (json.isBlank()) return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AutoClickPointEntity(
                        id = 0,
                        xPx = obj.optInt("x", getScreenWidth() / 2),
                        yPx = obj.optInt("y", getScreenHeight() / 2),
                        endXPx = obj.optInt("endX", 0),
                        endYPx = obj.optInt("endY", 0),
                        gestureType = obj.optString("type", "SINGLE_TAP"),
                        delayAfterMs = obj.optLong("delay", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveCurrentPreset() {
        val json = serializePoints(points)
        scope.launch {
            repository.updateAutoClickConfig(config.copy(savedPresetPoints = json))
        }
        handler.post {
            Toast.makeText(service, "Preset saved! (${points.size} targets saved for next time)", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun resetTargetsToSavedPreset() {
        if (config.savedPresetPoints.isNotBlank()) {
            val preset = deserializePoints(config.savedPresetPoints)
            repository.clearAutoClickPoints()
            for (p in preset) {
                repository.insertAutoClickPoint(p)
            }
        } else {
            repository.clearAutoClickPoints()
            if (config.clickMode == "SINGLE") {
                val cx = getScreenWidth() / 2
                val cy = getScreenHeight() / 2
                repository.insertAutoClickPoint(
                    AutoClickPointEntity(xPx = cx, yPx = cy, gestureType = "SINGLE_TAP")
                )
            }
        }
    }

    private fun closeFloatingMenu() {
        engine.stop("Menu closed by user")
        scope.launch {
            resetTargetsToSavedPreset()
            repository.updateAutoClickConfig(config.copy(isMenuVisible = false))
        }
        handler.post {
            Toast.makeText(service, "Auto-click menu closed", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // FLOATING MENU PANEL (Capsule / Fit Icon Collapsible)
    // =========================================================================

    private fun createPanel() {
        removePanel()
        if (isDeviceLockedOrScreenOff()) return

        val iconSizeDp = when (config.menuSize) {
            "SMALL" -> 20f
            "BIG" -> 28f
            else -> 24f
        }
        val buttonPaddingDp = when (config.menuSize) {
            "SMALL" -> 6f
            "BIG" -> 12f
            else -> 8f
        }
        val pillHeightDp = when (config.menuSize) {
            "SMALL" -> 40f
            "BIG" -> 56f
            else -> 48f
        }

        val pillLayout = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(pillHeightDp / 2f).toFloat()
                setColor(Color.parseColor("#EE212328"))
                setStroke(dpToPx(1.5f), Color.parseColor("#4A4F5D"))
            }
            background = bg
            val hPad = dpToPx(if (isFolded) 4f else 8f)
            val vPad = dpToPx(if (isFolded) 4f else 4f)
            setPadding(hPad, vPad, hPad, vPad)
        }

        fun createIconButton(drawableRes: Int, tooltip: String, onClick: (View) -> Unit): ImageView {
            return ImageView(service).apply {
                setImageResource(drawableRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val p = dpToPx(buttonPaddingDp)
                setPadding(p, p, p, p)
                val s = dpToPx(iconSizeDp + (buttonPaddingDp * 2))
                layoutParams = LinearLayout.LayoutParams(s, s)
                contentDescription = tooltip
                setOnClickListener(onClick)
            }
        }

        // 1. Play / Pause button (always visible, with emergency instant-stop on touch down)
        val playBtn = createIconButton(
            if (isRunning) R.drawable.ic_autoclick_pause else R.drawable.ic_autoclick_play,
            "Start or Stop AutoClick"
        ) {
            resetAutoFoldTimer()
            (service as? NotchAccessibilityService)?.toggleAutoClick()
        }
        playBtn.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isRunning) {
                // Emergency instant-stop on finger contact down
                (service as? NotchAccessibilityService)?.toggleAutoClick()
                return@setOnTouchListener true
            }
            false
        }
        playBtn.setOnLongClickListener {
            if (isFolded) foldMenu(false) else foldMenu(true)
            true
        }
        pillLayout.addView(playBtn)

        // 2. Collapse / Expand toggle icon right next to Play/Pause
        val foldToggleBtn = ImageView(service).apply {
            setImageResource(if (isFolded) R.drawable.ic_autoclick_expand else R.drawable.ic_autoclick_collapse)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val s = dpToPx(iconSizeDp + (buttonPaddingDp * 2))
            val p = dpToPx(buttonPaddingDp)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(s, s)
            contentDescription = if (isFolded) "Expand Menu" else "Collapse Menu"
            setOnClickListener {
                foldMenu(!isFolded)
            }
        }
        pillLayout.addView(foldToggleBtn)

        // Container for other tools that hide when folded
        val toolsContainer = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (isFolded) View.GONE else View.VISIBLE
        }

        // 3. Plus icon (Add target according to mode)
        val plusBtn = createIconButton(R.drawable.ic_autoclick_plus, "Add target") {
            resetAutoFoldTimer()
            val w = getScreenWidth()
            val h = getScreenHeight()

            when (config.clickMode) {
                "SINGLE" -> {
                    if (points.isEmpty()) {
                        scope.launch {
                            repository.insertAutoClickPoint(
                                AutoClickPointEntity(xPx = w / 2, yPx = h / 2, gestureType = "SINGLE_TAP")
                            )
                        }
                    } else {
                        Toast.makeText(service, "Single Area mode active. Tap target to set delay.", Toast.LENGTH_SHORT).show()
                    }
                }
                "SWIPE" -> {
                    // When swipe option selected, plus icon adds two targets for Start and End of swipe (eg. 1:2, 3:4...)
                    val swipeCount = points.count { it.gestureType == "SWIPE" }
                    val offset = (swipeCount % 4) * dpToPx(36f)
                    val startX = (w / 2) + offset
                    val startY = (h / 2) - dpToPx(80f) + offset
                    val endX = (w / 2) + offset
                    val endY = (h / 2) + dpToPx(80f) + offset

                    scope.launch {
                        repository.insertAutoClickPoint(
                            AutoClickPointEntity(
                                xPx = startX,
                                yPx = startY,
                                endXPx = endX,
                                endYPx = endY,
                                gestureType = "SWIPE"
                            )
                        )
                        val startNum = swipeCount * 2 + 1
                        val endNum = swipeCount * 2 + 2
                        handler.post {
                            Toast.makeText(service, "Added Swipe #${swipeCount + 1} ($startNum:$endNum)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                else -> { // MULTI
                    val offset = (points.size % 6) * dpToPx(24f)
                    val newX = (w / 2) + offset
                    val newY = (h / 2) + offset
                    scope.launch {
                        repository.insertAutoClickPoint(
                            AutoClickPointEntity(xPx = newX, yPx = newY, gestureType = "SINGLE_TAP")
                        )
                        handler.post {
                            Toast.makeText(service, "Target #${points.size + 1} added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        toolsContainer.addView(plusBtn)

        // 5. Minus icon (Remove recent target)
        val minusBtn = createIconButton(R.drawable.ic_autoclick_minus, "Remove recent target") {
            resetAutoFoldTimer()
            scope.launch {
                if (points.isNotEmpty()) {
                    repository.deleteLastAutoClickPoint()
                    handler.post {
                        Toast.makeText(service, "Removed recent target", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    handler.post {
                        Toast.makeText(service, "No targets to remove", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        toolsContainer.addView(minusBtn)

        // 6. Save icon (Save preset - only saves when user explicitly clicks this)
        val saveBtn = createIconButton(R.drawable.ic_autoclick_save, "Save preset") {
            resetAutoFoldTimer()
            saveCurrentPreset()
        }
        toolsContainer.addView(saveBtn)

        // 7. Eye icon (Toggle visibility of targets)
        val eyeBtn = createIconButton(
            if (config.showPointMarkers) R.drawable.ic_autoclick_eye else R.drawable.ic_autoclick_eye_off,
            "Show or hide targets"
        ) {
            resetAutoFoldTimer()
            val newVis = !config.showPointMarkers
            scope.launch {
                repository.updateAutoClickConfig(config.copy(showPointMarkers = newVis))
            }
            (it as ImageView).setImageResource(
                if (newVis) R.drawable.ic_autoclick_eye else R.drawable.ic_autoclick_eye_off
            )
        }
        toolsContainer.addView(eyeBtn)

        // 8. Setting icon (Open mini settings popup over overlay)
        val settingsBtn = createIconButton(R.drawable.ic_autoclick_settings, "Quick settings") {
            resetAutoFoldTimer()
            showMiniSettings()
        }
        toolsContainer.addView(settingsBtn)

        // 9. 4-directional arrow icon (Drag handle to move floating menu pill)
        val moveBtn = createIconButton(R.drawable.ic_autoclick_move, "Move menu") {}.apply {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            setOnTouchListener { _, event ->
                val lp = panelView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchX = event.rawX
                        touchY = event.rawY
                        resetAutoFoldTimer()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (event.rawX - touchX).toInt()
                        lp.y = startY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(panelView, lp)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val screenW = getScreenWidth()
                        if (config.minimizeToEdge) {
                            val targetX: Int
                            if (lp.x < dpToPx(40f)) {
                                targetX = dpToPx(4f)
                                isDockedToEdge = true
                            } else if (lp.x > screenW - dpToPx(160f)) {
                                targetX = screenW - dpToPx(50f)
                                isDockedToEdge = true
                            } else {
                                targetX = lp.x
                                isDockedToEdge = false
                            }
                            if (targetX != lp.x) {
                                val startXPos = lp.x
                                val animator = ValueAnimator.ofInt(startXPos, targetX).apply {
                                    duration = 200
                                    interpolator = DecelerateInterpolator(1.5f)
                                    addUpdateListener { va ->
                                        lp.x = va.animatedValue as Int
                                        try {
                                            windowManager.updateViewLayout(panelView, lp)
                                        } catch (_: Exception) {}
                                    }
                                }
                                animator.start()
                            }
                        }
                        scope.launch {
                            repository.updateAutoClickConfig(config.copy(panelXPx = lp.x, panelYPx = lp.y))
                        }
                        resetAutoFoldTimer()
                        true
                    }
                    else -> false
                }
            }
        }
        toolsContainer.addView(moveBtn)

        // 10. Close icon for closing the floating menu (explicit user requirement)
        val closeBtn = createIconButton(R.drawable.ic_autoclick_close, "Close floating menu") {
            closeFloatingMenu()
        }
        toolsContainer.addView(closeBtn)

        pillLayout.addView(toolsContainer)

        panelView = FrameLayout(service).apply {
            addView(pillLayout)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.panelXPx
            y = config.panelYPx
        }

        try {
            windowManager.addView(panelView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        resetAutoFoldTimer()
    }

    private fun foldMenu(fold: Boolean) {
        isFolded = fold
        panelView?.let { root ->
            val pill = (root as? FrameLayout)?.getChildAt(0) as? LinearLayout ?: return
            val foldBtn = pill.getChildAt(1) as? ImageView
            val tools = pill.getChildAt(2) as? LinearLayout ?: return

            tools.visibility = if (fold) View.GONE else View.VISIBLE
            foldBtn?.setImageResource(if (fold) R.drawable.ic_autoclick_expand else R.drawable.ic_autoclick_collapse)
            foldBtn?.contentDescription = if (fold) "Expand Menu" else "Collapse Menu"

            // Adjust padding so menu tightly converts to fit that icon
            val hPad = dpToPx(if (fold) 4f else 8f)
            val vPad = dpToPx(if (fold) 4f else 4f)
            pill.setPadding(hPad, vPad, hPad, vPad)

            val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            try {
                windowManager.updateViewLayout(root, lp)
            } catch (e: Exception) {}
        }
    }

    private fun resetAutoFoldTimer() {
        autoFoldJob?.cancel()
        if (config.foldMenu && !isRunning) {
            autoFoldJob = scope.launch {
                delay(4000)
                handler.post {
                    if (config.foldMenu && !isRunning) {
                        foldMenu(true)
                    }
                }
            }
        }
    }

    private fun updatePlayPauseState() {
        panelView?.let { root ->
            val pill = (root as? FrameLayout)?.getChildAt(0) as? LinearLayout ?: return
            val playBtn = pill.getChildAt(0) as? ImageView ?: return
            playBtn.setImageResource(
                if (isRunning) R.drawable.ic_autoclick_pause else R.drawable.ic_autoclick_play
            )
        }
    }

    private fun updateCountdownVisual(count: Int) {
        if (count <= 0) {
            dismissCountdownOverlay()
            return
        }

        if (countdownOverlayView == null) {
            val tv = TextView(service).apply {
                text = "$count"
                textSize = 72f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#D9000000"))
                    setStroke(dpToPx(4f), Color.parseColor("#448AFF"))
                }
                background = bg
                val sizePx = dpToPx(130f)
                layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
                elevation = dpToPx(16f).toFloat()
            }

            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            val params = WindowManager.LayoutParams(
                dpToPx(130f),
                dpToPx(130f),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(tv, params)
                countdownOverlayView = tv
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            (countdownOverlayView as? TextView)?.apply {
                text = "$count"
                scaleX = 1.35f
                scaleY = 1.35f
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start()
            }
        }
    }

    private fun dismissCountdownOverlay() {
        countdownOverlayView?.let { v ->
            try {
                if (v.isAttachedToWindow) {
                    windowManager.removeViewImmediate(v)
                } else {
                    windowManager.removeView(v)
                }
            } catch (e: Exception) {
                try { windowManager.removeView(v) } catch (ignored: Exception) {}
            }
        }
        countdownOverlayView = null
    }

    private fun updatePanelPosition() {
        val lp = panelView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.x != config.panelXPx || lp.y != config.panelYPx) {
            lp.x = config.panelXPx
            lp.y = config.panelYPx
            try {
                windowManager.updateViewLayout(panelView, lp)
            } catch (e: Exception) {}
        }
    }

    private fun updatePanelDimensions() {
        updatePanelPosition()
    }

    private fun removePanel() {
        panelView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                try {
                    windowManager.removeView(view)
                } catch (ignored: Exception) {}
            }
        }
        panelView = null
    }

    // =========================================================================
    // MODE SELECTION DIALOG (Single Area, Multi Area, Swipe)
    // =========================================================================

    private fun showModeSelectionDialog() {
        dismissModeSelectionDialog()
        if (isDeviceLockedOrScreenOff()) return

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18f).toFloat()
                setColor(Color.parseColor("#F51E2024"))
                setStroke(dpToPx(1.5f), Color.parseColor("#3F4452"))
            }
            background = bg
            val p = dpToPx(16f)
            setPadding(p, p, p, p)
            elevation = dpToPx(12f).toFloat()
        }

        // Header
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val titleView = TextView(service).apply {
            text = "Select AutoClick Mode"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(service).apply {
            setImageResource(R.drawable.ic_autoclick_close)
            val s = dpToPx(28f)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            setOnClickListener { dismissModeSelectionDialog() }
        }
        header.addView(titleView)
        header.addView(closeBtn)
        card.addView(header)

        card.addView(View(service).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1f)).apply {
                setMargins(0, dpToPx(10f), 0, dpToPx(12f))
            }
            setBackgroundColor(Color.parseColor("#2F3542"))
        })

        fun createModeRow(
            iconRes: Int,
            title: String,
            desc: String,
            modeCode: String,
            isSelected: Boolean,
            onSelect: () -> Unit
        ): LinearLayout {
            return LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(12f).toFloat()
                    setColor(if (isSelected) Color.parseColor("#33448AFF") else Color.parseColor("#1A283038"))
                    setStroke(dpToPx(1.5f), if (isSelected) Color.parseColor("#448AFF") else Color.parseColor("#334A4F5D"))
                }
                background = bg
                val pad = dpToPx(12f)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(10f)
                }

                val icon = ImageView(service).apply {
                    setImageResource(iconRes)
                    val s = dpToPx(28f)
                    layoutParams = LinearLayout.LayoutParams(s, s)
                }
                val textCol = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dpToPx(12f)
                    }
                    val t = TextView(service).apply {
                        text = title
                        setTextColor(if (isSelected) Color.parseColor("#448AFF") else Color.WHITE)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val d = TextView(service).apply {
                        text = desc
                        setTextColor(Color.parseColor("#90A4AE"))
                        textSize = 12f
                    }
                    addView(t)
                    addView(d)
                }
                addView(icon)
                addView(textCol)
                setOnClickListener {
                    onSelect()
                    dismissModeSelectionDialog()
                }
            }
        }

        // Option 1: Single Area
        card.addView(
            createModeRow(
                R.drawable.ic_autoclick_mode_single,
                "Single Area",
                "Autoclick on a single selected target",
                "SINGLE",
                config.clickMode == "SINGLE"
            ) {
                switchMode("SINGLE")
            }
        )

        // Option 2: Multi Area
        card.addView(
            createModeRow(
                R.drawable.ic_autoclick_mode_multi,
                "Multi Area",
                "Autoclick on multiple targets in sequence (1, 2, 3...)",
                "MULTI",
                config.clickMode == "MULTI"
            ) {
                switchMode("MULTI")
            }
        )

        // Option 3: Swipe
        card.addView(
            createModeRow(
                R.drawable.ic_autoclick_mode_swipe,
                "Swipe",
                "Swiping on screen with Start & End targets (1:2, 3:4...)",
                "SWIPE",
                config.clickMode == "SWIPE"
            ) {
                switchMode("SWIPE")
            }
        )

        modeSelectionView = card

        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val params = WindowManager.LayoutParams(
            dpToPx(320f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(modeSelectionView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun switchMode(newMode: String) {
        if (config.clickMode == newMode) return
        scope.launch {
            val isMulti = newMode != "SINGLE"
            val updatedConfig = config.copy(clickMode = newMode, isMultiMode = isMulti)
            repository.updateAutoClickConfig(updatedConfig)

            when (newMode) {
                "SINGLE" -> {
                    val current = repository.getAutoClickPointsDirect()
                    if (current.isEmpty()) {
                        val cx = getScreenWidth() / 2
                        val cy = getScreenHeight() / 2
                        repository.insertAutoClickPoint(
                            AutoClickPointEntity(xPx = cx, yPx = cy, gestureType = "SINGLE_TAP")
                        )
                    } else if (current.size > 1) {
                        repository.clearAutoClickPoints()
                        val first = current.first().copy(gestureType = "SINGLE_TAP")
                        repository.insertAutoClickPoint(first)
                    }
                    handler.post {
                        Toast.makeText(service, "Switched to Single Area mode", Toast.LENGTH_SHORT).show()
                        createPanel()
                    }
                }
                "MULTI" -> {
                    handler.post {
                        Toast.makeText(service, "Switched to Multi Area mode", Toast.LENGTH_SHORT).show()
                        createPanel()
                    }
                }
                "SWIPE" -> {
                    val current = repository.getAutoClickPointsDirect()
                    val hasSwipe = current.any { it.gestureType == "SWIPE" }
                    if (!hasSwipe) {
                        val cx = getScreenWidth() / 2
                        val cy = getScreenHeight() / 2
                        repository.insertAutoClickPoint(
                            AutoClickPointEntity(
                                xPx = cx,
                                yPx = cy - dpToPx(80f),
                                endXPx = cx,
                                endYPx = cy + dpToPx(80f),
                                gestureType = "SWIPE"
                            )
                        )
                    }
                    handler.post {
                        Toast.makeText(service, "Switched to Swipe mode (1:2)", Toast.LENGTH_SHORT).show()
                        createPanel()
                    }
                }
            }
        }
    }

    private fun dismissModeSelectionDialog() {
        modeSelectionView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        modeSelectionView = null
    }

    // =========================================================================
    // TARGET CLICK CONFIG OVERLAY (CRASH FIX & SHOW CLICK DELAY)
    // =========================================================================

    private fun showTargetConfigOverlay(
        point: AutoClickPointEntity,
        displayNumber: Int,
        isSwipe: Boolean = false,
        pairName: String = ""
    ) {
        dismissTargetDialog()
        if (isDeviceLockedOrScreenOff()) return

        var currentDelay = if (point.delayAfterMs >= 100L) point.delayAfterMs else config.delayMs.coerceAtLeast(100L)
        var selectedType = point.gestureType

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18f).toFloat()
                setColor(Color.parseColor("#F51E2024"))
                setStroke(dpToPx(1.5f), Color.parseColor("#3F4452"))
            }
            background = bg
            val p = dpToPx(16f)
            setPadding(p, p, p, p)
            elevation = dpToPx(12f).toFloat()
        }

        // Header
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val titleView = TextView(service).apply {
            text = if (isSwipe && pairName.isNotBlank()) "Swipe ($pairName)" else "Target #$displayNumber"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(service).apply {
            setImageResource(R.drawable.ic_autoclick_close)
            val s = dpToPx(28f)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            setOnClickListener { dismissTargetDialog() }
        }
        header.addView(titleView)
        header.addView(closeBtn)
        card.addView(header)

        // Divider
        card.addView(View(service).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1f)).apply {
                setMargins(0, dpToPx(10f), 0, dpToPx(12f))
            }
            setBackgroundColor(Color.parseColor("#2F3542"))
        })

        // 1. Target Click Delay (The user explicitly requested: "when i click on it shows that targets click delay")
        val delaySectionTitle = TextView(service).apply {
            text = "Target Click Delay"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 13f
        }
        card.addView(delaySectionTitle)

        val delayDisplayRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6f)
            }
        }
        val delayValueTxt = TextView(service).apply {
            text = "$currentDelay ms"
            setTextColor(Color.parseColor("#448AFF"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        fun updateDelayText(newDelay: Long) {
            if (newDelay < 100L) {
                currentDelay = 100L
                Toast.makeText(service, "Safety rule: 100ms is the minimum safe delay so you can stop auto-click anytime", Toast.LENGTH_SHORT).show()
            } else {
                currentDelay = newDelay
            }
            delayValueTxt.text = "$currentDelay ms"
        }

        val minus50Btn = Button(service).apply {
            text = "-50"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dpToPx(52f), dpToPx(38f))
            setOnClickListener { updateDelayText(currentDelay - 50L) }
        }
        val plus50Btn = Button(service).apply {
            text = "+50"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dpToPx(52f), dpToPx(38f))
            setOnClickListener { updateDelayText(currentDelay + 50L) }
        }
        val plus100Btn = Button(service).apply {
            text = "+100"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dpToPx(56f), dpToPx(38f))
            setOnClickListener { updateDelayText(currentDelay + 100L) }
        }

        delayDisplayRow.addView(delayValueTxt)
        delayDisplayRow.addView(minus50Btn)
        delayDisplayRow.addView(plus50Btn)
        delayDisplayRow.addView(plus100Btn)
        card.addView(delayDisplayRow)

        // Quick presets row: safe presets 100, 200, 300, 500, 1000 ms
        val presetRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
            }
        }
        val presets = listOf(100L, 200L, 300L, 500L, 1000L)
        for (ms in presets) {
            val chip = Button(service).apply {
                text = "$ms"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(34f), 1f).apply {
                    setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
                }
                setOnClickListener { updateDelayText(ms) }
            }
            presetRow.addView(chip)
        }
        card.addView(presetRow)

        // Safety explanation rule badge
        val safetyHint = TextView(service).apply {
            text = "Safety Rule: Delays under 100ms are restricted to prevent screen touches from locking up"
            setTextColor(Color.parseColor("#FFB74D"))
            textSize = 10.5f
            setPadding(0, dpToPx(4f), 0, dpToPx(6f))
        }
        card.addView(safetyHint)

        // Gesture Type options if not forced swipe
        if (config.clickMode != "SWIPE" && !isSwipe) {
            val typeTitle = TextView(service).apply {
                text = "Gesture Type"
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(12f)
                }
            }
            card.addView(typeTitle)

            val typeRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(6f)
                }
            }
            val types = listOf(
                "SINGLE_TAP" to "Tap",
                "DOUBLE_TAP" to "Double",
                "LONG_PRESS" to "Hold"
            )
            for ((tCode, tLabel) in types) {
                val btn = Button(service).apply {
                    text = tLabel
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(34f), 1f).apply {
                        setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
                    }
                    if (selectedType == tCode) {
                        setBackgroundColor(Color.parseColor("#2979FF"))
                    }
                    setOnClickListener {
                        selectedType = tCode
                        for (i in 0 until typeRow.childCount) {
                            val c = typeRow.getChildAt(i) as? Button
                            c?.setBackgroundColor(Color.parseColor("#37474F"))
                        }
                        setBackgroundColor(Color.parseColor("#2979FF"))
                    }
                }
                typeRow.addView(btn)
            }
            card.addView(typeRow)
        }

        // Action buttons: Delete Target & Save
        val actionRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
        }
        val deleteBtn = Button(service).apply {
            text = "Delete"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42f), 1f).apply {
                marginEnd = dpToPx(6f)
            }
            setOnClickListener {
                scope.launch { repository.deleteAutoClickPoint(point.id) }
                dismissTargetDialog()
                handler.post {
                    Toast.makeText(service, "Target removed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val saveBtn = Button(service).apply {
            text = "Save Delay"
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42f), 1.4f).apply {
                marginStart = dpToPx(6f)
            }
            setOnClickListener {
                scope.launch {
                    repository.updateAutoClickPoint(
                        point.copy(
                            delayAfterMs = currentDelay,
                            gestureType = selectedType
                        )
                    )
                }
                dismissTargetDialog()
                handler.post {
                    Toast.makeText(service, "Target delay set to $currentDelay ms", Toast.LENGTH_SHORT).show()
                }
            }
        }
        actionRow.addView(deleteBtn)
        actionRow.addView(saveBtn)
        card.addView(actionRow)

        targetDialogView = card

        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val params = WindowManager.LayoutParams(
            dpToPx(300f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(targetDialogView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissTargetDialog() {
        targetDialogView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        targetDialogView = null
    }

    // =========================================================================
    // MINI SETTINGS POPUP OVERLAY
    // =========================================================================

    private fun showMiniSettings() {
        if (miniSettingsView != null) {
            dismissMiniSettings()
            return
        }
        if (isDeviceLockedOrScreenOff()) return

        val popup = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16f).toFloat()
                setColor(Color.parseColor("#F0212328"))
                setStroke(dpToPx(1.5f), Color.parseColor("#4A4F5D"))
            }
            background = bg
            val p = dpToPx(14f)
            setPadding(p, p, p, p)
            elevation = dpToPx(10f).toFloat()
        }

        // Header with Close
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val title = TextView(service).apply {
                text = "AutoClick Settings"
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val closeBtn = ImageView(service).apply {
                setImageResource(R.drawable.ic_autoclick_close)
                val s = dpToPx(24f)
                layoutParams = LinearLayout.LayoutParams(s, s)
                setOnClickListener { dismissMiniSettings() }
            }
            addView(title)
            addView(closeBtn)
        }
        popup.addView(header)

        // Mode Selection Section (Moved here from floating menu as explicitly requested)
        val modeTitle = TextView(service).apply {
            text = "AutoClick Mode"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(10f), 0, dpToPx(6f))
        }
        popup.addView(modeTitle)

        val modeRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createModeItem(title: String, iconRes: Int, modeCode: String): LinearLayout {
            val isSelected = config.clickMode == modeCode
            return LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(52f), 1f).apply {
                    setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(10f).toFloat()
                    if (isSelected) {
                        setColor(Color.parseColor("#29448AFF"))
                        setStroke(dpToPx(1.5f), Color.parseColor("#448AFF"))
                    } else {
                        setColor(Color.parseColor("#15FFFFFF"))
                        setStroke(dpToPx(1f), Color.parseColor("#2FFFFFFF"))
                    }
                }
                background = bg

                val iv = ImageView(service).apply {
                    setImageResource(iconRes)
                    val isz = dpToPx(20f)
                    layoutParams = LinearLayout.LayoutParams(isz, isz)
                    setColorFilter(if (isSelected) Color.parseColor("#448AFF") else Color.WHITE)
                }
                val tv = TextView(service).apply {
                    text = title
                    textSize = 10.5f
                    typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(if (isSelected) Color.parseColor("#448AFF") else Color.parseColor("#CCCCCC"))
                    setPadding(0, dpToPx(2f), 0, 0)
                }
                addView(iv)
                addView(tv)

                setOnClickListener {
                    switchMode(modeCode)
                    dismissMiniSettings()
                }
            }
        }

        modeRow.addView(createModeItem("Single", R.drawable.ic_autoclick_mode_single, "SINGLE"))
        modeRow.addView(createModeItem("Multi", R.drawable.ic_autoclick_mode_multi, "MULTI"))
        modeRow.addView(createModeItem("Swipe", R.drawable.ic_autoclick_mode_swipe, "SWIPE"))
        popup.addView(modeRow)

        // Delay Section with Safety Rules
        val delayRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
            }
            val label = TextView(service).apply {
                text = "Default Delay:"
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valueTxt = TextView(service).apply {
                text = "${max(100L, config.delayMs)} ms"
                setTextColor(Color.parseColor("#448AFF"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minWidth = dpToPx(65f)
                setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
            }
            val minus = Button(service).apply {
                text = "-"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(36f))
                setOnClickListener {
                    val current = max(100L, config.delayMs)
                    val newDelay = max(100L, current - 50L)
                    if (newDelay == current) {
                        Toast.makeText(service, "Safety rule: 100ms is the minimum safe delay", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch { repository.updateAutoClickConfig(config.copy(delayMs = newDelay, intervalMs = newDelay)) }
                        valueTxt.text = "$newDelay ms"
                    }
                }
            }
            val plus = Button(service).apply {
                text = "+"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(36f))
                setOnClickListener {
                    val newDelay = max(100L, config.delayMs) + 50L
                    scope.launch { repository.updateAutoClickConfig(config.copy(delayMs = newDelay, intervalMs = newDelay)) }
                    valueTxt.text = "$newDelay ms"
                }
            }
            addView(label)
            addView(minus)
            addView(valueTxt)
            addView(plus)
        }
        popup.addView(delayRow)

        // Safety note
        val safetyNote = TextView(service).apply {
            text = "Safety Rule: Min safe delay is 100ms to allow stopping"
            setTextColor(Color.parseColor("#FFB74D"))
            textSize = 10.5f
            setPadding(0, dpToPx(3f), 0, dpToPx(4f))
        }
        popup.addView(safetyNote)

        // Sliding time stepper
        val slideRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6f)
            }
            val label = TextView(service).apply {
                text = "Slide Duration:"
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valueTxt = TextView(service).apply {
                text = "${config.slidingTimeMs} ms"
                setTextColor(Color.parseColor("#00E676"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minWidth = dpToPx(65f)
                setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
            }
            val minus = Button(service).apply {
                text = "-"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(36f))
                setOnClickListener {
                    val newSlide = max(300L, config.slidingTimeMs - 50L)
                    scope.launch { repository.updateAutoClickConfig(config.copy(slidingTimeMs = newSlide)) }
                    valueTxt.text = "$newSlide ms"
                }
            }
            val plus = Button(service).apply {
                text = "+"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(36f))
                setOnClickListener {
                    val newSlide = config.slidingTimeMs + 50L
                    scope.launch { repository.updateAutoClickConfig(config.copy(slidingTimeMs = newSlide)) }
                    valueTxt.text = "$newSlide ms"
                }
            }
            addView(label)
            addView(minus)
            addView(valueTxt)
            addView(plus)
        }
        popup.addView(slideRow)

        // Button to open full settings in MainActivity
        val fullSettingsBtn = Button(service).apply {
            text = "More Settings..."
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(38f)
            ).apply {
                topMargin = dpToPx(10f)
            }
            setOnClickListener {
                dismissMiniSettings()
                val intent = Intent(service, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                service.startActivity(intent)
            }
        }
        popup.addView(fullSettingsBtn)

        miniSettingsView = popup

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            dpToPx(280f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(dpToPx(16f), config.panelXPx - dpToPx(40f))
            y = max(dpToPx(60f), config.panelYPx - dpToPx(200f))
        }

        try {
            windowManager.addView(miniSettingsView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissMiniSettings() {
        miniSettingsView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        miniSettingsView = null
    }

    // =========================================================================
    // TARGET MARKERS & SWIPE CONNECTORS RENDERING
    // =========================================================================

    private fun recreatePointMarkers() {
        syncPointMarkers()
    }

    private fun syncPointMarkers() {
        if (isDeviceLockedOrScreenOff() || !config.isMenuVisible || !config.showPointMarkers) {
            removeMarkers()
            return
        }

        val targetSizeDp = when (config.targetSize) {
            "SMALL" -> 40f
            "BIG" -> 72f
            else -> 56f
        }
        val sizePx = dpToPx(targetSizeDp)
        val halfSize = sizePx / 2

        // Swipe connector overlay handling without removing and re-adding
        val hasSwipes = points.any { it.gestureType == "SWIPE" } || config.clickMode == "SWIPE"
        if (hasSwipes) {
            if (swipeConnectorOverlay == null) {
                val connector = SwipeConnectorOverlayView(
                    context = service,
                    getPoints = { points },
                    isRunningCheck = { isRunning },
                    getMode = { config.clickMode }
                )
                val connParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                try {
                    windowManager.addView(connector, connParams)
                    swipeConnectorOverlay = connector
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                swipeConnectorOverlay?.invalidate()
            }
        } else {
            swipeConnectorOverlay?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
            swipeConnectorOverlay = null
        }

        // Clean up markers for points that were deleted
        val currentPointIds = points.map { it.id }.toSet()
        val removedMarkerIds = pointMarkers.keys.filter { it !in currentPointIds }
        for (id in removedMarkerIds) {
            pointMarkers.remove(id)?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
            swipeEndMarkers.remove(id)?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
        }

        var swipeCounter = 0
        points.forEachIndexed { index, point ->
            val isSwipe = point.gestureType == "SWIPE" || config.clickMode == "SWIPE"
            val startNumber = if (isSwipe) (swipeCounter * 2 + 1) else (index + 1)
            val endNumber = if (isSwipe) (swipeCounter * 2 + 2) else 0
            val pairLabel = if (isSwipe) "$startNumber:$endNumber" else ""

            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (isRunning) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }

            // Start marker: update in-place if already exists
            val existingMarker = pointMarkers[point.id] as? TargetMarkerView
            if (existingMarker != null) {
                var needsInvalidate = false
                if (existingMarker.pointNumber != startNumber) {
                    existingMarker.pointNumber = startNumber
                    needsInvalidate = true
                }
                if (existingMarker.skin != config.selectedSkin) {
                    existingMarker.skin = config.selectedSkin
                    needsInvalidate = true
                }
                if (existingMarker.markerAlpha != config.targetTransparency) {
                    existingMarker.markerAlpha = config.targetTransparency
                    needsInvalidate = true
                }
                if (existingMarker.sizePx != sizePx) {
                    existingMarker.sizePx = sizePx
                    existingMarker.requestLayout()
                    needsInvalidate = true
                }
                val badge = if (isSwipe) "S" else ""
                if (existingMarker.pairBadge != badge) {
                    existingMarker.pairBadge = badge
                    needsInvalidate = true
                }
                if (needsInvalidate) existingMarker.invalidate()

                val lp = existingMarker.layoutParams as? WindowManager.LayoutParams
                if (lp != null) {
                    val targetX = point.xPx - halfSize
                    val targetY = point.yPx - halfSize
                    if (lp.x != targetX || lp.y != targetY || lp.width != sizePx || lp.height != sizePx || lp.flags != flags) {
                        lp.x = targetX
                        lp.y = targetY
                        lp.width = sizePx
                        lp.height = sizePx
                        lp.flags = flags
                        try { windowManager.updateViewLayout(existingMarker, lp) } catch (_: Exception) {}
                    }
                }
            } else {
                val marker = TargetMarkerView(
                    context = service,
                    pointNumber = startNumber,
                    skin = config.selectedSkin,
                    markerAlpha = config.targetTransparency,
                    sizePx = sizePx,
                    isSwipeEnd = false,
                    pairBadge = if (isSwipe) "S" else ""
                ).apply {
                    if (!isRunning) {
                        var startX = 0
                        var startY = 0
                        var touchX = 0f
                        var touchY = 0f
                        var isClick = false

                        setOnTouchListener { v, event ->
                            val lp = layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    startX = lp.x
                                    startY = lp.y
                                    touchX = event.rawX
                                    touchY = event.rawY
                                    isClick = true
                                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = (event.rawX - touchX).toInt()
                                    val dy = (event.rawY - touchY).toInt()
                                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isClick = false
                                    lp.x = startX + dx
                                    lp.y = startY + dy
                                    windowManager.updateViewLayout(v, lp)
                                    swipeConnectorOverlay?.invalidate()
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                                    if (isClick) {
                                        showTargetConfigOverlay(point, startNumber, isSwipe, pairLabel)
                                    } else {
                                        scope.launch {
                                            repository.updateAutoClickPoint(
                                                point.copy(xPx = lp.x + halfSize, yPx = lp.y + halfSize)
                                            )
                                        }
                                    }
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                }

                val params = WindowManager.LayoutParams(
                    sizePx, sizePx,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = point.xPx - halfSize
                    y = point.yPx - halfSize
                }

                try {
                    windowManager.addView(marker, params)
                    pointMarkers[point.id] = marker
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // End marker for swipe
            if (isSwipe) {
                val ex = if (point.endXPx != 0) point.endXPx else point.xPx
                val ey = if (point.endYPx != 0) point.endYPx else point.yPx + dpToPx(160f)

                val existingEndMarker = swipeEndMarkers[point.id] as? TargetMarkerView
                if (existingEndMarker != null) {
                    var needsInvalidate = false
                    if (existingEndMarker.pointNumber != endNumber) {
                        existingEndMarker.pointNumber = endNumber
                        needsInvalidate = true
                    }
                    if (existingEndMarker.skin != config.selectedSkin) {
                        existingEndMarker.skin = config.selectedSkin
                        needsInvalidate = true
                    }
                    if (existingEndMarker.markerAlpha != config.targetTransparency) {
                        existingEndMarker.markerAlpha = config.targetTransparency
                        needsInvalidate = true
                    }
                    if (existingEndMarker.sizePx != sizePx) {
                        existingEndMarker.sizePx = sizePx
                        existingEndMarker.requestLayout()
                        needsInvalidate = true
                    }
                    if (needsInvalidate) existingEndMarker.invalidate()

                    val lp = existingEndMarker.layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        val targetX = ex - halfSize
                        val targetY = ey - halfSize
                        if (lp.x != targetX || lp.y != targetY || lp.width != sizePx || lp.height != sizePx || lp.flags != flags) {
                            lp.x = targetX
                            lp.y = targetY
                            lp.width = sizePx
                            lp.height = sizePx
                            lp.flags = flags
                            try { windowManager.updateViewLayout(existingEndMarker, lp) } catch (_: Exception) {}
                        }
                    }
                } else {
                    val endMarker = TargetMarkerView(
                        context = service,
                        pointNumber = endNumber,
                        skin = config.selectedSkin,
                        markerAlpha = config.targetTransparency,
                        sizePx = sizePx,
                        isSwipeEnd = true,
                        pairBadge = "E"
                    ).apply {
                        if (!isRunning) {
                            var startX = 0
                            var startY = 0
                            var touchX = 0f
                            var touchY = 0f
                            var isClick = false

                            setOnTouchListener { v, event ->
                                val lp = layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        startX = lp.x
                                        startY = lp.y
                                        touchX = event.rawX
                                        touchY = event.rawY
                                        isClick = true
                                        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                                        true
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        val dx = (event.rawX - touchX).toInt()
                                        val dy = (event.rawY - touchY).toInt()
                                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isClick = false
                                        lp.x = startX + dx
                                        lp.y = startY + dy
                                        windowManager.updateViewLayout(v, lp)
                                        swipeConnectorOverlay?.invalidate()
                                        true
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                                        if (isClick) {
                                            showTargetConfigOverlay(point, endNumber, true, pairLabel)
                                        } else {
                                            scope.launch {
                                                repository.updateAutoClickPoint(
                                                    point.copy(endXPx = lp.x + halfSize, endYPx = lp.y + halfSize)
                                                )
                                            }
                                        }
                                        true
                                    }
                                    MotionEvent.ACTION_CANCEL -> {
                                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                    }

                    val endParams = WindowManager.LayoutParams(
                        sizePx, sizePx,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        flags,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = ex - halfSize
                        y = ey - halfSize
                    }

                    try {
                        windowManager.addView(endMarker, endParams)
                        swipeEndMarkers[point.id] = endMarker
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                swipeCounter++
            } else {
                swipeEndMarkers.remove(point.id)?.let { view ->
                    try {
                        windowManager.removeView(view)
                    } catch (_: Exception) {}
                }
            }
        }
        swipeConnectorOverlay?.invalidate()
    }

    private fun removeMarkers() {
        pointMarkers.values.forEach { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {}
        }
        pointMarkers.clear()
        swipeEndMarkers.values.forEach { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {}
        }
        swipeEndMarkers.clear()
        swipeConnectorOverlay?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {}
        }
        swipeConnectorOverlay = null
    }

    fun destroy() {
        try {
            service.unregisterReceiver(lockscreenReceiver)
        } catch (e: Exception) {}
        observerJob?.cancel()
        autoFoldJob?.cancel()
        dismissCountdownOverlay()
        removePanel()
        dismissMiniSettings()
        dismissModeSelectionDialog()
        dismissTargetDialog()
        removeMarkers()
    }

    // =========================================================================
    // SWIPE CONNECTOR OVERLAY VIEW (Draws 1:2, 3:4 lines with directional arrow)
    // =========================================================================
    class SwipeConnectorOverlayView(
        context: Context,
        private val getPoints: () -> List<AutoClickPointEntity>,
        private val isRunningCheck: () -> Boolean,
        private val getMode: () -> String
    ) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#448AFF")
            strokeWidth = 5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
        }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2979FF")
            style = Paint.Style.FILL
        }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EE1E2024")
            style = Paint.Style.FILL
        }
        private val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#448AFF")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val mode = getMode()
            val allPoints = getPoints()
            var swipeIdx = 0

            for (point in allPoints) {
                val isSwipe = point.gestureType == "SWIPE" || mode == "SWIPE"
                if (!isSwipe) continue

                val sx = point.xPx.toFloat()
                val sy = point.yPx.toFloat()
                val ex = (if (point.endXPx != 0) point.endXPx else point.xPx).toFloat()
                val ey = (if (point.endYPx != 0) point.endYPx else point.yPx + 300).toFloat()

                // Draw connecting dashed line
                canvas.drawLine(sx, sy, ex, ey, linePaint)

                // Draw arrowhead pointing from Start to End
                val angle = Math.atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val arrowLen = 24.0
                val arrowSpread = Math.PI / 6.0
                val arrowPath = Path().apply {
                    moveTo(ex, ey)
                    lineTo(
                        (ex - arrowLen * Math.cos(angle - arrowSpread)).toFloat(),
                        (ey - arrowLen * Math.sin(angle - arrowSpread)).toFloat()
                    )
                    lineTo(
                        (ex - arrowLen * Math.cos(angle + arrowSpread)).toFloat(),
                        (ey - arrowLen * Math.sin(angle + arrowSpread)).toFloat()
                    )
                    close()
                }
                canvas.drawPath(arrowPath, arrowPaint)

                // Draw center badge showing "1:2", "3:4", etc.
                val midX = (sx + ex) / 2f
                val midY = (sy + ey) / 2f
                val sNum = swipeIdx * 2 + 1
                val eNum = swipeIdx * 2 + 2
                val badgeText = "$sNum:$eNum"
                val bw = 60f
                val bh = 32f
                val rect = RectF(midX - bw / 2f, midY - bh / 2f, midX + bw / 2f, midY + bh / 2f)
                canvas.drawRoundRect(rect, 12f, 12f, badgePaint)
                canvas.drawRoundRect(rect, 12f, 12f, badgeStroke)

                val tb = Rect()
                textPaint.getTextBounds(badgeText, 0, badgeText.length, tb)
                canvas.drawText(badgeText, midX, midY + (tb.height() / 2f), textPaint)

                swipeIdx++
            }
        }
    }

    // =========================================================================
    // CUSTOM VIEW FOR RETICLE SKINS & NUMBERING
    // =========================================================================
    class TargetMarkerView(
        context: Context,
        var pointNumber: Int,
        var skin: String,
        var markerAlpha: Float,
        var sizePx: Int,
        var isSwipeEnd: Boolean = false,
        var pairBadge: String = ""
    ) : View(context) {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            alpha = markerAlpha.coerceIn(0.2f, 1.0f)

            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val r = (w / 2f) - 6f

            when (skin) {
                "RED_CROSS" -> {
                    strokePaint.color = Color.BLACK
                    strokePaint.strokeWidth = 6f
                    canvas.drawCircle(cx, cy, r * 0.6f, strokePaint)
                    strokePaint.color = Color.parseColor("#E53935")
                    strokePaint.strokeWidth = 6f
                    canvas.drawLine(cx, cy - r * 0.6f, cx, cy - r, strokePaint)
                    canvas.drawLine(cx, cy + r * 0.6f, cx, cy + r, strokePaint)
                    canvas.drawLine(cx - r * 0.6f, cy, cx - r, cy, strokePaint)
                    canvas.drawLine(cx + r * 0.6f, cy, cx + r, cy, strokePaint)
                    fillPaint.color = Color.parseColor("#CC000000")
                    canvas.drawCircle(cx, cy, r * 0.35f, fillPaint)
                }
                "GREEN_TARGET" -> {
                    fillPaint.color = Color.parseColor("#43A047")
                    canvas.drawCircle(cx, cy, r * 0.9f, fillPaint)
                    strokePaint.color = Color.WHITE
                    strokePaint.strokeWidth = 5f
                    canvas.drawCircle(cx, cy, r * 0.5f, strokePaint)
                    fillPaint.color = Color.BLACK
                    canvas.drawCircle(cx, cy, r * 0.25f, fillPaint)
                }
                "ORANGE_BRACKET" -> {
                    strokePaint.color = Color.parseColor("#FF6D00")
                    strokePaint.strokeWidth = 6f
                    val pad = r * 0.2f
                    val bLen = r * 0.35f
                    canvas.drawLine(cx - r + pad, cy - r + pad, cx - r + pad + bLen, cy - r + pad, strokePaint)
                    canvas.drawLine(cx - r + pad, cy - r + pad, cx - r + pad, cy - r + pad + bLen, strokePaint)
                    canvas.drawLine(cx + r - pad, cy - r + pad, cx + r - pad - bLen, cy - r + pad, strokePaint)
                    canvas.drawLine(cx + r - pad, cy - r + pad, cx + r - pad, cy - r + pad + bLen, strokePaint)
                    canvas.drawLine(cx - r + pad, cy + r - pad, cx - r + pad + bLen, cy + r - pad, strokePaint)
                    canvas.drawLine(cx - r + pad, cy + r - pad, cx - r + pad, cy + r - pad - bLen, strokePaint)
                    canvas.drawLine(cx + r - pad, cy + r - pad, cx + r - pad - bLen, cy + r - pad, strokePaint)
                    canvas.drawLine(cx + r - pad, cy + r - pad, cx + r - pad, cy + r - pad - bLen, strokePaint)
                    fillPaint.color = Color.parseColor("#CCFF6D00")
                    canvas.drawCircle(cx, cy, r * 0.35f, fillPaint)
                }
                "BLUE_LIGHTBULB" -> {
                    fillPaint.shader = RadialGradient(cx, cy, r, Color.parseColor("#448AFF"), Color.parseColor("#1565C0"), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, r * 0.85f, fillPaint)
                    fillPaint.shader = null
                    fillPaint.color = Color.WHITE
                    canvas.drawCircle(cx - r * 0.4f, cy - r * 0.4f, 4f, fillPaint)
                    canvas.drawCircle(cx + r * 0.4f, cy - r * 0.2f, 5f, fillPaint)
                    canvas.drawCircle(cx - r * 0.3f, cy + r * 0.4f, 3f, fillPaint)
                }
                "RED_STAR" -> {
                    fillPaint.shader = RadialGradient(cx, cy, r, Color.parseColor("#FF5252"), Color.parseColor("#C62828"), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, r * 0.85f, fillPaint)
                    fillPaint.shader = null
                }
                "GREEN_HEX" -> {
                    fillPaint.shader = RadialGradient(cx, cy, r, Color.parseColor("#69F0AE"), Color.parseColor("#2E7D32"), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, r * 0.85f, fillPaint)
                    fillPaint.shader = null
                }
                "ORANGE_DIAMOND" -> {
                    fillPaint.shader = RadialGradient(cx, cy, r, Color.parseColor("#FFB74D"), Color.parseColor("#E65100"), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, r * 0.85f, fillPaint)
                    fillPaint.shader = null
                }
                "ORANGE_SUN" -> {
                    fillPaint.color = Color.parseColor("#FF6D00")
                    canvas.drawCircle(cx, cy, r * 0.45f, fillPaint)
                    for (i in 0 until 8) {
                        val angle = (i * Math.PI / 4).toFloat()
                        val dotX = cx + (r * 0.75f * Math.cos(angle.toDouble())).toFloat()
                        val dotY = cy + (r * 0.75f * Math.sin(angle.toDouble())).toFloat()
                        canvas.drawCircle(dotX, dotY, r * 0.12f, fillPaint)
                    }
                }
                "RAINBOW" -> {
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = 6f
                    strokePaint.color = Color.parseColor("#2979FF")
                    canvas.drawArc(RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f), 180f, 180f, false, strokePaint)
                    strokePaint.color = Color.parseColor("#FF1744")
                    canvas.drawArc(RectF(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f), 180f, 180f, false, strokePaint)
                }
                "CYAN_SNOW" -> {
                    strokePaint.color = Color.parseColor("#00E5FF")
                    strokePaint.strokeWidth = 5f
                    for (i in 0 until 6) {
                        val angle = (i * Math.PI / 3).toFloat()
                        val ex = cx + (r * 0.85f * Math.cos(angle.toDouble())).toFloat()
                        val ey = cy + (r * 0.85f * Math.sin(angle.toDouble())).toFloat()
                        canvas.drawLine(cx, cy, ex, ey, strokePaint)
                    }
                }
                "YELLOW_MOON" -> {
                    fillPaint.color = Color.parseColor("#FFD600")
                    canvas.drawCircle(cx, cy, r * 0.8f, fillPaint)
                    fillPaint.color = Color.parseColor("#212328")
                    canvas.drawCircle(cx + r * 0.35f, cy - r * 0.2f, r * 0.7f, fillPaint)
                }
                else -> { // DEFAULT: Black circle with 4 blue ticks
                    strokePaint.color = Color.BLACK
                    strokePaint.strokeWidth = 6f
                    canvas.drawCircle(cx, cy, r * 0.6f, strokePaint)
                    strokePaint.color = Color.parseColor("#2979FF")
                    strokePaint.strokeWidth = 6f
                    canvas.drawLine(cx, cy - r * 0.6f, cx, cy - r, strokePaint)
                    canvas.drawLine(cx, cy + r * 0.6f, cx, cy + r, strokePaint)
                    canvas.drawLine(cx - r * 0.6f, cy, cx - r, cy, strokePaint)
                    canvas.drawLine(cx + r * 0.6f, cy, cx + r, cy, strokePaint)
                    fillPaint.color = Color.parseColor("#CC000000")
                    canvas.drawCircle(cx, cy, r * 0.35f, fillPaint)
                }
            }

            // Draw sequence number
            textPaint.textSize = (r * 0.5f).coerceAtLeast(20f)
            val label = "$pointNumber"
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawText(label, cx, cy + (textBounds.height() / 2f), textPaint)
        }
    }
}
