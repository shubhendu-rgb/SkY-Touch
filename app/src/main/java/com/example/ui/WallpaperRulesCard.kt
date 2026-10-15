package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.WallpaperRules

/**
 * Lets the user decide how the "Wallpaper: Images" / "Wallpaper: Folder" Quick Settings tiles and the
 * Next / Previous / Random wallpaper gestures behave. These rules are global (they are not tied to the
 * Home / Lock tab that is currently open above).
 */
@Composable
fun WallpaperRulesCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var selectedTrigger by remember { mutableStateOf(WallpaperRules.TRIGGER_TILE_IMAGES) }
    val rules: SnapshotStateMap<String, WallpaperRules.Rule> = remember {
        mutableStateMapOf<String, WallpaperRules.Rule>().apply {
            WallpaperRules.ALL_TRIGGERS.forEach { put(it, WallpaperRules.load(context, it)) }
        }
    }
    val rule = rules[selectedTrigger] ?: WallpaperRules.defaultFor(selectedTrigger)

    fun update(newRule: WallpaperRules.Rule) {
        rules[selectedTrigger] = newRule
        WallpaperRules.save(context, selectedTrigger, newRule)
    }

    val isTile = selectedTrigger != WallpaperRules.TRIGGER_GESTURE

    Card(
        modifier = modifier.fillMaxWidth().testTag("card_wallpaper_rules"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF64B5F6))
                Text(
                    "Tile & Gesture Rules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                "Choose how the wallpaper Quick Settings tiles and the Next / Previous / Random " +
                    "wallpaper gestures change your wallpaper. These rules apply to both Home and Lock tabs.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )

            RuleLabel("Edit rules for")
            RulePills(
                options = listOf(
                    WallpaperRules.TRIGGER_TILE_IMAGES to "Images tile",
                    WallpaperRules.TRIGGER_TILE_FOLDER to "Folder tile",
                    WallpaperRules.TRIGGER_GESTURE to "Gestures"
                ),
                selected = selectedTrigger,
                onSelect = { selectedTrigger = it },
                tagPrefix = "rule_trigger"
            )

            RuleLabel("Change wallpaper on")
            RulePills(
                options = listOf(
                    WallpaperRules.TARGET_HOME to "Home",
                    WallpaperRules.TARGET_LOCK to "Lock",
                    WallpaperRules.TARGET_BOTH to "Both"
                ),
                selected = rule.target,
                onSelect = { update(rule.copy(target = it)) },
                tagPrefix = "rule_target"
            )

            if (rule.target == WallpaperRules.TARGET_BOTH) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Same picture on both", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Off: Home and Lock each move through their own pictures",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = rule.sameOnBoth,
                        onCheckedChange = { update(rule.copy(sameOnBoth = it)) },
                        modifier = Modifier.testTag("switch_rule_same_on_both")
                    )
                }
            }

            if (!isTile) {
                RuleLabel("Pick wallpapers from")
                RulePills(
                    options = listOf(
                        WallpaperRules.SOURCE_ANY to "Images + Folder",
                        WallpaperRules.SOURCE_IMAGES to "Images",
                        WallpaperRules.SOURCE_FOLDER to "Folder"
                    ),
                    selected = rule.source,
                    onSelect = { update(rule.copy(source = it)) },
                    tagPrefix = "rule_source"
                )
            } else {
                RuleLabel("Order")
                RulePills(
                    options = listOf(
                        WallpaperRules.ORDER_SEQUENTIAL to "Next in order",
                        WallpaperRules.ORDER_RANDOM to "Random"
                    ),
                    selected = rule.order,
                    onSelect = { update(rule.copy(order = it)) },
                    tagPrefix = "rule_order"
                )
            }

            Text(
                WallpaperRules.describe(selectedTrigger, rule),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun RuleLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RulePills(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).testTag("${tagPrefix}_$value"),
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) Color(0xFF1976D2) else Color.Transparent
            ) {
                Text(
                    label,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
