package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.theme.MyApplicationTheme

/**
 * Edit panel for a widget. Opened (a) by the launcher when a widget is placed or reconfigured,
 * (b) by tapping a placed widget, (c) from Widget Studio in the app (template mode, edits the
 * design new widgets start from).
 */
class WidgetEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val templateMode = intent?.getBooleanExtra(EXTRA_TEMPLATE, false) == true ||
            widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
        val targetId = if (templateMode) WidgetStore.TEMPLATE_ID else widgetId

        // Launcher "configure" flow: cancelled unless the user saves.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContent {
            MyApplicationTheme {
                WidgetEditorScreen(
                    appWidgetId = targetId,
                    onCancel = { finish() },
                    onSave = { layout ->
                        WidgetStore.save(applicationContext, targetId, layout)
                        WidgetAssets.cleanupImages(applicationContext, WidgetStore.referencedImages(applicationContext))
                        if (!templateMode) {
                            SkyWidgetProvider.updateOne(applicationContext, widgetId)
                            WidgetTicker.scheduleNext(applicationContext)
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        }
                        finish()
                    },
                    onSaveAsTemplate = { layout ->
                        WidgetStore.save(applicationContext, WidgetStore.TEMPLATE_ID, layout)
                        WidgetAssets.cleanupImages(applicationContext, WidgetStore.referencedImages(applicationContext))
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE = "sky_widget_template"
    }
}
