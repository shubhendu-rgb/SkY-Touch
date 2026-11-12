package com.example.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.QuietHours

/**
 * Switch + two time buttons (AM/PM) for a daily "from time to time" window.
 * [key] picks which window it edits (HOME, LOCK or QuietHours.SIDE_DECK).
 */
@Composable
fun QuietHoursControls(
    key: String,
    title: String,
    description: String,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    var enabled by remember(key) { mutableStateOf(QuietHours.isEnabled(context, key)) }
    var start by remember(key) { mutableIntStateOf(QuietHours.getStart(context, key)) }
    var end by remember(key) { mutableIntStateOf(QuietHours.getEnd(context, key)) }

    fun pick(initial: Int, onPicked: (Int) -> Unit) {
        // is24HourView = false gives the AM/PM clock
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            initial / 60, initial % 60, false
        ).show()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(description, fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    QuietHours.setEnabled(context, key, it)
                    onChanged()
                },
                modifier = Modifier.testTag("switch_quiet_$key")
            )
        }
        if (enabled) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeButton(
                    label = "From",
                    value = QuietHours.format12h(start),
                    textColor = textColor,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f).testTag("quiet_start_$key")
                ) {
                    pick(start) { start = it; QuietHours.setStart(context, key, it); onChanged() }
                }
                TimeButton(
                    label = "To",
                    value = QuietHours.format12h(end),
                    textColor = textColor,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f).testTag("quiet_end_$key")
                ) {
                    pick(end) { end = it; QuietHours.setEnd(context, key, it); onChanged() }
                }
            }
        }
    }
}

@Composable
private fun TimeButton(
    label: String,
    value: String,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 10.sp, color = textColor.copy(alpha = 0.7f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}
