package com.example

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.CodeDetectionTab
import com.example.ui.TextAssistantTab
import com.example.ui.DeveloperTab
import com.example.ui.RecordingsTab
import com.example.ui.ExcludedAppsTab
import com.example.ui.BackupRestoreTab
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.AppDatabase
import com.example.data.GestureActionEntity
import com.example.data.NotchConfigEntity
import com.example.data.NotchRepository
import com.example.data.TriggerStatEntity
import com.example.service.NotchAccessibilityService
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.SideDeckTab
import com.example.ui.theme.MyApplicationTheme
import com.example.util.OtpNotificationHelper
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var repository: NotchRepository
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getDatabase(this)
        repository = NotchRepository(db)
        OtpNotificationHelper.createNotificationChannel(this)

        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var isDarkMode by remember {
                mutableStateOf(
                    if (prefs.contains("dark_mode")) prefs.getBoolean("dark_mode", false) else systemDark
                )
            }
            var isDynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }

            MyApplicationTheme(darkTheme = isDarkMode, dynamicColor = isDynamicColor) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_screen"),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    MainContentScreen(
                        viewModel = viewModel,
                        repository = repository,
                        isDarkMode = isDarkMode,
                        isDynamicColor = isDynamicColor,
                        onDarkModeChange = { 
                            isDarkMode = it
                            prefs.edit().putBoolean("dark_mode", it).apply()
                        },
                        onDynamicColorChange = {
                            isDynamicColor = it
                            prefs.edit().putBoolean("dynamic_color", it).apply()
                        },
                        modifier = Modifier.padding(innerPadding),
                        onOpenSettings = { openAccessibilitySettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Force evaluation of service state
        // Since state is stored statically as well as queryable from secure settings
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Scroll down and turn on 'SkY Touch' in downloaded services!",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings. Please enable manually.", Toast.LENGTH_SHORT).show()
        }
    }
}

// Utility to check if service is enabled in settings
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val expectedComponentName = ComponentName(context, serviceClass)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

