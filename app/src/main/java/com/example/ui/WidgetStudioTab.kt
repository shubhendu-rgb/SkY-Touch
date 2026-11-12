package com.example.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.widget.SkyWidgetProvider
import com.example.widget.WidgetEditorActivity
import com.example.widget.WidgetPreviewCanvas
import com.example.widget.WidgetStore

private fun openEditor(context: Context, widgetId: Int?) {
    val i = Intent(context, WidgetEditorActivity::class.java)
    if (widgetId == null) i.putExtra(WidgetEditorActivity.EXTRA_TEMPLATE, true)
    else i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    context.startActivity(i)
}

/** Widget Studio: design the default widget, add it to the home screen, and edit every placed widget. */
@Composable
fun WidgetStudioTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val ids = remember(refresh) {
        manager.getAppWidgetIds(ComponentName(context, SkyWidgetProvider::class.java)).toList()
    }
    val template = remember(refresh) { WidgetStore.load(context, WidgetStore.TEMPLATE_ID) }

    Column(
        modifier = modifier.fillMaxSize().testTag("widget_studio_screen")
            .verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Widget Studio", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Move and resize every part of the widget, change the colour of the time, date and " +
                                "other text, or use Material You colours that follow your wallpaper.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StudioPreview(
                    template, 250f, 110f,
                    Modifier.fillMaxWidth().aspectRatio(250f / 110f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { openEditor(context, null) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit design")
                    }
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
                                manager.requestPinAppWidget(ComponentName(context, SkyWidgetProvider::class.java), null, null)
                            } else {
                                Toast.makeText(
                                    context, "Long-press the home screen → Widgets → SkY Touch", Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to home")
                    }
                }
                Text(
                    "\"Edit design\" changes the look new widgets start with. Widgets already on your home screen " +
                        "keep their own design: tap one, or use Edit below.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Widgets on your home screen", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (ids.isEmpty()) {
                    Text(
                        "None yet. Tap \"Add to home\" above.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ids.forEach { id ->
                    val (w, h) = remember(refresh, id) { SkyWidgetProvider.sizeDp(context, manager, id) }
                    val layout = remember(refresh, id) { WidgetStore.load(context, id) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudioPreview(layout, w, h, Modifier.fillMaxWidth().aspectRatio(w / h))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Widget ${w.toInt()}×${h.toInt()} dp",
                                Modifier.weight(1f), fontSize = 13.sp
                            )
                            OutlinedButton(onClick = { openEditor(context, id) }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioPreview(
    layout: com.example.widget.WidgetLayout, wDp: Float, hDp: Float, modifier: Modifier
) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F3B63), Color(0xFF6B4E9B), Color(0xFFB4637A))))
    ) {
        WidgetPreviewCanvas(layout, wDp, hDp, Modifier.fillMaxSize())
    }
}
