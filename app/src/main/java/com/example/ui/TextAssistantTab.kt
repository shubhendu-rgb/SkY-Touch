package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val coroutineScope = rememberCoroutineScope()

    // Dialog state
    var showEditSnippetDialog by remember { mutableStateOf<TextSnippetEntity?>(null) }
    var showNewSnippetDialog by remember { mutableStateOf(false) }
    var newSnippetIsAi by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
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
                        placeholder = { Text("Paste your API key (AIzaSy...)") },
                        supportingText = {
                            Text("Get your free API key at https://aistudio.google.com/")
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

                    // Model Selection Dropdown
                    var expandedModelDropdown by remember { mutableStateOf(false) }
                    val availableModels = listOf(
                        "gemini-3.5-flash",
                        "gemini-3.1-pro-preview",
                        "gemini-2.5-flash",
                        "gemini-2.5-pro",
                        "gemini-2.0-flash",
                        "gemini-2.0-flash-lite-preview-02-05",
                        "gemini-2.0-pro-exp-02-05",
                        "gemini-2.0-flash-thinking-exp-01-21",
                        "gemini-1.5-flash",
                        "gemini-1.5-pro"
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
                    OutlinedButton(
                        onClick = {
                            isTestingApiKey = true
                            apiKeyTestResult = null
                            apiKeyTestSuccess = null
                            coroutineScope.launch {
                                val result = GeminiTextHelper.testApiKey(effectiveKey, config.modelName)
                                isTestingApiKey = false
                                result.onSuccess {
                                    apiKeyTestSuccess = true
                                    apiKeyTestResult = it
                                }.onFailure { error ->
                                    apiKeyTestSuccess = false
                                    apiKeyTestResult = error.localizedMessage ?: "Unknown error"
                                }
                            }
                        },
                        enabled = !isTestingApiKey && effectiveKey.isNotBlank(),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_gemini_key_button")
                    ) {
                        if (isTestingApiKey) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
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
                            shape = RoundedCornerShape(8.dp),
                            color = if (apiKeyTestSuccess == true)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (apiKeyTestSuccess == true) Icons.Filled.Check else Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = if (apiKeyTestSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (apiKeyTestSuccess == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Local Instant Text Expansion Snippets
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_snippets_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Local Snippets",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Instant Text Snippets",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${localSnippets.count { it.isEnabled }} active (${localSnippets.size} total)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = {
                                newSnippetIsAi = false
                                showNewSnippetDialog = true
                            },
                            modifier = Modifier.testTag("add_local_snippet_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New Snippet", modifier = Modifier.size(16.dp))
                        }
                    }

                    if (localSnippets.isEmpty()) {
                        Text(
                            text = "No local snippets defined. Tap 'New Snippet' to add your first quick shortcut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        localSnippets.forEach { snippet ->
                            SnippetRowItem(
                                snippet = snippet,
                                prefix = config.triggerPrefix,
                                onToggle = { isEnabled ->
                                    viewModel.updateSnippet(snippet.copy(isEnabled = isEnabled))
                                },
                                onEdit = { showEditSnippetDialog = snippet },
                                onDelete = { viewModel.deleteSnippet(snippet.id) }
                            )
                        }
                    }
                }
            }
        }

        // AI Smart Transformation Actions
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_snippets_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI Actions",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "AI Smart Transformations",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${aiSnippets.count { it.isEnabled }} active (${aiSnippets.size} total)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = {
                                newSnippetIsAi = true
                                showNewSnippetDialog = true
                            },
                            modifier = Modifier.testTag("add_ai_snippet_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New AI Action", modifier = Modifier.size(16.dp))
                        }
                    }

                    if (aiSnippets.isEmpty()) {
                        Text(
                            text = "No AI actions defined. Tap 'New AI Action' to add prompts for fixing grammar, formal tone, summarizing, etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        aiSnippets.forEach { snippet ->
                            SnippetRowItem(
                                snippet = snippet,
                                prefix = config.triggerPrefix,
                                onToggle = { isEnabled ->
                                    viewModel.updateSnippet(snippet.copy(isEnabled = isEnabled))
                                },
                                onEdit = { showEditSnippetDialog = snippet },
                                onDelete = { viewModel.deleteSnippet(snippet.id) }
                            )
                        }
                    }
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
                aiPromptInstruction = if (newSnippetIsAi) "Fix all spelling and grammar errors. Output ONLY the corrected text." else "",
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

    // Confirmation for Resetting Defaults
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text("Reset Default Snippets?") },
            text = { Text("This will replace your current snippets with the built-in default local shortcuts (?addr, ?email, ?meet, ?shrug) and AI actions (?fix, ?formal, ?shorten, ?reply, ?translate).") },
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
fun SnippetRowItem(
    snippet: TextSnippetEntity,
    prefix: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Trigger Keyword Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (snippet.isAiAction)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.widthIn(min = 64.dp)
            ) {
                Text(
                    text = "$prefix${snippet.triggerKeyword}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (snippet.isAiAction)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // Description / Replacement preview
            Column(modifier = Modifier.weight(1f)) {
                if (!snippet.isAiAction) {
                    Text(
                        text = snippet.replacementText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        color = if (snippet.isEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "AI Transformation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (snippet.isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            if (isExpanded) {
                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Toggle Switch
            Switch(
                checked = snippet.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.85f)
            )
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

    // Pre-made AI presets
    val presets = listOf(
        Pair("Fix Grammar", "Fix all spelling, grammar, punctuation, and capitalization errors while preserving original tone. Output ONLY the polished text."),
        Pair("Professional Tone", "Rewrite this message into a polite, respectful, and professional business tone. Output ONLY the rewritten text."),
        Pair("Shorten", "Condense and summarize this text to make it clear, concise, and punchy while retaining all key points. Output ONLY the shortened text."),
        Pair("Smart Reply", "Generate a thoughtful, friendly, and helpful direct reply to this message. Output ONLY the response text."),
        Pair("Translate", "Translate this text into fluent, natural English (or if already in English, translate to Spanish). Output ONLY the translation."),
        Pair("Explain Like I'm 5", "Explain this concept in simple, friendly, easy-to-understand language. Output ONLY the explanation.")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSnippet.id == 0L) {
                    if (isAi) "Add AI Smart Action" else "Add Instant Snippet"
                } else {
                    if (isAi) "Edit AI Action" else "Edit Snippet"
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
                            selected = !isAi,
                            onClick = { isAi = false },
                            label = { Text("Instant Text Expansion") },
                            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isAi,
                            onClick = { isAi = true },
                            label = { Text("AI Gemini Transform") },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Trigger Keyword Input
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it.replace(" ", "").lowercase() },
                    label = { Text("Trigger Keyword") },
                    placeholder = { Text(if (isAi) "formal" else "addr") },
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
                        placeholder = { Text("123 Main Street, New York, NY") },
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
                        placeholder = { Text("Rewrite this into a polite business tone...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )

                    // Template Chips
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.take(3).forEach { (name, prompt) ->
                            SuggestionChip(
                                onClick = { aiInstruction = prompt },
                                label = { Text(name, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.drop(3).forEach { (name, prompt) ->
                            SuggestionChip(
                                onClick = { aiInstruction = prompt },
                                label = { Text(name, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
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