enum class ScreenType {
    DASHBOARD, GESTURES, CALIBRATION, SIDE_DECK, CODE_DETECTION, TEXT_ASSISTANT, ANALYTICS, DEVELOPER, RECORDINGS, EXCLUDED_APPS, INSTRUCTIONS, BACKUP_RESTORE, WALLPAPER
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(
    viewModel: MainViewModel,
    repository: NotchRepository,
    isDarkMode: Boolean,
    isDynamicColor: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val testMessage by viewModel.testGestureMessage.collectAsStateWithLifecycle()

    var isServiceRunning by remember { mutableStateOf(false) }
    var isPermissionEnabled by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted! Camera options are now enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required for Spy Cam feature.", Toast.LENGTH_LONG).show()
        }
    }

    // Periodically check service status
    LaunchedEffect(Unit) {
        while (true) {
            isServiceRunning = NotchAccessibilityService.isRunning
            isPermissionEnabled = isAccessibilityServiceEnabled(context, NotchAccessibilityService::class.java)
            delay(1500)
        }
    }

    val isServiceActive = isServiceRunning

    // Dismiss test message after 2 seconds
    LaunchedEffect(testMessage) {
        if (testMessage != null) {
            delay(2000)
            viewModel.clearTestMessage()
        }
    }

    val initialScreen = remember {
        if ((context as? ComponentActivity)?.intent?.getStringExtra("OPEN_SCREEN") == "CODE_DETECTION") {
            ScreenType.CODE_DETECTION
        } else {
            ScreenType.DASHBOARD
        }
    }
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var editingActionGesture by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var githubName by remember { mutableStateOf("Details & About") }
    var githubAvatar by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url("https://api.github.com/users/shubhendu-rgb").build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(response.body?.string() ?: "")
                    val name = json.optString("name", "Shubhendu Kumar Sahoo")
                    val avatarUrl = json.optString("avatar_url", "")
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        githubName = name
                    }

                    if (avatarUrl.isNotEmpty()) {
                        val imgRequest = okhttp3.Request.Builder().url(avatarUrl).build()
                        val imgResponse = okhttp3.OkHttpClient().newCall(imgRequest).execute()
                        if (imgResponse.isSuccessful) {
                            val bytes = imgResponse.body?.bytes()
                            if (bytes != null) {
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val imageBitmap = bitmap.asImageBitmap()
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    githubAvatar = imageBitmap
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Handle system back button for 1-step back navigation
    BackHandler(enabled = drawerState.isOpen || editingActionGesture != null || currentScreen != ScreenType.DASHBOARD) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (editingActionGesture != null) {
            editingActionGesture = null
        } else if (currentScreen != ScreenType.DASHBOARD) {
            currentScreen = ScreenType.DASHBOARD
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(24.dp)
                ) {
                    Text("SkY Touch", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dark Mode", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dynamic Color", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Switch(checked = isDynamicColor, onCheckedChange = onDynamicColorChange)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Menu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                        label = { Text("Analytics") },
                        selected = currentScreen == ScreenType.ANALYTICS,
                        onClick = {
                            currentScreen = ScreenType.ANALYTICS
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Block, contentDescription = null) },
                        label = { Text("Excluded Apps") },
                        selected = currentScreen == ScreenType.EXCLUDED_APPS,
                        onClick = {
                            currentScreen = ScreenType.EXCLUDED_APPS
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                        label = { Text("Video Recordings") },
                        selected = currentScreen == ScreenType.RECORDINGS,
                        onClick = {
                            currentScreen = ScreenType.RECORDINGS
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null) },
                        label = { Text("Backup & Restore") },
                        selected = currentScreen == ScreenType.BACKUP_RESTORE,
                        onClick = {
                            currentScreen = ScreenType.BACKUP_RESTORE
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text("Instructions") },
                        selected = currentScreen == ScreenType.INSTRUCTIONS,
                        onClick = { 
                            currentScreen = ScreenType.INSTRUCTIONS
                            scope.launch { drawerState.close() } 
                        }
                    )
                    NavigationDrawerItem(
                        icon = { 
                            if (githubAvatar != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = githubAvatar!!,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null) 
                            }
                        },
                        label = { Text(githubName) },
                        selected = currentScreen == ScreenType.DEVELOPER,
                        onClick = { 
                            currentScreen = ScreenType.DEVELOPER
                            scope.launch { drawerState.close() } 
                        }
                    )
                }
            }
        },
        scrimColor = Color.Black.copy(alpha = 0.3f)
    ) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val primaryGlow = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        val secondaryGlow = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        // Performance Optimization: Cache gradient brush using remember to avoid GC allocations & expensive calculations during draw passes
        val backgroundBrush = remember(backgroundColor, primaryGlow, secondaryGlow) {
            Brush.verticalGradient(
                colors = listOf(
                    backgroundColor,
                    primaryGlow.copy(alpha = 0.15f),
                    secondaryGlow.copy(alpha = 0.12f),
                    backgroundColor
                )
            )
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                ScreenType.DASHBOARD -> "Dashboard"
                                ScreenType.GESTURES -> "Gestures"
                                ScreenType.CALIBRATION -> "Calibration"
                                ScreenType.SIDE_DECK -> "Side Deck"
                                ScreenType.CODE_DETECTION -> "Code Detector"
                                ScreenType.TEXT_ASSISTANT -> "Text Assistant & AI"
                                ScreenType.ANALYTICS -> "Analytics"
                                ScreenType.DEVELOPER -> "About Developer"
                                ScreenType.RECORDINGS -> "Video Recordings"
                                ScreenType.EXCLUDED_APPS -> "Excluded Apps"
                                ScreenType.INSTRUCTIONS -> "Instructions"
                                ScreenType.BACKUP_RESTORE -> "Backup & Restore"
                                ScreenType.WALLPAPER -> "Auto Wallpaper"
                            },

                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                    },
                    navigationIcon = {
                        if (currentScreen == ScreenType.DASHBOARD) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = onSurfaceColor)
                            }
                        } else {
                            IconButton(onClick = { currentScreen = ScreenType.DASHBOARD }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onSurfaceColor)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (currentScreen) {
                        ScreenType.DASHBOARD -> DashboardScreen(
                            isServiceRunning = isServiceRunning,
                            isPermissionEnabled = isPermissionEnabled,
                            onOpenSettings = onOpenSettings,
                            onNavigate = { currentScreen = it }
                        )
                        ScreenType.GESTURES -> GesturesTab(
                            actions = uiState.actions,
                            onConfigureGesture = { editingActionGesture = it }
                        )
                        ScreenType.CALIBRATION -> CalibrationTab(
                            config = uiState.config,
                            onConfigChange = { viewModel.updateConfig(it) },
                            onConfigChangeDebounced = { viewModel.updateConfigDebounced(it) }
                        )
                        ScreenType.SIDE_DECK -> SideDeckTab(
                            sideDeckConfig = uiState.sideDeckConfig,
                            isServiceActive = isServiceActive,
                            onConfigChange = { viewModel.updateSideDeckConfig(it) },
                            onConfigChangeDebounced = { viewModel.updateSideDeckConfigDebounced(it) },
                            onOpenSettings = onOpenSettings
                        )
                        ScreenType.CODE_DETECTION -> CodeDetectionTab(
                            config = uiState.codeConfig,
                            detectedCodes = uiState.detectedCodes,
                            onConfigChange = { viewModel.updateCodeConfig(it) },
                            onConfigChangeDebounced = { viewModel.updateCodeConfigDebounced(it) },
                            onClearHistory = { viewModel.clearDetectedCodes() },
                            onDeleteHistoryItem = { viewModel.deleteDetectedCode(it) },
                            onAddDetectedCode = { viewModel.addDetectedCode(it) }
                        )
                        ScreenType.TEXT_ASSISTANT -> TextAssistantTab(
                            viewModel = viewModel,
                            onOpenSettings = onOpenSettings
                        )
                        ScreenType.ANALYTICS -> AnalyticsTab(

                            stats = uiState.stats,
                            onResetStats = { viewModel.resetStats() },
                            onSimulateGesture = { viewModel.triggerTestGesture(it) },
                            testMessage = testMessage
                        )
                        ScreenType.DEVELOPER -> DeveloperTab()
                        ScreenType.RECORDINGS -> RecordingsTab()
                        ScreenType.EXCLUDED_APPS -> ExcludedAppsTab(
                            notchConfig = uiState.config,
                            onConfigChange = { viewModel.updateConfig(it) }
                        )
                        ScreenType.INSTRUCTIONS -> com.example.ui.InstructionsTab()
                        ScreenType.BACKUP_RESTORE -> BackupRestoreTab(
                            repository = repository
                        )
                        ScreenType.WALLPAPER -> com.example.ui.WallpaperTab()
                    }
                }
            }
        }
    }

    // Gesture Configuration Bottom Sheet/Dialog
    if (editingActionGesture != null) {
        val currentEntity = uiState.actions.find { it.gestureName == editingActionGesture }
        ActionPickerSheet(
            gestureName = editingActionGesture!!,
            currentAction = currentEntity?.actionType ?: "NONE",
            currentPackage = currentEntity?.packageToLaunch,
            currentExtra = currentEntity?.extraValue,
            onDismiss = { editingActionGesture = null },
            onActionSelected = { actionType, packageName, label, extraValue ->
                if (actionType == "SPY_CAM_BACK" || actionType == "SPY_CAM_FRONT") {
                    if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }
                viewModel.updateAction(
                    GestureActionEntity(
                        gestureName = editingActionGesture!!,
                        actionType = actionType,
                        packageToLaunch = packageName,
                        label = label,
                        extraValue = extraValue
                    )
                )
                editingActionGesture = null
            }
        )
    }
}

