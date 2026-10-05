package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TextAssistantConfigEntity
import com.example.data.TextSnippetEntity
import com.example.service.NotchAccessibilityService
import com.example.util.GeminiTextHelper
import kotlinx.coroutines.launch

data class BuiltInTriggerItem(
    val keyword: String,
    val description: String
)

val defaultBuiltInTriggers = listOf(
    BuiltInTriggerItem("replace", "Replace text with clipboard content."),
    BuiltInTriggerItem("paste", "Paste from clipboard."),
    BuiltInTriggerItem("undo", "Undo the last replacement and restore the original text."),
    BuiltInTriggerItem("copy", "Copy the text to clipboard."),
    BuiltInTriggerItem("cut", "Cut the text to clipboard."),
    BuiltInTriggerItem("translate:xx", "Translate text to any language code (e.g. ?translate:es, ?translate:fr).")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextAssistantTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.textAssistantConfig
    val snippets = uiState.textSnippets
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    // Dialog state
    var showEditSnippetDialog by remember { mutableStateOf<TextSnippetEntity?>(null) }
    var showNewSnippetDialog by remember { mutableStateOf(false) }
    var newSnippetIsAi by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var snippetToDelete by remember { mutableStateOf<TextSnippetEntity?>(null) }
    var selectedCategory by remember { mutableStateOf("ai") }

    // API Key test state
    var isTestingApiKey by remember { mutableStateOf(false) }
    var apiKeyTestResult by remember { mutableStateOf<String?>(null) }
    var apiKeyTestSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Interactive sandbox text
    var sandboxText by remember { mutableStateOf("") }

    val localSnippets = snippets.filter { !it.isAiAction }
    val aiSnippets = snippets.filter { it.isAiAction }

    val isServiceActive = NotchAccessibilityService.isRunning

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("text_assistant_tab"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header & Master Toggle
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_assistant_master_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = "Text Assistant",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Text Assistant & AI",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "System-wide Snippets & Gemini Rewrite",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { isChecked ->
                                viewModel.updateTextAssistantConfig(config.copy(enabled = isChecked))
                            },
                            modifier = Modifier.testTag("text_assistant_master_switch")
                        )
                    }

                    // Accessibility Service Banner if not active
                    if (!isServiceActive) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Accessibility Service is Inactive",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Enable SkY Touch Accessibility Service with window content retrieval to intercept text triggers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onOpenSettings,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Enable", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Accessibility Service is active & monitoring triggers",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Trigger Prefix Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Trigger Prefix",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val prefixes = listOf("?", "!", "/", "#", ".", "@")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            prefixes.forEach { prefix ->
                                val isSelected = config.triggerPrefix == prefix
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateTextAssistantConfig(config.copy(triggerPrefix = prefix))
                                    },
                                    label = {
                                        Text(
                                            text = prefix,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 16.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Preferences: Haptics & AI Progress Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Haptic Feedback",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Vibrate subtly on trigger replacement",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.hapticFeedback,
                            onCheckedChange = {
                                viewModel.updateTextAssistantConfig(config.copy(hapticFeedback = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating AI Progress Pill",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Show status overlay while Gemini is thinking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.showOverlayPill,
                            onCheckedChange = {
                                viewModel.updateTextAssistantConfig(config.copy(showOverlayPill = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic Numeric Triggers & AI Limiter",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Enable multiplying text or limiting AI words using numbers (e.g. ?20)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.enableNumericTriggers,
                            onCheckedChange = {
                                viewModel.updateTextAssistantConfig(config.copy(enableNumericTriggers = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Multiplier Style",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (config.isDefaultMultiplierLineByLine) "Line-by-line (New Line)" else "Inline (Space)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.isDefaultMultiplierLineByLine,
                            onCheckedChange = {
                                viewModel.updateTextAssistantConfig(config.copy(isDefaultMultiplierLineByLine = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Inline Editing Commands",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Enable inline commands like ?copy, ?paste, ?cut, ?clear",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.enableEditingCommands,
                            onCheckedChange = {
                                viewModel.updateTextAssistantConfig(config.copy(enableEditingCommands = it))
                            }
                        )
                    }
                }
            }
        }

        // Gemini AI Configuration Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_config_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                var apiKeyInput by remember(config.apiKey) { mutableStateOf(config.apiKey) }

                val effectiveKey = GeminiTextHelper.getEffectiveApiKey(config.apiKey)
                val hasKey = effectiveKey.isNotBlank()

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = "Gemini AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Gemini AI Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bring Your Own Key • Powered by Google Gemini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Key status badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasKey)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (hasKey)
                                "Active • Custom API Key Configured"
                            else
                                "API Key Required • Enter your Gemini API Key below",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (hasKey)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Custom API Key Input
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                        },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Paste your API key (starts with AIzaSy...)") },
                        supportingText = {
                            Text("Starts with 'AIzaSy...'. Note: Do NOT use an OAuth Client ID.")
                        },
                        singleLine = true,
                        trailingIcon = {
                            Row(
                                modifier = Modifier.wrapContentSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (apiKeyInput.isNotEmpty()) {
                                    IconButton(onClick = {
                                        apiKeyInput = ""
                                        viewModel.updateTextAssistantConfig(config.copy(apiKey = ""))
                                    }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(onClick = { 
                                    viewModel.updateTextAssistantConfig(config.copy(apiKey = apiKeyInput.trim()))
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Save API Key",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_field"),
                        shape = RoundedCornerShape(24.dp)
                    )

                    // One-click Get API Key Button
                    OutlinedButton(
                        onClick = {
                            try {
                                uriHandler.openUri("https://aistudio.google.com/apikey")
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Get Free Gemini API Key (Google AI Studio)")
                    }

                    // Model Selection Dropdown
                    var expandedModelDropdown by remember { mutableStateOf(false) }
                    val availableModels = listOf(
                        "gemini-2.5-flash",
                        "gemini-flash-latest",
                        "gemini-3.5-flash",
                        "gemini-2.5-pro",
                        "gemini-3.1-pro-preview",
                        "gemini-3.1-flash-lite-preview"
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedModelDropdown,
                        onExpandedChange = { expandedModelDropdown = !expandedModelDropdown }
                    ) {
                        OutlinedTextField(
                            value = config.modelName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model Version") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(24.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModelDropdown,
                            onDismissRequest = { expandedModelDropdown = false }
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.updateTextAssistantConfig(config.copy(modelName = model))
                                        expandedModelDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Test API Key Button
                    val keyToTest = apiKeyInput.trim().ifEmpty { effectiveKey }
                    val canTestKey = keyToTest.isNotBlank()

                    Button(
                        onClick = {
                            isTestingApiKey = true
                            apiKeyTestResult = null
                            apiKeyTestSuccess = null

                            // Save current text input immediately
                            if (apiKeyInput.isNotBlank()) {
                                viewModel.updateTextAssistantConfig(config.copy(apiKey = apiKeyInput.trim()))
                            }

                            coroutineScope.launch {
                                val result = GeminiTextHelper.testApiKey(keyToTest, config.modelName, context)
                                isTestingApiKey = false
                                result.onSuccess {
                                    apiKeyTestSuccess = true
                                    apiKeyTestResult = it
                                }.onFailure { error ->
                                    apiKeyTestSuccess = false
                                    apiKeyTestResult = error.localizedMessage ?: "Unknown error occurred"
                                }
                            }
                        },
                        enabled = !isTestingApiKey && canTestKey,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_gemini_key_button")
                    ) {
                        if (isTestingApiKey) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Gemini Connection...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Gemini Connection")
                        }
                    }

                    // Test result display
                    apiKeyTestResult?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (apiKeyTestSuccess == true)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                if (apiKeyTestSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (apiKeyTestSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = if (apiKeyTestSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (apiKeyTestSuccess == true) "Connection Successful" else "Authentication / Connection Error",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (apiKeyTestSuccess == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (apiKeyTestSuccess == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (apiKeyTestSuccess == false) {
                                    FilledTonalButton(
                                        onClick = {
                                            try {
                                                uriHandler.openUri("https://aistudio.google.com/apikey")
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Google AI Studio to Get API Key")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Filter & Add Trigger Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (selectedCategory) {
                                "ai" -> "AI Triggers"
                                "builtin" -> "Built-in Triggers"
                                "local" -> "Text Shortcuts"
                                else -> "All Triggers & Actions"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Type ${config.triggerPrefix}<keyword> at the end of any text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            newSnippetIsAi = (selectedCategory != "local")
                            showNewSnippetDialog = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("add_trigger_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add")
                    }
                }

                // Category Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == "ai",
                        onClick = { selectedCategory = "ai" },
                        label = { Text("AI Triggers (${aiSnippets.size})") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = selectedCategory == "builtin",
                        onClick = { selectedCategory = "builtin" },
                        label = { Text("Built-in (${defaultBuiltInTriggers.size})") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = selectedCategory == "local",
                        onClick = { selectedCategory = "local" },
                        label = { Text("Shortcuts (${localSnippets.size})") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = selectedCategory == "all",
                        onClick = { selectedCategory = "all" },
                        label = { Text("All") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Render AI Triggers
        if (selectedCategory == "ai" || selectedCategory == "all") {
            if (aiSnippets.isEmpty()) {
                item {
                    Text(
                        text = "No AI triggers found. Tap '+ Add' to create one or 'Reset Default Snippets' below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(aiSnippets, key = { "ai_${it.id}" }) { snippet ->
                    AiTriggerCard(
                        snippet = snippet,
                        prefix = config.triggerPrefix,
                        onEdit = { showEditSnippetDialog = snippet },
                        onDelete = { snippetToDelete = snippet }
                    )
                }
            }
        }

        // Render Built-in Triggers
        if (selectedCategory == "builtin" || selectedCategory == "all") {
            items(defaultBuiltInTriggers, key = { "builtin_${it.keyword}" }) { item ->
                BuiltInTriggerCard(
                    item = item,
                    prefix = config.triggerPrefix
                )
            }
        }

        // Render Local Shortcuts
        if (selectedCategory == "local" || selectedCategory == "all") {
            if (localSnippets.isEmpty()) {
                item {
                    Text(
                        text = "No local shortcuts defined. Tap '+ Add' to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(localSnippets, key = { "local_${it.id}" }) { snippet ->
                    AiTriggerCard(
                        snippet = snippet,
                        prefix = config.triggerPrefix,
                        onEdit = { showEditSnippetDialog = snippet },
                        onDelete = { snippetToDelete = snippet }
                    )
                }
            }
        }

        // Live Sandbox & Playground
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_sandbox_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = "Sandbox",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Interactive Test Sandbox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Test triggers directly in this input field! Type a trigger like ${config.triggerPrefix}addr or precede with text like 'hello bro ${config.triggerPrefix}formal ' and watch it transform in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = sandboxText,
                        onValueChange = { sandboxText = it },
                        label = { Text("Live Sandbox Input Field") },
                        placeholder = { Text("Try typing: Can we meet soon ${config.triggerPrefix}formal ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sandbox_text_field"),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            if (sandboxText.isNotEmpty()) {
                                Button(onClick = { sandboxText = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    // Quick Suggestion Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("${config.triggerPrefix}addr ", "${config.triggerPrefix}fix ", "${config.triggerPrefix}formal ", "${config.triggerPrefix}shorten ").forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    sandboxText = if (suggestion.contains("addr")) suggestion else "please look at the document $suggestion"
                                },
                                label = { Text(suggestion.trim(), fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Reset Snippets Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showResetConfirmation = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Default Snippets", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog for Adding New Snippet / AI Action
    if (showNewSnippetDialog) {
        SnippetEditDialog(
            initialSnippet = TextSnippetEntity(
                triggerKeyword = "",
                isAiAction = newSnippetIsAi,
                replacementText = "",
                aiPromptInstruction = "",
                isEnabled = true
            ),
            prefix = config.triggerPrefix,
            onDismiss = { showNewSnippetDialog = false },
            onSave = { snippet ->
                viewModel.insertSnippet(snippet)
                showNewSnippetDialog = false
                Toast.makeText(context, "Snippet added!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog for Editing Existing Snippet
    showEditSnippetDialog?.let { snippetToEdit ->
        SnippetEditDialog(
            initialSnippet = snippetToEdit,
            prefix = config.triggerPrefix,
            onDismiss = { showEditSnippetDialog = null },
            onSave = { updatedSnippet ->
                viewModel.updateSnippet(updatedSnippet)
                showEditSnippetDialog = null
                Toast.makeText(context, "Snippet updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog for Delete Trigger Confirmation
    snippetToDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { snippetToDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252)) },
            title = { Text("Delete Trigger?") },
            text = { Text("Are you sure you want to delete '${config.triggerPrefix}${snippet.triggerKeyword}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSnippet(snippet.id)
                        snippetToDelete = null
                        Toast.makeText(context, "Trigger deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { snippetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation for Resetting Defaults
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text("Reset Default Snippets?") },
            text = { Text("This will replace your current snippets with the default AI actions (?improve, ?shorten, ?expand, ?formal, ?casual, ?emoji, ?human, ?reply, ?fix) and local shortcuts (?addr, ?email, ?meet, ?shrug).") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetSnippetsToDefault()
                        showResetConfirmation = false
                        Toast.makeText(context, "Reset to default snippets!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AiTriggerCard(
    snippet: TextSnippetEntity,
    prefix: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevron_rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trigger_card_${snippet.triggerKeyword}")
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) {
                isExpanded = !isExpanded
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$prefix${snippet.triggerKeyword}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse prompt" else "Expand prompt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = rotationState }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 20.dp)
                            ) { onEdit() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "|",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFFF5252),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 20.dp)
                            ) { onDelete() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (snippet.isAiAction) snippet.aiPromptInstruction else snippet.replacementText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BuiltInTriggerCard(
    item: BuiltInTriggerItem,
    prefix: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "builtin_chevron_rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("builtin_trigger_card_${item.keyword}")
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) {
                isExpanded = !isExpanded
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$prefix${item.keyword}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = rotationState }
                    )
                }

                Text(
                    text = "Built-in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetEditDialog(
    initialSnippet: TextSnippetEntity,
    prefix: String,
    onDismiss: () -> Unit,
    onSave: (TextSnippetEntity) -> Unit
) {
    var keyword by remember { mutableStateOf(initialSnippet.triggerKeyword) }
    var isAi by remember { mutableStateOf(initialSnippet.isAiAction) }
    var replacementText by remember { mutableStateOf(initialSnippet.replacementText) }
    var aiInstruction by remember { mutableStateOf(initialSnippet.aiPromptInstruction) }
    var isEnabled by remember { mutableStateOf(initialSnippet.isEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSnippet.id == 0L) {
                    if (isAi) "Add AI Trigger" else "Add Text Shortcut"
                } else {
                    if (isAi) "Edit AI Trigger" else "Edit Shortcut"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Type Switcher if creating new
                if (initialSnippet.id == 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isAi,
                            onClick = { isAi = true },
                            label = { Text("AI Trigger") },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isAi,
                            onClick = { isAi = false },
                            label = { Text("Text Shortcut") },
                            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Trigger Keyword Input
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it.replace(" ", "").lowercase() },
                    label = { Text("Trigger Keyword") },
                    prefix = { Text(prefix, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                )

                if (!isAi) {
                    // Replacement Text for Local Snippets
                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        label = { Text("Replacement Text") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                } else {
                    // System Prompt Instruction for AI Actions
                    OutlinedTextField(
                        value = aiInstruction,
                        onValueChange = { aiInstruction = it },
                        label = { Text("Gemini Prompt Instruction") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyword.isBlank()) return@Button
                    val trimmedKeyword = keyword.trim().removePrefix(prefix)
                    onSave(
                        initialSnippet.copy(
                            triggerKeyword = trimmedKeyword,
                            isAiAction = isAi,
                            replacementText = replacementText,
                            aiPromptInstruction = aiInstruction,
                            isEnabled = isEnabled
                        )
                    )
                },
                enabled = keyword.isNotBlank() && (if (isAi) aiInstruction.isNotBlank() else replacementText.isNotBlank())
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
