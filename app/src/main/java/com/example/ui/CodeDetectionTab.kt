package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.CodeDetectionConfigEntity
import com.example.data.DetectedCodeEntity
import com.example.service.OtpNotificationListenerService
import com.example.util.CodeDetector
import com.example.util.OtpNotificationHelper
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeDetectionTab(
    config: CodeDetectionConfigEntity,
    detectedCodes: List<DetectedCodeEntity>,
    onConfigChange: (CodeDetectionConfigEntity) -> Unit,
    onConfigChangeDebounced: (CodeDetectionConfigEntity) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onAddDetectedCode: (DetectedCodeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Local copies of the text fields so typing stays smooth
    var keywordFilterState by remember { mutableStateOf(config.keywordFilter) }
    var prototypeMessageState by remember { mutableStateOf(config.prototypeMessage) }
    var customRegexState by remember { mutableStateOf(config.customRegex) }

    LaunchedEffect(config.keywordFilter) { if (config.keywordFilter != keywordFilterState) keywordFilterState = config.keywordFilter }
    LaunchedEffect(config.prototypeMessage) { if (config.prototypeMessage != prototypeMessageState) prototypeMessageState = config.prototypeMessage }
    LaunchedEffect(config.customRegex) { if (config.customRegex != customRegexState) customRegexState = config.customRegex }

    // Permission state
    var isNotificationAccessGranted by remember { mutableStateOf(false) }
    var isPostNotificationPermissionGranted by remember { mutableStateOf(true) }
    var isSmsPermissionGranted by remember { mutableStateOf(false) }

    fun checkAllPermissions() {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        isNotificationAccessGranted = enabledPackages.contains(context.packageName)
        isPostNotificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        isSmsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    val requestPostNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isPostNotificationPermissionGranted = isGranted
        if (isGranted) Toast.makeText(context, "Alerts are now allowed", Toast.LENGTH_SHORT).show()
    }

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isSmsPermissionGranted = isGranted
        if (isGranted) {
            Toast.makeText(context, "SMS access allowed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "SMS access was not allowed, so codes in text messages cannot be found.", Toast.LENGTH_SHORT).show()
        }
    }

    // Re-check when the screen opens and every time the user comes back from system settings
    LaunchedEffect(Unit) { checkAllPermissions() }
    DisposableEffect(context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) checkAllPermissions()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // Test area state
    var sandboxInput by remember {
        mutableStateOf("Your one-time verification password for bank transaction is 839201. Valid for 5 minutes.")
    }
    val detectedSandboxResult = remember(sandboxInput, config) {
        CodeDetector.detectCode(sandboxInput, config)
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val needsSetup = !isNotificationAccessGranted ||
        !isPostNotificationPermissionGranted ||
        (config.detectFromSms && !isSmsPermissionGranted)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- 1. Status and main switch ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Code Detector",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when {
                            !config.enabled -> "Turned off. Switch it on to find codes automatically."
                            needsSetup -> "Almost ready. Finish the setup below."
                            else -> "On. New codes are found automatically."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
                    modifier = Modifier.testTag("otp_master_switch")
                )
            }
        }

        // ---------- 2. Setup and permissions ----------
        SectionCard(
            icon = Icons.Filled.Shield,
            title = "Setup",
            subtitle = if (needsSetup) "Allow these so codes can be found and shown" else "Everything is allowed"
        ) {
            PermissionRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Read notifications",
                description = "Lets the app see your notifications to find codes in them.",
                granted = isNotificationAccessGranted,
                grantLabel = "Allow",
                modifier = Modifier.testTag("permission_banner"),
                buttonModifier = Modifier.testTag("grant_notification_access_btn"),
                onGrant = {
                    try {
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Open Settings, then Notification access, and turn on SkY Touch.", Toast.LENGTH_LONG).show()
                    }
                }
            )
            PermissionRow(
                icon = Icons.Filled.Notifications,
                title = "Show alerts",
                description = "Lets the app show the alert with the Copy button.",
                granted = isPostNotificationPermissionGranted,
                grantLabel = "Allow",
                modifier = Modifier.testTag("post_notification_banner"),
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPostNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        } catch (_: Exception) {}
                    }
                }
            )
            PermissionRow(
                icon = Icons.Filled.Sms,
                title = "Read text messages",
                description = "Lets the app see new SMS messages to find codes in them.",
                granted = isSmsPermissionGranted,
                grantLabel = "Allow",
                modifier = Modifier.testTag("sms_permission_banner"),
                onGrant = { requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) }
            )
            if (needsSetup) {
                TextButton(
                    onClick = { checkAllPermissions() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check again", fontSize = 13.sp)
                }
            }
        }

        // ---------- 3. Where to look for codes ----------
        SectionCard(
            icon = Icons.Filled.Search,
            title = "Where to look for codes",
            subtitle = "Choose which messages are checked"
        ) {
            SettingSwitchRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                description = "Find codes in notifications from your apps.",
                checked = config.detectFromNotifications,
                enabled = config.enabled,
                onCheckedChange = { onConfigChange(config.copy(detectFromNotifications = it)) }
            )
            SettingSwitchRow(
                icon = Icons.Filled.Sms,
                title = "Text messages (SMS)",
                description = "Find codes in new SMS messages. Needs SMS access.",
                checked = config.detectFromSms,
                enabled = config.enabled,
                onCheckedChange = {
                    onConfigChange(config.copy(detectFromSms = it))
                    if (it && !isSmsPermissionGranted) {
                        requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingSwitchRow(
                icon = Icons.Filled.Apps,
                title = "Check notifications from all apps",
                description = "Turn this off to choose only the apps you trust.",
                checked = config.detectAllApps,
                enabled = config.enabled && config.detectFromNotifications,
                onCheckedChange = { onConfigChange(config.copy(detectAllApps = it)) }
            )

            if (!config.detectAllApps && config.detectFromNotifications) {
                Text(
                    text = "Apps that are checked",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                val appList = remember(config.allowedPackages) {
                    config.allowedPackages
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }

                if (appList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No apps chosen yet. Tap \"Add app\" to pick one.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    appList.forEach { pkg ->
                        var appTitle by remember { mutableStateOf(pkg.substringAfterLast(".")) }
                        LaunchedEffect(pkg) {
                            try {
                                val pm = context.packageManager
                                val info = pm.getApplicationInfo(pkg, 0)
                                appTitle = pm.getApplicationLabel(info).toString()
                            } catch (_: Exception) {}
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pkg,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    val updated = appList.filter { it != pkg }.joinToString(",")
                                    onConfigChange(config.copy(allowedPackages = updated))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove app",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                var showAppPickerInCodeDetector by remember { mutableStateOf(false) }

                FilledTonalButton(
                    onClick = { showAppPickerInCodeDetector = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add app")
                }

                if (showAppPickerInCodeDetector) {
                    AppSelectionDialog(
                        context = context,
                        onDismiss = { showAppPickerInCodeDetector = false },
                        onAppChosen = { chosenPkg ->
                            val current = config.allowedPackages
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            if (!current.contains(chosenPkg)) {
                                val updated = (current + chosenPkg).joinToString(",")
                                onConfigChange(config.copy(allowedPackages = updated))
                            }
                            showAppPickerInCodeDetector = false
                        }
                    )
                }
            }
        }

        // ---------- 4. What happens when a code is found ----------
        SectionCard(
            icon = Icons.Filled.FlashOn,
            title = "When a code is found",
            subtitle = "Choose what the app does for you"
        ) {
            SettingSwitchRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Show an alert with a Copy button",
                description = "The alert appears 2 seconds after the code is found. If a newer code arrives, only the newest one is shown.",
                checked = config.showNotification,
                enabled = config.enabled,
                tag = "show_notification_switch",
                onCheckedChange = { onConfigChange(config.copy(showNotification = it)) }
            )
            SettingSwitchRow(
                icon = Icons.Filled.ContentCopy,
                title = "Copy the code automatically",
                description = "The code is placed on your clipboard right away, ready to paste.",
                checked = config.autoCopyToClipboard,
                enabled = config.enabled,
                tag = "auto_copy_switch",
                onCheckedChange = { onConfigChange(config.copy(autoCopyToClipboard = it)) }
            )
            SettingSwitchRow(
                icon = Icons.Filled.Vibration,
                title = "Vibrate",
                description = "Your phone vibrates briefly when a code is found.",
                checked = config.hapticFeedback,
                enabled = config.enabled,
                tag = "haptic_feedback_switch",
                onCheckedChange = { onConfigChange(config.copy(hapticFeedback = it)) }
            )
        }

        // ---------- 5. How codes are recognized ----------
        SectionCard(
            icon = Icons.Filled.Tune,
            title = "How codes are recognized",
            subtitle = "The defaults work for most messages. Change them only if a code is missed."
        ) {
            // Code length
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Filled.Straighten)
                        Text(
                            text = "Length of number codes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${config.minDigits} to ${config.maxDigits} digits",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RangeSlider(
                    value = config.minDigits.toFloat()..config.maxDigits.toFloat(),
                    onValueChange = { range ->
                        onConfigChangeDebounced(
                            config.copy(minDigits = range.start.toInt(), maxDigits = range.endInclusive.toInt())
                        )
                    },
                    valueRange = 3f..10f,
                    steps = 6,
                    modifier = Modifier.testTag("code_length_slider")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingSwitchRow(
                icon = Icons.Filled.TextFields,
                title = "Also find codes with letters",
                description = "For codes that mix letters and numbers, like W8KZ-9P2A or 9X8Y7Z.",
                checked = config.allowAlphanumeric,
                onCheckedChange = { onConfigChange(config.copy(allowAlphanumeric = it)) }
            )
            SettingSwitchRow(
                icon = Icons.Filled.Key,
                title = "Number codes need a keyword nearby",
                description = "Ignores random numbers unless the message also contains a keyword such as \"code\" or \"OTP\".",
                checked = config.requireKeywordForNumeric,
                onCheckedChange = { onConfigChange(config.copy(requireKeywordForNumeric = it)) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Keywords
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldTitle(Icons.Filled.Search, "Keywords")
                OutlinedTextField(
                    value = keywordFilterState,
                    onValueChange = {
                        keywordFilterState = it
                        onConfigChangeDebounced(config.copy(keywordFilter = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("code, otp, verification, pin, secret, auth") }
                )
                Text(
                    text = "Separate the words with commas. Messages that contain one of them are checked first.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Message format
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldTitle(Icons.Filled.FormatQuote, "Message format (optional)")
                OutlinedTextField(
                    value = prototypeMessageState,
                    onValueChange = {
                        prototypeMessageState = it
                        onConfigChangeDebounced(config.copy(prototypeMessage = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Your verification code is [CODE]") }
                )
                Text(
                    text = "If you fill this in, only messages that match this format are used. Write [CODE] where the code appears.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Custom pattern
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FieldTitle(Icons.Filled.Code, "Custom pattern (advanced, optional)")
                }
                OutlinedTextField(
                    value = customRegexState,
                    onValueChange = {
                        customRegexState = it
                        onConfigChangeDebounced(config.copy(customRegex = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("""For example \b\d{6}\b""") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                if (config.customRegex.isNotBlank()) {
                    val isValid = remember(config.customRegex) {
                        try {
                            Pattern.compile(config.customRegex)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                    Text(
                        text = if (isValid) "The pattern is valid" else "The pattern has an error",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isValid) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "A pattern (regular expression) that is tried first. Leave it empty to use the automatic detection.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- 6. Try it ----------
        SectionCard(
            icon = Icons.Filled.Science,
            title = "Try it",
            subtitle = "Paste a message to see which code would be found"
        ) {
            Text(
                text = "Examples",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        sandboxInput = "Your one-time password for transaction of $142.50 at Amazon is 839201. Valid for 10 minutes. Do not share."
                    },
                    label = { Text("Bank message") },
                    leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { sandboxInput = "G-749201 is your Google verification code." },
                    label = { Text("Google") },
                    leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { sandboxInput = "Your Discord security login code is W8KZ-9P2A. Do not give this to anyone." },
                    label = { Text("Discord / Steam") },
                    leadingIcon = { Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { sandboxInput = "Use verification passcode 9X8Y7Z to finalize your login authentication." },
                    label = { Text("Letters and numbers") },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            OutlinedTextField(
                value = sandboxInput,
                onValueChange = { sandboxInput = it },
                label = { Text("Message to test") },
                placeholder = { Text("Paste an SMS or notification text here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sandbox_text_field"),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (sandboxInput.isNotEmpty()) {
                        IconButton(onClick = { sandboxInput = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear text")
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = detectedSandboxResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (detectedSandboxResult != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sandbox_detection_result"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Code found",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = formatCodeType(detectedSandboxResult.type),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = detectedSandboxResult.code,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Verification Code", detectedSandboxResult.code))
                                        triggerHaptic(context)
                                        Toast.makeText(context, "Copied: ${detectedSandboxResult.code}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationPermissionGranted) {
                                            requestPostNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val notifId = OtpNotificationHelper.postOtpNotification(
                                                context = context,
                                                code = detectedSandboxResult.code,
                                                sourcePackage = context.packageName,
                                                snippet = detectedSandboxResult.contextSnippet
                                            )
                                            if (notifId != -1) {
                                                Toast.makeText(context, "Test alert sent", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Alerts are turned off in your phone settings.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Send test alert")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onAddDetectedCode(
                                            DetectedCodeEntity(
                                                code = detectedSandboxResult.code,
                                                codeType = detectedSandboxResult.type,
                                                sourcePackage = "Test message",
                                                snippet = detectedSandboxResult.contextSnippet,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                        Toast.makeText(context, "Saved to the list below", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save")
                                }
                            }

                            if (detectedSandboxResult.contextSnippet.isNotBlank()) {
                                Text(
                                    text = "Found in: \"${detectedSandboxResult.contextSnippet}\"",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }

            if (detectedSandboxResult == null && sandboxInput.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "No code found in this message. Try changing the code length or the keywords above.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---------- 7. Found codes ----------
        SectionCard(
            icon = Icons.Filled.History,
            title = "Found codes",
            subtitle = if (detectedCodes.isEmpty()) "Codes the app finds are listed here" else "${detectedCodes.size} saved",
            trailing = {
                if (detectedCodes.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Delete all codes",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        ) {
            if (detectedCodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HistoryToggleOff,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "No codes yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "When a message with a code arrives, it will show up here.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

                detectedCodes.forEach { item ->
                    key(item.id) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = item.code,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(
                                                text = formatCodeType(item.codeType),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "From ${item.sourcePackage}  \u2022  ${dateFormat.format(Date(item.timestamp))}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.snippet.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.snippet,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Verification Code", item.code))
                                        triggerHaptic(context)
                                        Toast.makeText(context, "Copied ${item.code}", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteHistoryItem(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Delete code",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            title = { Text("Delete all codes?") },
            text = { Text("All saved codes will be removed from this list. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Card with a round icon, a title and a short explanation, used for every group of settings. */
@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(end = 10.dp)
            .size(20.dp)
    )
}

@Composable
private fun FieldTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** One setting with an icon, a short title, a plain explanation and a switch. */
@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    tag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier
        )
    }
}

/** A permission with its state: a green tick when allowed, an "Allow" button when it is not. */
@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantLabel: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E9E5B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Allowed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E9E5B))
            }
        } else {
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = buttonModifier
            ) {
                Text(grantLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatCodeType(type: String): String {
    return when (type) {
        "NUMERIC_OTP" -> "Numeric OTP"
        "ALPHANUMERIC_2FA" -> "Alphanumeric 2FA"
        "SERVICE_PREFIX" -> "Service Code"
        "HYPHENATED_2FA" -> "Hyphenated 2FA"
        "CUSTOM_REGEX" -> "Custom Pattern"
        else -> type
    }
}

private fun triggerHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    } catch (_: Exception) {
    }
}