@Composable
fun AppHeader(
    isServiceRunning: Boolean,
    isPermissionEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val statusActiveBg = if (isDark) Color(0x3322C55E) else Color(0x2216A34A)
    val statusInactiveBg = if (isDark) Color(0x33F59E0B) else Color(0x22D97706)
    val statusActiveBorder = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val statusInactiveBorder = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val statusActiveText = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val statusInactiveText = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SkY Touch",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.testTag("app_title")
                    )
                    Text(
                        text = "Camera Notch Gestures",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                val badgeBg = if (isServiceRunning) statusActiveBg else statusInactiveBg
                val badgeBorder = if (isServiceRunning) statusActiveBorder else statusInactiveBorder
                val badgeText = if (isServiceRunning) "ACTIVE" else if (isPermissionEnabled) "STALLED" else "INACTIVE"
                val badgeTextColor = if (isServiceRunning) statusActiveText else statusInactiveText

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(badgeBg)
                        .border(
                            1.dp,
                            badgeBorder,
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeBorder)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeText,
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions / Trigger Button based on explicit states
            if (isServiceRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = statusActiveText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Touch/Swipe near camera to trigger your shortcuts!",
                        color = statusActiveText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Need to make changes? Tap settings to disable/re-enable service anytime.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            } else if (isPermissionEnabled) {
                Text(
                    text = "Service is enabled in your system settings but has been suspended or paused by Android's battery/process management. Toggle 'SkY Touch' off and on again to wake it up!",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_service_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restart Service (Toggle On/Off)", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Accessibility Service is turned off. Enable 'SkY Touch' to start listening for gestures near your front camera.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_service_button")
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Service Permission", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    isServiceRunning: Boolean,
    isPermissionEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onNavigate: (ScreenType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isIgnoringBattery by remember { mutableStateOf(true) }
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }

    LaunchedEffect(context) {
        while (true) {
            isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
            delay(1500)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppHeader(
            isServiceRunning = isServiceRunning,
            isPermissionEnabled = isPermissionEnabled,
            onOpenSettings = onOpenSettings
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 25.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isIgnoringBattery) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background Run Restricted",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Android battery restrictions might stop SkY Touch background gestures. Click here to allow running indefinitely.",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                DashboardCard(
                    title = "Notch Gestures",
                    description = "Configure custom swipe actions around your front camera.",
                    icon = Icons.Filled.TouchApp,
                    onClick = { onNavigate(ScreenType.GESTURES) }
                )
            }
            item {
                DashboardCard(
                    title = "Notch Position",
                    description = "Adjust notch position, size, and sensitivity.",
                    icon = Icons.Filled.GpsFixed,
                    onClick = { onNavigate(ScreenType.CALIBRATION) }
                )
            }
            item {
                DashboardCard(
                    title = "Side Deck",
                    description = "Manage quick access panel and floating edge handle.",
                    icon = Icons.Filled.ViewSidebar,
                    onClick = { onNavigate(ScreenType.SIDE_DECK) }
                )
            }
            item {
                DashboardCard(
                    title = "OTP & Code Detector",
                    description = "Background interception, auto-copy to clipboard, and live test sandbox.",
                    icon = Icons.Filled.VpnKey,
                    onClick = { onNavigate(ScreenType.CODE_DETECTION) }
                )
            }
            item {
                DashboardCard(
                    title = "Text Assistant & AI",
                    description = "Instant snippet expansions and smart Gemini AI rewriting across any app.",
                    icon = Icons.Filled.AutoFixHigh,
                    onClick = { onNavigate(ScreenType.TEXT_ASSISTANT) }
                )
            }
            item {
                DashboardCard(
                    title = "Automatic Wallpaper",
                    description = "Change wallpaper automatically from a selected folder.",
                    icon = Icons.Filled.Wallpaper,
                    onClick = { onNavigate(ScreenType.WALLPAPER) }
                )
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GesturesTab(
    actions: List<GestureActionEntity>,
    onConfigureGesture: (String) -> Unit
) {
    val gestureIcons = mapOf(
        "SINGLE_TAP" to Icons.Default.TouchApp,
        "DOUBLE_TAP" to Icons.Default.Gesture,
        "TRIPLE_TAP" to Icons.Default.Filter3,
        "LONG_PRESS" to Icons.Default.Timer,
        "SWIPE_LEFT" to Icons.AutoMirrored.Filled.ArrowBack,
        "SWIPE_RIGHT" to Icons.Default.ArrowForward,
        "SWIPE_LEFT_AND_HOLD" to Icons.Default.FastRewind,
        "SWIPE_RIGHT_AND_HOLD" to Icons.Default.FastForward
    )

    val gestureLabels = mapOf(
        "SINGLE_TAP" to "Single Click",
        "DOUBLE_TAP" to "Double Click",
        "TRIPLE_TAP" to "Triple Click",
        "LONG_PRESS" to "Long Press",
        "SWIPE_LEFT" to "Swipe Left",
        "SWIPE_RIGHT" to "Swipe Right",
        "SWIPE_LEFT_AND_HOLD" to "Swipe Left & Hold",
        "SWIPE_RIGHT_AND_HOLD" to "Swipe Right & Hold"
    )

    val gestureOrder = listOf(
        "SINGLE_TAP",
        "DOUBLE_TAP",
        "TRIPLE_TAP",
        "LONG_PRESS",
        "SWIPE_LEFT",
        "SWIPE_RIGHT",
        "SWIPE_LEFT_AND_HOLD",
        "SWIPE_RIGHT_AND_HOLD"
    )

    val visibleActions = actions
        .filter { it.gestureName != "SWIPE_DOWN" }
        .sortedBy { val idx = gestureOrder.indexOf(it.gestureName); if (idx != -1) idx else 99 }

    val context = LocalContext.current
    val hasBrightnessAction = visibleActions.any { it.actionType in listOf("BRIGHTNESS_UP", "BRIGHTNESS_DOWN", "CUSTOM_BRIGHTNESS") }
    val isWriteSettingsOk = remember(context) { isWriteSettingsGranted(context) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("gestures_list")
    ) {
        item {
            Text(
                text = "Gesture Shortcut Actions",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Configure actions to execute when you touch or swipe around the camera.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (hasBrightnessAction && !isWriteSettingsOk) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Brightness Permission Needed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Allow 'Modify system settings' so gesture actions can change screen brightness.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(visibleActions, key = { it.gestureName }) { gestureAction ->
            val gestureName = gestureAction.gestureName
            val icon = gestureIcons[gestureName] ?: Icons.Default.Adjust
            val prettyName = gestureLabels[gestureName] ?: gestureName

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfigureGesture(gestureName) }
                    .testTag("gesture_item_$gestureName"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = prettyName,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = prettyName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = getActionDescription(gestureAction),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (gestureAction.actionType == "NONE") "Disabled" else "Edit",
                            color = if (gestureAction.actionType == "NONE") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (gestureAction.actionType == "NONE") Color.Transparent else MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Edit Action",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

fun getActionDescription(entity: GestureActionEntity): String {
    val isHold = entity.gestureName in listOf("LONG_PRESS", "SWIPE_LEFT_AND_HOLD", "SWIPE_RIGHT_AND_HOLD")
    return when (entity.actionType) {
        "BACK" -> "Go Back"
        "HOME" -> "Go Home"
        "RECENTS" -> "Open Recents"
        "NOTIFICATIONS" -> "Open Notifications"
        "QUICK_SETTINGS" -> "Open Quick Settings"
        "SCREENSHOT" -> "Take Screenshot"
        "FLASHLIGHT" -> "Toggle Flashlight"
        "MEDIA_PLAY_PAUSE" -> "Play / Pause Media"
        "MEDIA_NEXT" -> "Next Track"
        "MEDIA_PREVIOUS" -> "Previous Track"
        "SPY_CAM_BACK" -> "Spy Cam (Silent Back Record)"
        "SPY_CAM_FRONT" -> "Spy Cam (Silent Front Record)"
        "VOLUME_UP" -> if (isHold) "Volume Up (Hold to repeat & accelerate)" else "Volume Up"
        "VOLUME_DOWN" -> if (isHold) "Volume Down (Hold to repeat & accelerate)" else "Volume Down"
        "BRIGHTNESS_UP" -> if (isHold) "Increase Brightness (Hold to repeat & accelerate)" else "Increase Brightness (+8%)"
        "BRIGHTNESS_DOWN" -> if (isHold) "Decrease Brightness (Hold to repeat & accelerate)" else "Decrease Brightness (-8%)"
        "CUSTOM_BRIGHTNESS" -> "Set Brightness to ${entity.extraValue ?: "50"}%"
        "LAUNCH_APP" -> if (entity.label.startsWith("Open ")) entity.label else "Open App: ${entity.label}"
        "SHORTCUT" -> if (entity.label.startsWith("Shortcut: ")) entity.label else "Shortcut: ${entity.label}"
        else -> "Disabled (No Action)"
    }
}

@Composable
fun CalibrationTab(
    config: NotchConfigEntity,
    onConfigChange: (NotchConfigEntity) -> Unit,
    onConfigChangeDebounced: ((NotchConfigEntity) -> Unit)? = null
) {
    var localWidth by remember(config.widthDp) { mutableFloatStateOf(config.widthDp.toFloat()) }
    var localHeight by remember(config.heightDp) { mutableFloatStateOf(config.heightDp.toFloat()) }
    var localYOffset by remember(config.yOffsetDp) { mutableFloatStateOf(config.yOffsetDp.toFloat()) }
    var localXOffset by remember(config.xOffsetDp) { mutableFloatStateOf(config.xOffsetDp.toFloat()) }
    var localOpacity by remember(config.overlayOpacity) { mutableFloatStateOf(config.overlayOpacity) }

    // Live combined config reflecting immediate drag/slider motion for 60-120fps preview
    val liveConfig = remember(config, localWidth, localHeight, localYOffset, localXOffset, localOpacity) {
        config.copy(
            widthDp = localWidth.roundToInt(),
            heightDp = localHeight.roundToInt(),
            yOffsetDp = localYOffset.roundToInt(),
            xOffsetDp = localXOffset.roundToInt(),
            overlayOpacity = localOpacity
        )
    }

    val notifyChange: (NotchConfigEntity, Boolean) -> Unit = { updated, finished ->
        if (finished) {
            onConfigChange(updated)
        } else if (onConfigChangeDebounced != null) {
            onConfigChangeDebounced(updated)
        } else {
            onConfigChange(updated)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("calibration_section")
    ) {
        item {
            Text(
                text = "Notch Calibration & Visuals",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Adjust the position and size of the overlay to perfectly wrap around your front camera.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Notch Interactive Preview mockup
        item {
            NotchPreviewMockup(
                config = liveConfig,
                onConfigChange = { updated ->
                    localXOffset = updated.xOffsetDp.toFloat()
                    localYOffset = updated.yOffsetDp.toFloat()
                    notifyChange(updated, false)
                },
                onConfigChangeFinished = { updated ->
                    notifyChange(updated, true)
                }
            )
        }

        // Dimension Controls
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Dimensions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Width slider
                    SliderSettingRow(
                        label = "Width",
                        value = localWidth,
                        valueRange = 30f..250f,
                        suffix = "dp",
                        onValueChange = {
                            localWidth = it
                            notifyChange(liveConfig.copy(widthDp = it.roundToInt()), false)
                        },
                        onValueChangeFinished = {
                            notifyChange(liveConfig.copy(widthDp = localWidth.roundToInt()), true)
                        }
                    )

                    // Height slider
                    SliderSettingRow(
                        label = "Height",
                        value = localHeight,
                        valueRange = 10f..100f,
                        suffix = "dp",
                        onValueChange = {
                            localHeight = it
                            notifyChange(liveConfig.copy(heightDp = it.roundToInt()), false)
                        },
                        onValueChangeFinished = {
                            notifyChange(liveConfig.copy(heightDp = localHeight.roundToInt()), true)
                        }
                    )

                    // Y Offset Slider
                    SliderSettingRow(
                        label = "Y Offset (Distance from top)",
                        value = localYOffset,
                        valueRange = 0f..120f,
                        suffix = "dp",
                        onValueChange = {
                            localYOffset = it
                            notifyChange(liveConfig.copy(yOffsetDp = it.roundToInt()), false)
                        },
                        onValueChangeFinished = {
                            notifyChange(liveConfig.copy(yOffsetDp = localYOffset.roundToInt()), true)
                        }
                    )

                    // X Offset Slider
                    SliderSettingRow(
                        label = "X Offset (Left/Right alignment)",
                        value = localXOffset,
                        valueRange = -150f..150f,
                        suffix = "dp",
                        onValueChange = {
                            localXOffset = it
                            notifyChange(liveConfig.copy(xOffsetDp = it.roundToInt()), false)
                        },
                        onValueChangeFinished = {
                            notifyChange(liveConfig.copy(xOffsetDp = localXOffset.roundToInt()), true)
                        }
                    )
                }
            }
        }

        // Aesthetics & Haptics Control
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Appearance & Feedback", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Shape selector
                    Text("Overlay Shape", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("capsule" to "Capsule", "circle" to "Circle", "rect" to "Rectangle").forEach { (type, label) ->
                            val isSelected = config.shapeType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .clickable { onConfigChange(config.copy(shapeType = type)) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Show Visual Overlay toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Draw Visual Overlay Layer", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Renders colored bar/circle around notch to see zone", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.showVisualOverlay,
                            onCheckedChange = { onConfigChange(config.copy(showVisualOverlay = it)) }
                        )
                    }

                    AnimatedVisibility(visible = config.showVisualOverlay) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Color selection row
                            Text("Overlay Color", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val colorsList = listOf(
                                    "#FF000000" to "Cyber Black",
                                    "#FF2196F3" to "Neon Blue",
                                    "#FF4CAF50" to "Emerald Green",
                                    "#FF9C27B0" to "Glowing Violet",
                                    "#FFE91E63" to "Hot Pink"
                                )
                                colorsList.forEach { (hex, name) ->
                                    val isSelected = config.overlayColorHex.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { onConfigChange(config.copy(overlayColorHex = hex)) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Opacity slider
                            SliderSettingRow(
                                label = "Overlay Opacity",
                                value = localOpacity,
                                valueRange = 0.1f..1.0f,
                                suffix = "",
                                isFloat = true,
                                onValueChange = {
                                    localOpacity = it
                                    notifyChange(liveConfig.copy(overlayOpacity = it), false)
                                },
                                onValueChangeFinished = {
                                    notifyChange(liveConfig.copy(overlayOpacity = localOpacity), true)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Vibration feedback selector
                    Text("Haptic Vibration Strength", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Off", 1 to "Light", 2 to "Medium", 3 to "Heavy").forEach { (strength, name) ->
                            val isSelected = config.vibrationStrength == strength
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onConfigChange(config.copy(vibrationStrength = strength)) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun SliderSettingRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    suffix: String,
    isFloat: Boolean = false,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(
                text = if (isFloat) "%.1f".format(value) else "${value.toInt()}$suffix",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
fun NotchPreviewMockup(
    config: NotchConfigEntity,
    onConfigChange: (NotchConfigEntity) -> Unit,
    onConfigChangeFinished: ((NotchConfigEntity) -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Live Preview (Drag Overlay Below)",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Phone shell mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F0E17))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                // Status bar mockup time/icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("09:41", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = "wifi", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                        Icon(imageVector = Icons.Default.BatteryFull, contentDescription = "battery", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                    }
                }

                // Interactive Notch representation inside mockup
                val density = 1.0f // Using relative mapping inside the mockup Box
                val mockupWidth = (config.widthDp * 0.7f).dp
                val mockupHeight = (config.heightDp * 0.7f).dp
                val mockupY = (config.yOffsetDp * 0.7f).dp
                val mockupX = (config.xOffsetDp * 0.7f).dp

                Box(
                    modifier = Modifier
                        .offset(x = mockupX, y = mockupY)
                        .align(Alignment.TopCenter)
                        .size(width = mockupWidth, height = mockupHeight)
                        .clip(
                            if (config.shapeType == "circle") CircleShape
                            else if (config.shapeType == "capsule") RoundedCornerShape(config.cornerRadiusDp.dp)
                            else RoundedCornerShape(0.dp)
                        )
                        .background(
                            if (config.showVisualOverlay) {
                                try {
                                    val baseColor = Color(android.graphics.Color.parseColor(config.overlayColorHex))
                                    baseColor.copy(alpha = config.overlayOpacity)
                                } catch (e: Exception) {
                                    Color.Black
                                }
                            } else {
                                Color.Gray.copy(alpha = 0.2f)
                            }
                        )
                        .border(
                            1.dp,
                            if (config.showVisualOverlay) Color.White.copy(alpha = 0.5f) else Color.Red,
                            if (config.shapeType == "circle") CircleShape
                            else if (config.shapeType == "capsule") RoundedCornerShape(config.cornerRadiusDp.dp)
                            else RoundedCornerShape(0.dp)
                        )
                        .pointerInput(config) {
                            detectDragGestures(
                                onDragEnd = {
                                    onConfigChangeFinished?.invoke(config)
                                },
                                onDragCancel = {
                                    onConfigChangeFinished?.invoke(config)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val scaleFactor = 1.4f
                                    val newX = (config.xOffsetDp + dragAmount.x / scaleFactor).coerceIn(-150f, 150f)
                                    val newY = (config.yOffsetDp + dragAmount.y / scaleFactor).coerceIn(0f, 120f)
                                    onConfigChange(
                                        config.copy(
                                            xOffsetDp = newX.roundToInt(),
                                            yOffsetDp = newY.roundToInt()
                                        )
                                    )
                                }
                            )
                        }
                )

                // Simulated Front Camera Lens inside notch
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF070B19))
                        .align(Alignment.TopCenter)
                        .offset(y = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.Center)
                    )
                }

                // Help Tip
                Text(
                    text = "Adjust using sliders, or drag the overlay directly inside this screen preview!",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun AnalyticsTab(
    stats: List<TriggerStatEntity>,
    onResetStats: () -> Unit,
    onSimulateGesture: (String) -> Unit,
    testMessage: String?
) {
    val gesturePrettyNames = mapOf(
        "SINGLE_TAP" to "Single Click",
        "DOUBLE_TAP" to "Double Click",
        "TRIPLE_TAP" to "Triple Click",
        "LONG_PRESS" to "Long Press",
        "SWIPE_LEFT" to "Swipe Left",
        "SWIPE_RIGHT" to "Swipe Right",
        "SWIPE_LEFT_AND_HOLD" to "Swipe Left & Hold",
        "SWIPE_RIGHT_AND_HOLD" to "Swipe Right & Hold"
    )

    val gestureOrder = listOf(
        "SINGLE_TAP",
        "DOUBLE_TAP",
        "TRIPLE_TAP",
        "LONG_PRESS",
        "SWIPE_LEFT",
        "SWIPE_RIGHT",
        "SWIPE_LEFT_AND_HOLD",
        "SWIPE_RIGHT_AND_HOLD"
    )

    val visibleStats = stats
        .filter { it.gestureName != "SWIPE_DOWN" }
        .sortedBy { val idx = gestureOrder.indexOf(it.gestureName); if (idx != -1) idx else 99 }
    val totalTriggers = visibleStats.sumOf { it.count }

    val isDark = isSystemInDarkTheme()
    val bannerBg = if (isDark) Color(0x3322C55E) else Color(0xFFDCFCE7)
    val bannerBorder = if (isDark) Color(0xFF4ADE80) else Color(0xFF86EFAC)
    val bannerText = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("analytics_section")
    ) {
        item {
            Text(
                text = "Gesture Usage Analytics",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Track your gestures statistics and test physical accuracy inside the application.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Stats summary block
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Triggers Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Total Actions Run", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = totalTriggers.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                // Quick Reset Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onResetStats() }
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Reset Logs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Stats detailed progress list
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Usage Breakdown", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    if (visibleStats.isEmpty()) {
                        Text(
                            text = "No gesture stats tracked yet. Use gestures outside or test below!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        visibleStats.forEach { stat ->
                            val prettyName = gesturePrettyNames[stat.gestureName] ?: stat.gestureName
                            val percentage = if (totalTriggers > 0) stat.count.toFloat() / totalTriggers else 0f

                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(prettyName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text(
                                        text = "${stat.count} runs",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { percentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Interactive Practice Sandbox / Gesture Tester
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tester_zone")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Interactive Calibration Tester",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Click or swipe on the simulated target below to test your speed & coordinate mappings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated Touch Target Box
                    var clickCount by remember { mutableIntStateOf(0) }
                    var lastClickTime by remember { mutableLongStateOf(0L) }
                    var feedbackText by remember { mutableStateOf("TAP / SWIPE / HOLD TARGET") }
                    val coroutineScope = rememberCoroutineScope()

                    var dragStartX by remember { mutableFloatStateOf(0f) }
                    var dragStartTime by remember { mutableLongStateOf(0L) }
                    var isSwipeHoldDetected by remember { mutableStateOf(false) }
                    var currentDragDeltaX by remember { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .size(width = 180.dp, height = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(26.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        val now = System.currentTimeMillis()
                                        if (now - lastClickTime < 350L) {
                                            clickCount++
                                        } else {
                                            clickCount = 1
                                        }
                                        lastClickTime = now

                                        if (clickCount >= 3) {
                                            clickCount = 0
                                            feedbackText = "Triple Tap!"
                                            onSimulateGesture("TRIPLE_TAP")
                                        } else {
                                            val c = clickCount
                                            coroutineScope.launch {
                                                delay(350L)
                                                if (lastClickTime == now) {
                                                    if (c == 2) {
                                                        feedbackText = "Double Tap!"
                                                        onSimulateGesture("DOUBLE_TAP")
                                                    } else if (c == 1) {
                                                        feedbackText = "Single Tap!"
                                                        onSimulateGesture("SINGLE_TAP")
                                                    }
                                                    clickCount = 0
                                                }
                                            }
                                        }
                                    },
                                    onLongPress = {
                                        feedbackText = "Long Press!"
                                        onSimulateGesture("LONG_PRESS")
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragStartX = offset.x
                                        dragStartTime = System.currentTimeMillis()
                                        isSwipeHoldDetected = false
                                        currentDragDeltaX = 0f
                                    },
                                    onDragEnd = {
                                        if (!isSwipeHoldDetected) {
                                            if (currentDragDeltaX > 25f) {
                                                feedbackText = "Swipe Right!"
                                                onSimulateGesture("SWIPE_RIGHT")
                                            } else if (currentDragDeltaX < -25f) {
                                                feedbackText = "Swipe Left!"
                                                onSimulateGesture("SWIPE_LEFT")
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentDragDeltaX += dragAmount.x
                                        val elapsed = System.currentTimeMillis() - dragStartTime
                                        if (abs(currentDragDeltaX) > 30f && elapsed > 400L && !isSwipeHoldDetected) {
                                            isSwipeHoldDetected = true
                                            if (currentDragDeltaX < 0) {
                                                feedbackText = "Swipe Left & Hold!"
                                                onSimulateGesture("SWIPE_LEFT_AND_HOLD")
                                            } else {
                                                feedbackText = "Swipe Right & Hold!"
                                                onSimulateGesture("SWIPE_RIGHT_AND_HOLD")
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = feedbackText.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Or tap any trigger button below:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 2 rows of 4 quick simulation trigger buttons
                    val quickButtons = listOf(
                        listOf(
                            "SINGLE_TAP" to "1x Tap",
                            "DOUBLE_TAP" to "2x Tap",
                            "TRIPLE_TAP" to "3x Tap",
                            "LONG_PRESS" to "Long Press"
                        ),
                        listOf(
                            "SWIPE_LEFT" to "Swipe Left",
                            "SWIPE_RIGHT" to "Swipe Right",
                            "SWIPE_LEFT_AND_HOLD" to "Swipe L & Hold",
                            "SWIPE_RIGHT_AND_HOLD" to "Swipe R & Hold"
                        )
                    )

                    quickButtons.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { (gestureKey, btnLabel) ->
                                OutlinedButton(
                                    onClick = {
                                        feedbackText = "$btnLabel triggered"
                                        onSimulateGesture(gestureKey)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = btnLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedVisibility(
                        visible = testMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = testMessage ?: "",
                            color = bannerText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(bannerBg)
                                .border(1.dp, bannerBorder, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

fun isWriteSettingsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.System.canWrite(context)
    } else {
        true
    }
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

// In-memory cache so opening the gesture picker sheet is instant without querying PackageManager repeatedly
object AppInfoCache {
    @Volatile
    var cachedLauncherApps: List<AppEntry>? = null
    @Volatile
    var cachedShortcutApps: List<AppEntry>? = null

    fun getOrLoadLauncherApps(context: Context): List<AppEntry> {
        cachedLauncherApps?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = pm.queryIntentActivities(intent, 0)
        val list = resolveList.mapNotNull { resolveInfo ->
            try {
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                val iconDrawable = resolveInfo.loadIcon(pm)
                val bitmap = iconDrawable.toBitmapOrNull()
                AppEntry(label = label, packageName = pkg, icon = bitmap?.asImageBitmap())
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
        cachedLauncherApps = list
        return list
    }

    fun getOrLoadShortcutApps(context: Context): List<AppEntry> {
        cachedShortcutApps?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val resolveList = pm.queryIntentActivities(intent, 0)
        val list = resolveList.mapNotNull { resolveInfo ->
            try {
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                val iconDrawable = resolveInfo.loadIcon(pm)
                val bitmap = iconDrawable.toBitmapOrNull()
                AppEntry(label = label, packageName = pkg, icon = bitmap?.asImageBitmap())
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
        cachedShortcutApps = list
        return list
    }
}

data class ShortcutOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

fun Drawable.toBitmapOrNull(): Bitmap? {
    return try {
        if (this is BitmapDrawable && this.bitmap != null) {
            return this.bitmap
        }
        val width = if (intrinsicWidth in 1..96) intrinsicWidth else 72
        val height = if (intrinsicHeight in 1..96) intrinsicHeight else 72
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    gestureName: String,
    currentAction: String,
    currentPackage: String?,
    currentExtra: String?,
    onDismiss: () -> Unit,
    onActionSelected: (String, String?, String, String?) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(0) } // 0: System, 1: Brightness, 2: Open App, 3: Shortcuts
    var appSearchQuery by remember { mutableStateOf("") }
    var customBrightnessSlider by remember {
        mutableFloatStateOf(
            if (currentAction == "CUSTOM_BRIGHTNESS") {
                (currentExtra?.toFloatOrNull() ?: 50f).coerceIn(5f, 100f)
            } else {
                50f
            }
        )
    }

    val gestureLabels = mapOf(
        "SINGLE_TAP" to "Single Click",
        "DOUBLE_TAP" to "Double Click",
        "TRIPLE_TAP" to "Triple Click",
        "LONG_PRESS" to "Long Press",
        "SWIPE_LEFT" to "Swipe Left",
        "SWIPE_RIGHT" to "Swipe Right",
        "SWIPE_LEFT_AND_HOLD" to "Swipe Left & Hold",
        "SWIPE_RIGHT_AND_HOLD" to "Swipe Right & Hold"
    )
    val prettyGesture = gestureLabels[gestureName] ?: gestureName

    // Query installed launcher apps (cached in-memory, loads only when Open App category is visited)
    val installedApps by produceState<List<AppEntry>>(initialValue = AppInfoCache.cachedLauncherApps ?: emptyList(), selectedCategory) {
        if (selectedCategory == 2 && value.isEmpty()) {
            withContext(Dispatchers.IO) {
                value = AppInfoCache.getOrLoadLauncherApps(context)
            }
        }
    }

    // Query apps supporting CREATE_SHORTCUT (cached in-memory, loads only when Shortcuts category is visited)
    val shortcutApps by produceState<List<AppEntry>>(initialValue = AppInfoCache.cachedShortcutApps ?: emptyList(), selectedCategory) {
        if (selectedCategory == 3 && value.isEmpty()) {
            withContext(Dispatchers.IO) {
                value = AppInfoCache.getOrLoadShortcutApps(context)
            }
        }
    }

    // Shortcut creator launcher
    var pendingShortcutPkg by remember { mutableStateOf<String?>(null) }
    val shortcutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val shortcutIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
            }
            val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Custom Shortcut"
            if (shortcutIntent != null) {
                val uri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
                onActionSelected("SHORTCUT", pendingShortcutPkg, "Shortcut: $shortcutName", uri)
            }
        }
    }

    val systemActions = listOf(
        Triple("NONE", null, "Disabled (No Action)"),
        Triple("BACK", null, "Go Back"),
        Triple("HOME", null, "Go Home"),
        Triple("RECENTS", null, "Open Recents Overview"),
        Triple("NOTIFICATIONS", null, "Open Notifications Shade"),
        Triple("QUICK_SETTINGS", null, "Open Quick Settings Panel"),
        Triple("SCREENSHOT", null, "Take Instant Screenshot"),
        Triple("FLASHLIGHT", null, "Toggle Camera Flashlight"),
        Triple("MEDIA_PLAY_PAUSE", null, "Media Play / Pause"),
        Triple("MEDIA_NEXT", null, "Next Track"),
        Triple("MEDIA_PREVIOUS", null, "Previous Track"),
        Triple("SPY_CAM_BACK", null, "Spy Cam (Silent Back Record)"),
        Triple("SPY_CAM_FRONT", null, "Spy Cam (Silent Front Record)"),
        Triple("VOLUME_UP", null, "Raise Music Volume"),
        Triple("VOLUME_DOWN", null, "Lower Music Volume")
    )

    val instantShortcuts = listOf(
        ShortcutOption("SHORTCUT_SELFIE", "Selfie Camera", "Launch front camera directly", Icons.Default.CameraAlt),
        ShortcutOption("SHORTCUT_SEARCH", "Google Web Search", "Open Google Search immediately", Icons.Default.Search),
        ShortcutOption("SHORTCUT_ALARM", "Clock & Alarms", "Quick access to alarms and timers", Icons.Default.Alarm),
        ShortcutOption("SHORTCUT_EMAIL", "Compose Email", "Create a draft in default email app", Icons.Default.Mail),
        ShortcutOption("SHORTCUT_BATTERY", "Battery Saver & Usage", "Quick toggle battery settings and stats", Icons.Default.BatteryChargingFull),
        ShortcutOption("SHORTCUT_WIFI", "Wi-Fi Settings", "Manage wireless networks and connections", Icons.Default.Wifi),
        ShortcutOption("SHORTCUT_BLUETOOTH", "Bluetooth Settings", "Pair and switch Bluetooth accessories", Icons.Default.Bluetooth),
        ShortcutOption("SHORTCUT_SOUND", "Sound & Vibration", "Quick volume and sound profile settings", Icons.Default.VolumeUp),
        ShortcutOption("SHORTCUT_DISPLAY", "Display & Timeout", "Screen timeout and dark mode settings", Icons.Default.Brightness6),
        ShortcutOption("SHORTCUT_APPS", "Manage Apps", "View installed applications and storage", Icons.Default.Apps)
    )

    val categories = listOf("System", "Open App", "Shortcuts")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Configure $prettyGesture",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Assign action to trigger near your front camera:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Category Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedCategory,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    categories.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedCategory == index,
                            onClick = { selectedCategory = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedCategory == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Category Contents
                when (selectedCategory) {
                    0 -> {
                        // System Actions
                        val isHold = gestureName in listOf("LONG_PRESS", "SWIPE_LEFT_AND_HOLD", "SWIPE_RIGHT_AND_HOLD")
                        val isWriteGranted = remember(context) { isWriteSettingsGranted(context) }
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(systemActions, key = { it.first }) { (type, pkg, label) ->
                                val isSelected = currentAction == type
                                val sub = when (type) {
                                    "NONE" -> "No gesture action"
                                    "VOLUME_UP" -> if (isHold) "Raise Volume • Supports continuous hold-to-repeat (accelerates in 2s)" else "Raise Music Volume"
                                    "VOLUME_DOWN" -> if (isHold) "Lower Volume • Supports continuous hold-to-repeat (accelerates in 2s)" else "Lower Music Volume"
                                    else -> "System navigation"
                                }
                                ActionRowItem(
                                    title = label,
                                    subtitle = sub,
                                    isSelected = isSelected,
                                    onClick = { onActionSelected(type, null, label, null) }
                                )
                            }
                            
                            // BRIGHTNESS SECTION
                            if (!isWriteGranted) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Permission Needed", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                                Text("Allow 'Modify system settings' for brightness gestures to work.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                        try {
                                                            context.startActivity(
                                                                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                                    data = Uri.parse("package:${context.packageName}")
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                            )
                                                        } catch (_: Exception) {}
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                shape = CircleShape,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Grant", fontSize = 11.sp, color = MaterialTheme.colorScheme.onError)
                                            }
                                        }
                                    }
                                }
                            }

                            // 1. Increase Brightness
                            item {
                                val isSelected = currentAction == "BRIGHTNESS_UP"
                                ActionRowItem(
                                    title = "Increase Brightness",
                                    subtitle = if (isHold) "Steps brightness up • Supports continuous hold-to-repeat (accelerates in 2s)" else "Steps brightness up by 8% on every gesture trigger",
                                    isSelected = isSelected,
                                    onClick = {
                                        onActionSelected("BRIGHTNESS_UP", null, "Increase Brightness", null)
                                    }
                                )
                            }

                            // 2. Decrease Brightness
                            item {
                                val isSelected = currentAction == "BRIGHTNESS_DOWN"
                                ActionRowItem(
                                    title = "Decrease Brightness",
                                    subtitle = if (isHold) "Steps brightness down • Supports continuous hold-to-repeat (accelerates in 2s)" else "Steps brightness down by 8% on every gesture trigger",
                                    isSelected = isSelected,
                                    onClick = {
                                        onActionSelected("BRIGHTNESS_DOWN", null, "Decrease Brightness", null)
                                    }
                                )
                            }

                            // 3. Custom Brightness
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Custom Brightness",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${customBrightnessSlider.roundToInt()}%",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Text(
                                            text = "Instantly set the screen to this exact level when touched",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )

                                        Slider(
                                            value = customBrightnessSlider,
                                            onValueChange = { customBrightnessSlider = it },
                                            valueRange = 5f..100f,
                                            steps = 18,
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )

                                        // Preset chips
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            listOf(10, 25, 50, 75, 100).forEach { preset ->
                                                val isChipSelected = customBrightnessSlider.roundToInt() == preset
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(if (isChipSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                        .border(1.dp, if (isChipSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                        .clickable { customBrightnessSlider = preset.toFloat() }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "$preset%",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isChipSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isChipSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Button(
                                            onClick = {
                                                val pct = customBrightnessSlider.roundToInt()
                                                onActionSelected(
                                                    "CUSTOM_BRIGHTNESS",
                                                    null,
                                                    "Set Brightness to $pct%",
                                                    pct.toString()
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text(
                                                text = "Save Custom Brightness (${customBrightnessSlider.roundToInt()}%)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Open Any App Tab
                        val filteredApps = remember(installedApps, appSearchQuery) {
                            if (appSearchQuery.isBlank()) {
                                installedApps
                            } else {
                                installedApps.filter {
                                    it.label.contains(appSearchQuery, ignoreCase = true) ||
                                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                placeholder = { Text("Search any app...", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (appSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { appSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            if (filteredApps.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (installedApps.isEmpty()) "Loading installed apps..." else "No apps match '$appSearchQuery'",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    items(filteredApps, key = { it.packageName }) { app ->
                                        val isSelected = currentAction == "LAUNCH_APP" && currentPackage == app.packageName
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(24.dp)
                                                )
                                                .clickable {
                                                    onActionSelected("LAUNCH_APP", app.packageName, "Open ${app.label}", null)
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (app.icon != null) {
                                                Image(
                                                    bitmap = app.icon,
                                                    contentDescription = app.label,
                                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.label,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 14.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Shortcuts Tab
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            // Native App Shortcuts Section
                            if (shortcutApps.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "APP SHORTCUTS CREATOR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Text(
                                        text = "Select an app to pick its native shortcut (e.g. WhatsApp chats, Maps routes, Contacts):",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                items(shortcutApps, key = { it.packageName }) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                                            .clickable {
                                                pendingShortcutPkg = app.packageName
                                                val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                    setPackage(app.packageName)
                                                }
                                                shortcutPickerLauncher.launch(intent)
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (app.icon != null) {
                                            Image(
                                                bitmap = app.icon,
                                                contentDescription = app.label,
                                                modifier = Modifier.size(32.dp).clip(CircleShape)
                                            )
                                        } else {
                                            Icon(Icons.Default.Shortcut, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Pick from ${app.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = "Create shortcut with ${app.label}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }

                            // Built-in Instant Shortcuts
                            item {
                                Text(
                                    text = "FAST UTILITY SHORTCUTS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(instantShortcuts, key = { it.id }) { shortcut ->
                                val isSelected = currentAction == "SHORTCUT" && currentExtra == shortcut.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .clickable {
                                            onActionSelected("SHORTCUT", null, "Shortcut: ${shortcut.title}", shortcut.id)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = shortcut.icon,
                                            contentDescription = shortcut.title,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shortcut.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = shortcut.subtitle,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ActionRowItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
