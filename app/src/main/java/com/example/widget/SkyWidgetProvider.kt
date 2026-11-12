package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.util.GameMode
import com.example.util.NightSaver
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Home-screen widget host for the widget engine.
 * Renders pixel-perfect bitmaps matching launcher dimensions and binds customizable click actions
 * to both the widget container and individual components.
 */
class SkyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, manager, it) }
        WidgetTicker.start(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        updateOne(context, manager, appWidgetId)
    }

    override fun onEnabled(context: Context) = WidgetTicker.start(context)

    override fun onDisabled(context: Context) = WidgetTicker.stop(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStore.delete(context, it) }
        WidgetAssets.cleanupImages(context, WidgetStore.referencedImages(context))
        val manager = AppWidgetManager.getInstance(context)
        val remaining = manager.getAppWidgetIds(ComponentName(context, SkyWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            WidgetTicker.stop(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetTicker.ACTION,
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_USER_PRESENT -> {
                tick(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun tick(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SkyWidgetProvider::class.java))
        if (ids.isEmpty()) return
        if (isPaused(context)) {
            // Game Mode / Night Battery Saver: avoid rendering, schedule cheap re-check
            WidgetTicker.scheduleNext(context, 10 * 60_000L)
            return
        }
        ids.forEach { updateOne(context, manager, it) }
        WidgetTicker.scheduleNext(context)
    }

    companion object {
        private const val RESPECT_NIGHT_SAVER = true

        private const val DEFAULT_W_DP = 250f
        private const val DEFAULT_H_DP = 110f
        private const val MAX_PIXELS = 1_500_000f

        // Slot IDs matching res/layout/sky_widget.xml
        private val SLOT_IDS = intArrayOf(
            R.id.widget_slot_0, R.id.widget_slot_1, R.id.widget_slot_2, R.id.widget_slot_3,
            R.id.widget_slot_4, R.id.widget_slot_5, R.id.widget_slot_6, R.id.widget_slot_7,
            R.id.widget_slot_8, R.id.widget_slot_9, R.id.widget_slot_10, R.id.widget_slot_11,
            R.id.widget_slot_12, R.id.widget_slot_13, R.id.widget_slot_14, R.id.widget_slot_15
        )

        private fun isPaused(context: Context): Boolean =
            GameMode.isActive() || (RESPECT_NIGHT_SAVER && NightSaver.isActive(context))

        /**
         * Returns the exact size of the placed widget in dp as reported by the launcher.
         * On Android 12+ (API 31+), reads OPTION_APPWIDGET_SIZES for pixel-perfect matching.
         */
        fun sizeDp(context: Context, manager: AppWidgetManager, id: Int): Pair<Float, Float> {
            val o = manager.getAppWidgetOptions(id) ?: return DEFAULT_W_DP to DEFAULT_H_DP
            val land = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            // 1. Android 12+ (API 31+): exact SizeF list from launcher grid
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = try {
                    o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                } catch (_: Throwable) {
                    @Suppress("DEPRECATION")
                    o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                }
                if (!sizes.isNullOrEmpty()) {
                    val matched = if (land) {
                        sizes.maxByOrNull { it.width } ?: sizes.first()
                    } else {
                        sizes.minByOrNull { it.width } ?: sizes.first()
                    }
                    if (matched.width > 0f && matched.height > 0f) {
                        return matched.width to matched.height
                    }
                }
            }

            // 2. Standard widget options
            val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)

            val w = if (land) {
                if (maxW > 0) maxW else minW
            } else {
                if (minW > 0) minW else maxW
            }
            val h = if (land) {
                if (minH > 0) minH else maxH
            } else {
                if (maxH > 0) maxH else minH
            }

            val finalW = if (w > 0) w.toFloat() else DEFAULT_W_DP
            val finalH = if (h > 0) h.toFloat() else DEFAULT_H_DP
            return finalW to finalH
        }

        fun updateOne(context: Context, id: Int) =
            updateOne(context, AppWidgetManager.getInstance(context), id)

        fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val app = context.applicationContext
            val layout = WidgetStore.load(app, id)
            val (wDp, hDp) = sizeDp(app, manager, id)
            val density = app.resources.displayMetrics.density

            var wPx = (wDp * density)
            var hPx = (hDp * density)
            var dp = density
            val pixels = wPx * hPx
            var scaleDown = 1f
            if (pixels > MAX_PIXELS) {
                scaleDown = sqrt(MAX_PIXELS / pixels)
                wPx *= scaleDown; hPx *= scaleDown; dp *= scaleDown
            }

            val bmp = WidgetRenderer.renderBitmap(app, layout, wPx.roundToInt(), hPx.roundToInt(), dp)
            val views = RemoteViews(app.packageName, R.layout.sky_widget)
            views.setImageViewBitmap(R.id.sky_widget_image, bmp)

            // 1. Root / Widget background click action.
            // The action chosen on the Background component itself covers the whole widget, so it is
            // used first; otherwise the "blank area" action (when that switch is on).
            val backgroundComponentAction = layout.components
                .firstOrNull {
                    it.visible && it.type == ComponentType.BACKGROUND &&
                        it.clickAction.actionType.isNotBlank() && it.clickAction.actionType != "NONE"
                }?.clickAction
            val rootAction: WidgetClickAction? = when {
                backgroundComponentAction != null -> backgroundComponentAction
                layout.enableBackgroundClick -> layout.clickAction
                else -> null
            }
            if (rootAction != null) {
                if (rootAction.actionType == "OPEN_EDITOR" || rootAction.actionType.isBlank()) {
                    views.setOnClickPendingIntent(R.id.sky_widget_root, editorIntent(app, id))
                } else if (rootAction.actionType != "NONE") {
                    views.setOnClickPendingIntent(
                        R.id.sky_widget_root,
                        WidgetActionReceiver.createPendingIntent(app, id, "ROOT", rootAction)
                    )
                }
            }

            // 2. Individual component click touch targets (Android 12+ API 31+)
            val clickableComponents = layout.components.filter {
                it.visible && it.type != ComponentType.BACKGROUND && it.clickAction.actionType != "NONE"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && clickableComponents.isNotEmpty()) {
                try {
                    val boxes = WidgetRenderer.computeComponentBoxes(layout, wPx, hPx, dp)
                    var slotIndex = 0

                    for (c in clickableComponents) {
                        if (slotIndex >= SLOT_IDS.size) break
                        val box = boxes[c.id] ?: continue
                        val slotId = SLOT_IDS[slotIndex++]

                        // Small components (e.g. the battery text) would be very hard to hit, so every touch
                        // target is at least 40dp in each direction, kept inside the widget.
                        val realW = wDp * density
                        val realH = hDp * density
                        val minTouch = 40f * density
                        var widthPx = (box.width() / scaleDown).coerceAtLeast(1f)
                        var heightPx = (box.height() / scaleDown).coerceAtLeast(1f)
                        var leftPx = (box.left / scaleDown).coerceAtLeast(0f)
                        var topPx = (box.top / scaleDown).coerceAtLeast(0f)
                        if (widthPx < minTouch) {
                            leftPx -= (minTouch - widthPx) / 2f
                            widthPx = minTouch
                        }
                        if (heightPx < minTouch) {
                            topPx -= (minTouch - heightPx) / 2f
                            heightPx = minTouch
                        }
                        widthPx = widthPx.coerceAtMost(realW)
                        heightPx = heightPx.coerceAtMost(realH)
                        leftPx = leftPx.coerceIn(0f, (realW - widthPx).coerceAtLeast(0f))
                        topPx = topPx.coerceIn(0f, (realH - heightPx).coerceAtLeast(0f))

                        views.setViewVisibility(slotId, View.VISIBLE)
                        views.setViewLayoutMargin(slotId, RemoteViews.MARGIN_LEFT, leftPx, TypedValue.COMPLEX_UNIT_PX)
                        views.setViewLayoutMargin(slotId, RemoteViews.MARGIN_TOP, topPx, TypedValue.COMPLEX_UNIT_PX)
                        views.setViewLayoutWidth(slotId, widthPx, TypedValue.COMPLEX_UNIT_PX)
                        views.setViewLayoutHeight(slotId, heightPx, TypedValue.COMPLEX_UNIT_PX)
                        views.setOnClickPendingIntent(
                            slotId,
                            WidgetActionReceiver.createPendingIntent(app, id, c.id, c.clickAction)
                        )
                    }

                    // Hide any remaining unused overlay slots
                    while (slotIndex < SLOT_IDS.size) {
                        val slotId = SLOT_IDS[slotIndex++]
                        views.setViewVisibility(slotId, View.GONE)
                    }
                } catch (_: Throwable) {
                    for (slotId in SLOT_IDS) {
                        views.setViewVisibility(slotId, View.GONE)
                    }
                }
            } else {
                // Hide touch target slots
                for (slotId in SLOT_IDS) {
                    views.setViewVisibility(slotId, View.GONE)
                }
                // Android 11 and older cannot place a touch target per component, so a tap anywhere on
                // the widget runs the first component action instead of doing nothing.
                if (rootAction == null && clickableComponents.isNotEmpty()) {
                    views.setOnClickPendingIntent(
                        R.id.sky_widget_root,
                        WidgetActionReceiver.createPendingIntent(app, id, "ROOT", clickableComponents.first().clickAction)
                    )
                }
            }

            manager.updateAppWidget(id, views)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, SkyWidgetProvider::class.java))
                .forEach { updateOne(context, manager, it) }
        }

        fun editorIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, WidgetEditorActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("skywidget://edit/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/**
 * High-precision widget clock ticker.
 * Updates seamlessly with system time using:
 * 1. BroadcastReceiver listening for ACTION_TIME_TICK (exact :00 minute boundary),
 *    ACTION_SCREEN_ON / USER_PRESENT (instant refresh on device wake),
 *    ACTION_TIME_CHANGED & ACTION_TIMEZONE_CHANGED.
 * 2. Main thread Handler loop targeting the exact next minute boundary.
 * 3. AlarmManager fallback so the widget stays accurate in sleep.
 */
object WidgetTicker {
    const val ACTION = "com.skytouch.sk.action.WIDGET_TICK"
    private const val REQUEST_CODE = 7301

    private val handler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false
    private var isHandlerRunning = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            val app = appContext ?: return
            SkyWidgetProvider.updateAll(app)
            scheduleHandler(app)
        }
    }

    private var appContext: Context? = null

    private val systemTimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    SkyWidgetProvider.updateAll(context)
                    scheduleHandler(context)
                }
            }
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app

        // Register real-time system broadcasts if not yet registered
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(systemTimeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    app.registerReceiver(systemTimeReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (_: Exception) {}
        }

        scheduleHandler(app)
        scheduleNext(app)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        if (isReceiverRegistered) {
            try {
                app.unregisterReceiver(systemTimeReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        handler.removeCallbacks(tickRunnable)
        isHandlerRunning = false
        cancel(app)
    }

    private fun scheduleHandler(context: Context) {
        handler.removeCallbacks(tickRunnable)
        val now = System.currentTimeMillis()
        val delay = 60_000L - (now % 60_000L) + 25L
        handler.postDelayed(tickRunnable, delay)
        isHandlerRunning = true
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, SkyWidgetProvider::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduleNext(context: Context, delayMs: Long? = null) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val at = now + (delayMs ?: (60_000L - now % 60_000L + 25L))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC, at, pending(app))
            } else {
                am.set(AlarmManager.RTC, at, pending(app))
            }
        } catch (_: Exception) {}
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        try {
            (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(app))
        } catch (_: Exception) {}
    }
}
