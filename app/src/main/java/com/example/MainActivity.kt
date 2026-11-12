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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlin.math.roundToInt
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.CodeDetectionTab
import com.example.ui.TextAssistantTab
import com.example.ui.AutoClickTab
import com.example.ui.AutoClickSubScreen
import com.example.ui.DeveloperTab
import com.example.ui.RecordingsTab
import com.example.ui.ExcludedAppsTab
import com.example.ui.BackupRestoreTab
import com.example.util.AppShortcutHelper
import com.example.util.AppShortcutItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.AppDatabase
import com.example.data.GestureActionEntity
import com.example.data.NotchAreaGeometry
import com.example.data.NotchAreas
import com.example.data.NotchConfigEntity
import com.example.data.toAreaGeometry
import com.example.util.WebLinkHelper
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
    private val targetScreenState = mutableStateOf<ScreenType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetScreenState.value = parseScreenFromIntent(intent)
        db = AppDatabase.getDatabase(this)
        repository = NotchRepository(db)
        OtpNotificationHelper.createNotificationChannel(this)
        com.example.service.audio.EqualizerController.init(this)
        com.example.service.audio.EqualizerController.ensureService(this)
        NotchAreas.init(this)

        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

        // Warm the app/shortcut caches in the background so pickers and popups open instantly
        lifecycleScope.launch(Dispatchers.IO) {
            delay(1200L) // let the first frame draw first
            try { AppInfoCache.getOrLoadLauncherApps(applicationContext) } catch (_: Throwable) {}
            try { AppShortcutHelper.getOrLoadAllShortcuts(applicationContext) } catch (_: Throwable) {}
        }
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
                        onOpenSettings = { openAccessibilitySettings() },
                        targetScreen = targetScreenState.value,
                        onTargetScreenConsumed = { targetScreenState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = parseScreenFromIntent(intent)
        if (target != null) {
            targetScreenState.value = target
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
object SharedHttpClient {
    val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

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
    DASHBOARD, GESTURES, CALIBRATION, SIDE_DECK, CODE_DETECTION, TEXT_ASSISTANT, AUTOCLICK, ANALYTICS, DEVELOPER, RECORDINGS, VIDEO_EDITOR, NIGHT_SAVER, GAME_MODE, WIDGET_STUDIO, EXCLUDED_APPS, INSTRUCTIONS, BACKUP_RESTORE, WALLPAPER, LIVE_WALLPAPER, EQUALIZER
}

/**
 * Maps incoming intent (explicit OPEN_SCREEN extra or Quick Settings tile long-press)
 * to the exact feature screen in the app.
 */
fun parseScreenFromIntent(intent: Intent?): ScreenType? {
    if (intent == null) return null
    // 1. Direct explicit extra from internal notifications or shortcuts
    when (intent.getStringExtra("OPEN_SCREEN")) {
        "CODE_DETECTION" -> return ScreenType.CODE_DETECTION
        "EQUALIZER" -> return ScreenType.EQUALIZER
        "DYNAMIC_NOTCH" -> return ScreenType.GESTURES
        "GESTURES" -> return ScreenType.GESTURES
        "SIDE_DECK" -> return ScreenType.SIDE_DECK
        "TEXT_ASSISTANT" -> return ScreenType.TEXT_ASSISTANT
        "AUTOCLICK" -> return ScreenType.AUTOCLICK
        "WALLPAPER" -> return ScreenType.WALLPAPER
        "LIVE_WALLPAPER" -> return ScreenType.LIVE_WALLPAPER
        "RECORDINGS" -> return ScreenType.RECORDINGS
        "VIDEO_EDITOR" -> return ScreenType.VIDEO_EDITOR
        "WIDGET_STUDIO" -> return ScreenType.WIDGET_STUDIO
    }

    // 2. Quick Settings Tile Long-Click (android.service.quicksettings.action.QS_TILE_PREFERENCES)
    val comp: ComponentName? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
        }
    } catch (_: Throwable) {
        null
    } ?: intent.component

    val className = (comp?.className ?: intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME) ?: "").lowercase()

    return when {
        className.contains("autoclick") -> ScreenType.AUTOCLICK
        className.contains("equalizer") -> ScreenType.EQUALIZER
        className.contains("livewallpaper") -> ScreenType.LIVE_WALLPAPER
        className.contains("wallpaper") -> ScreenType.WALLPAPER
        className.contains("notch") -> ScreenType.GESTURES
        className.contains("sidedeck") -> ScreenType.SIDE_DECK
        className.contains("stoprecording") || className.contains("record") -> ScreenType.RECORDINGS
        className.contains("textassistant") || className.contains("assistant") -> ScreenType.TEXT_ASSISTANT
        className.contains("otp") || className.contains("code") -> ScreenType.CODE_DETECTION
        intent.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES" -> ScreenType.DASHBOARD
        else -> null
    }
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
    onOpenSettings: () -> Unit,
    targetScreen: ScreenType? = null,
    onTargetScreenConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val testMessage by viewModel.testGestureMessage.collectAsStateWithLifecycle()

    var isServiceRunning by remember { mutableStateOf(false) }
    var isPermissionEnabled by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.example.util.UpdateInfo?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted! Camera options are now enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required for Spy Cam feature.", Toast.LENGTH_LONG).show()
        }
    }

    var pendingUpdate by remember { mutableStateOf<com.example.util.UpdateInfo?>(null) }

    val updateNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Post the update notification right after the user grants permission
        if (granted) pendingUpdate?.let {
            com.example.util.UpdateChecker.showUpdateNotification(context, it)
        }
    }

    // Periodically check service status
    LaunchedEffect(Unit) {
        launch {
            val info = com.example.util.UpdateChecker.checkForUpdates(context)
            if (info != null && info.isUpdateAvailable) {
                updateInfo = info
                pendingUpdate = info
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    updateNotificationPermissionLauncher.launch(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    com.example.util.UpdateChecker.showUpdateNotification(context, info)
                }
            }
        }
        
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val justRestored = prefs.getBoolean("just_restored_backup", false)
        if (justRestored) {
            prefs.edit().putBoolean("just_restored_backup", false).apply()
            val initiallyEnabled = isAccessibilityServiceEnabled(context, NotchAccessibilityService::class.java)
            if (!initiallyEnabled) {
                onOpenSettings()
            }
        }
        
        while (true) {
            val running = NotchAccessibilityService.isRunning
            val perm = withContext(Dispatchers.IO) {
                isAccessibilityServiceEnabled(context, NotchAccessibilityService::class.java)
            }
            if (isServiceRunning != running) isServiceRunning = running
            if (isPermissionEnabled != perm) isPermissionEnabled = perm
            delay(2000)
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
        targetScreen ?: parseScreenFromIntent((context as? ComponentActivity)?.intent) ?: ScreenType.DASHBOARD
    }
    // rememberSaveable: Android re-creates the activity after the phone is locked / unlocked (and whenever the
    // system theme colors change). Plain remember lost the open screen every time, so you landed on the Dashboard.
    var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }

    LaunchedEffect(targetScreen) {
        if (targetScreen != null) {
            currentScreen = targetScreen
            onTargetScreenConsumed()
        }
    }
    var autoClickSubScreen by rememberSaveable { mutableStateOf<AutoClickSubScreen?>(null) }
    var editingActionGesture by rememberSaveable { mutableStateOf<String?>(null) }
    // Notch gesture areas: extra areas 2 and 3 (area 1 is the original notch) and the one being edited
    val extraNotchAreas by NotchAreas.extraAreas.collectAsStateWithLifecycle()
    var selectedGestureArea by rememberSaveable { mutableStateOf(1) }
    // Permissions overview (shield icon on the Dashboard)
    val appPermissions = com.example.ui.rememberAppPermissions()
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    val saveNotchArea: (NotchAreaGeometry, Boolean) -> Unit = { area, immediate ->
        viewModel.saveNotchArea(context, area, immediate)
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentScreen) {
        if (currentScreen != ScreenType.AUTOCLICK) {
            autoClickSubScreen = null
        }
    }

    var githubName by remember { mutableStateOf("Details & About") }
    var githubAvatar by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url("https://api.github.com/users/shubhendu-rgb").build()
                val response = SharedHttpClient.client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(response.body?.string() ?: "")
                    val name = json.optString("name", "Shubhendu Kumar Sahoo")
                    val avatarUrl = json.optString("avatar_url", "")
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        githubName = name
                    }

                    if (avatarUrl.isNotEmpty()) {
                        val imgRequest = okhttp3.Request.Builder().url(avatarUrl).build()
                        val imgResponse = SharedHttpClient.client.newCall(imgRequest).execute()
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
    BackHandler(enabled = drawerState.isOpen || editingActionGesture != null || (currentScreen == ScreenType.AUTOCLICK && autoClickSubScreen != null) || currentScreen != ScreenType.DASHBOARD) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (editingActionGesture != null) {
            editingActionGesture = null
        } else if (currentScreen == ScreenType.AUTOCLICK && autoClickSubScreen != null) {
            autoClickSubScreen = null
        } else if (currentScreen == ScreenType.LIVE_WALLPAPER) {
            currentScreen = ScreenType.WALLPAPER
        } else if (currentScreen != ScreenType.DASHBOARD) {
            currentScreen = ScreenType.DASHBOARD
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // The drawer is only reachable from the Dashboard. Leaving its swipe detector on every
        // other screen makes taps and sliders compete with it for touch events.
        gesturesEnabled = currentScreen == ScreenType.DASHBOARD || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("SkY Touch", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "v8.6",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
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
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                        label = { Text("Video Editor") },
                        selected = currentScreen == ScreenType.VIDEO_EDITOR,
                        onClick = {
                            currentScreen = ScreenType.VIDEO_EDITOR
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.BatterySaver, contentDescription = null) },
                        label = { Text("Night Battery Saver") },
                        selected = currentScreen == ScreenType.NIGHT_SAVER,
                        onClick = {
                            currentScreen = ScreenType.NIGHT_SAVER
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                        label = { Text("Game Mode") },
                        selected = currentScreen == ScreenType.GAME_MODE,
                        onClick = {
                            currentScreen = ScreenType.GAME_MODE
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                        label = { Text("Widget Studio") },
                        selected = currentScreen == ScreenType.WIDGET_STUDIO,
                        onClick = {
                            currentScreen = ScreenType.WIDGET_STUDIO
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

        val showMainTopBar = !(currentScreen == ScreenType.AUTOCLICK && autoClickSubScreen != null) &&
            currentScreen != ScreenType.WALLPAPER &&
            currentScreen != ScreenType.LIVE_WALLPAPER

        Scaffold(
            topBar = {
                if (showMainTopBar) {
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
                                    ScreenType.AUTOCLICK -> "AutoClick Studio"
                                    ScreenType.ANALYTICS -> "Analytics"
                                    ScreenType.DEVELOPER -> "About Developer"
                                    ScreenType.RECORDINGS -> "Video Recordings"
                                    ScreenType.VIDEO_EDITOR -> "Video Editor"
                                    ScreenType.NIGHT_SAVER -> "Night Battery Saver"
                                    ScreenType.GAME_MODE -> "Game Mode"
                                    ScreenType.WIDGET_STUDIO -> "Widget Studio"
                                    ScreenType.EXCLUDED_APPS -> "Excluded Apps"
                                    ScreenType.INSTRUCTIONS -> "Instructions"
                                    ScreenType.BACKUP_RESTORE -> "Backup & Restore"
                                    ScreenType.WALLPAPER -> "Wallpaper Changer"
                                    ScreenType.LIVE_WALLPAPER -> "Live Wallpaper Studio"
                                    ScreenType.EQUALIZER -> "Equalizer"
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
                                IconButton(onClick = {
                                    if (currentScreen == ScreenType.LIVE_WALLPAPER) {
                                        currentScreen = ScreenType.WALLPAPER
                                    } else {
                                        currentScreen = ScreenType.DASHBOARD
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onSurfaceColor)
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == ScreenType.DASHBOARD) {
                                com.example.ui.PermissionsIconButton(
                                    permissions = appPermissions,
                                    tint = onSurfaceColor,
                                    onClick = { showPermissions = true }
                                )
                            }
                            if (currentScreen == ScreenType.GESTURES) {
                                val canAddArea = extraNotchAreas.size + 1 < NotchAreas.MAX_AREAS
                                IconButton(
                                    onClick = {
                                        val added = viewModel.addNotchArea(context)
                                        if (added != null) {
                                            selectedGestureArea = added.id
                                            Toast.makeText(context, "Notch ${added.id} added", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "You can add up to ${NotchAreas.MAX_AREAS} notch areas",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("add_notch_area")
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add notch gesture area",
                                        tint = if (canAddArea) onSurfaceColor else onSurfaceColor.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            containerColor = Color.Transparent,
            modifier = modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) { paddingValues ->
            val effectivePadding = if (!showMainTopBar) PaddingValues(0.dp) else paddingValues
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(effectivePadding)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (currentScreen == ScreenType.AUTOCLICK ||
                                currentScreen == ScreenType.TEXT_ASSISTANT ||
                                currentScreen == ScreenType.WALLPAPER ||
                                currentScreen == ScreenType.LIVE_WALLPAPER
                            ) 0.dp else 16.dp
                        )
                ) {
                    // Plain switch instead of AnimatedContent: the cross-fade kept the old screen and the
                    // new (often heavy) screen composed at the same time, which delayed every open.
                    Box(modifier = Modifier.fillMaxSize()) {
                        val targetScreen = currentScreen
                        when (targetScreen) {
                            ScreenType.DASHBOARD -> DashboardScreen(
                                isServiceRunning = isServiceRunning,
                                isPermissionEnabled = isPermissionEnabled,
                                onOpenSettings = onOpenSettings,
                                onNavigate = { currentScreen = it }
                            )
                            ScreenType.GESTURES -> GesturesTab(
                                actions = uiState.actions,
                                config = uiState.config,
                                extraAreas = extraNotchAreas,
                                selectedAreaId = selectedGestureArea,
                                onSelectArea = { selectedGestureArea = it },
                                onAreaSave = saveNotchArea,
                                onRemoveArea = { id ->
                                    viewModel.removeNotchArea(context, id)
                                    selectedGestureArea = 1
                                },
                                onConfigureGesture = { editingActionGesture = it }
                            )
                            ScreenType.CALIBRATION -> CalibrationTab(
                                config = uiState.config,
                                extraAreas = extraNotchAreas,
                                onConfigChange = { viewModel.updateConfig(it) },
                                onAreaSave = saveNotchArea
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
                            ScreenType.AUTOCLICK -> AutoClickTab(
                                viewModel = viewModel,
                                currentSubScreen = autoClickSubScreen,
                                onSubScreenChange = { autoClickSubScreen = it }
                            )
                            ScreenType.ANALYTICS -> AnalyticsTab(
                                stats = uiState.stats,
                                onResetStats = { viewModel.resetStats() },
                                onSimulateGesture = { viewModel.triggerTestGesture(it) },
                                testMessage = testMessage
                            )
                            ScreenType.DEVELOPER -> DeveloperTab()
                            ScreenType.RECORDINGS -> RecordingsTab()
                            ScreenType.VIDEO_EDITOR -> com.example.ui.VideoEditorTab()
                            ScreenType.NIGHT_SAVER -> com.example.ui.NightBatterySaverTab()
                            ScreenType.GAME_MODE -> com.example.ui.GameModeTab()
                            ScreenType.WIDGET_STUDIO -> com.example.ui.WidgetStudioTab()
                            ScreenType.EXCLUDED_APPS -> ExcludedAppsTab(
                                notchConfig = uiState.config,
                                onConfigChange = { viewModel.updateConfig(it) }
                            )
                            ScreenType.INSTRUCTIONS -> com.example.ui.InstructionsTab()
                            ScreenType.BACKUP_RESTORE -> BackupRestoreTab(
                                repository = repository
                            )
                            ScreenType.WALLPAPER -> com.example.ui.WallpaperTab(
                                onBack = { currentScreen = ScreenType.DASHBOARD }
                            )
                            ScreenType.LIVE_WALLPAPER -> com.example.ui.LiveWallpaperTab(
                                onBack = { currentScreen = ScreenType.DASHBOARD }
                            )
                            ScreenType.EQUALIZER -> com.example.ui.EqualizerTab()
                        }
                    }
                }
            }
        }
    }

    if (showPermissions) {
        com.example.ui.PermissionsSheet(
            permissions = appPermissions,
            onDismiss = { showPermissions = false }
        )
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

    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update Available", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("A new version (${updateInfo!!.version}) of the app is available. Would you like to download it from GitHub?") },
            confirmButton = {
                Button(onClick = {
                    val url = updateInfo!!.releaseUrl
                    updateInfo = null
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "No browser found to open the download page", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { updateInfo = null }) {
                    Text("Later")
                }
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
            val ignoring = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                } else {
                    true
                }
            }
            if (isIgnoringBattery != ignoring) isIgnoringBattery = ignoring
            delay(3000)
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
                    description = "Tap, swipe & hold actions on up to 3 notch areas around your front camera.",
                    icon = Icons.Filled.TouchApp,
                    onClick = { onNavigate(ScreenType.GESTURES) }
                )
            }
            item {
                DashboardCard(
                    title = "Notch Position",
                    description = "Adjust the position and size of each notch gesture area.",
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
                    title = "AutoClick Studio",
                    description = "Automated screen taps, gestures, reticle skins, and floating pill controls.",
                    icon = Icons.Filled.TouchApp,
                    onClick = { onNavigate(ScreenType.AUTOCLICK) }
                )
            }
            item {
                DashboardCard(
                    title = "Wallpaper Changer",
                    description = "Change wallpaper automatically from images or folder with blur, presets & live preview.",
                    icon = Icons.Filled.Wallpaper,
                    onClick = { onNavigate(ScreenType.WALLPAPER) }
                )
            }
            item {
                DashboardCard(
                    title = "Equalizer",
                    description = "Global audio equalizer with presets, bass boost, surround and loudness for all your music & video apps.",
                    icon = Icons.Filled.Equalizer,
                    onClick = { onNavigate(ScreenType.EQUALIZER) }
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val iconBackgroundBrush = remember(primaryColor, tertiaryColor) {
        Brush.linearGradient(listOf(primaryColor, tertiaryColor))
    }

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
                    .background(iconBackgroundBrush),
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
    config: NotchConfigEntity,
    extraAreas: List<NotchAreaGeometry>,
    selectedAreaId: Int,
    onSelectArea: (Int) -> Unit,
    onAreaSave: (NotchAreaGeometry, Boolean) -> Unit,
    onRemoveArea: (Int) -> Unit,
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

    val allAreas = remember(config, extraAreas) { listOf(config.toAreaGeometry()) + extraAreas }
    val activeAreaId = if (allAreas.any { it.id == selectedAreaId }) selectedAreaId else 1
    var showGeometry by rememberSaveable { mutableStateOf(false) }
    // While the position & size section is open, mark the real area positions on the screen
    com.example.ui.NotchLivePreviewEffect(enabled = showGeometry, selectedAreaId = activeAreaId)

    // One row per gesture of the selected notch area (missing rows count as "no action")
    val areaActions = remember(actions, activeAreaId) {
        NotchAreas.GESTURE_NAMES.map { gesture ->
            val key = NotchAreas.actionKey(activeAreaId, gesture)
            actions.firstOrNull { it.gestureName == key }
                ?: GestureActionEntity(gestureName = key, actionType = "NONE", label = "Disabled (No Action)")
        }
    }

    val context = LocalContext.current
    val hasBrightnessAction = remember(actions) {
        actions.any { it.actionType in listOf("BRIGHTNESS_UP", "BRIGHTNESS_DOWN", "CUSTOM_BRIGHTNESS") }
    }
    val isWriteSettingsOk = remember(context) { isWriteSettingsGranted(context) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        modifier = Modifier.fillMaxSize().testTag("gestures_list")
    ) {
        if (hasBrightnessAction && !isWriteSettingsOk) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Brightness Permission Needed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Allow 'Modify system settings' for brightness gestures.",
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
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Notch area selector. The + icon in the top bar adds a new area (maximum 3).
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Notch areas (${allAreas.size}/${NotchAreas.MAX_AREAS})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (allAreas.size < NotchAreas.MAX_AREAS) {
                        "Each area has its own gestures, position and size. Tap + at the top to add another area."
                    } else {
                        "Each area has its own gestures, position and size. Maximum areas reached."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                com.example.ui.NotchAreaChipsRow(
                    areaIds = allAreas.map { it.id },
                    selectedId = activeAreaId,
                    onSelect = onSelectArea
                )
            }
        }

        // Position & size of the selected area (collapsible)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGeometry = !showGeometry }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Position & Size - Notch $activeAreaId",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val g = allAreas.first { it.id == activeAreaId }
                            Text(
                                text = "${g.widthDp} x ${g.heightDp} dp  \u2022  X ${g.xOffsetDp}, Y ${g.yOffsetDp}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (showGeometry) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showGeometry) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showGeometry) {
                        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                            com.example.ui.NotchAreaEditor(
                                allAreas = allAreas,
                                selectedId = activeAreaId,
                                showPreview = false,
                                onChange = { onAreaSave(it, false) },
                                onCommit = { onAreaSave(it, true) }
                            )
                            if (activeAreaId > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onRemoveArea(activeAreaId) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Remove Notch $activeAreaId")
                                }
                            }
                        }
                    }
                }
            }
        }

        items(areaActions, key = { it.gestureName }) { gestureAction ->
            val gestureKey = gestureAction.gestureName
            val baseGesture = NotchAreas.gestureOfKey(gestureKey)
            val icon = gestureIcons[baseGesture] ?: Icons.Default.Adjust
            val prettyName = gestureLabels[baseGesture] ?: baseGesture
            val isConfigured = gestureAction.actionType != "NONE"
            val actionDesc = getActionDescription(gestureAction)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfigureGesture(gestureKey) }
                    .testTag("gesture_item_$gestureKey"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConfigured) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = prettyName,
                            tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prettyName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = actionDesc,
                            color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit Action",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

fun getActionDescription(entity: GestureActionEntity): String {
    val isHold = NotchAreas.gestureOfKey(entity.gestureName) in listOf("LONG_PRESS", "SWIPE_LEFT_AND_HOLD", "SWIPE_RIGHT_AND_HOLD")
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
        "WALLPAPER_NEXT" -> "Next Wallpaper"
        "WALLPAPER_PREVIOUS" -> "Previous Wallpaper"
        "WALLPAPER_RANDOM" -> "Random Wallpaper"
        "WALLPAPER_HOME_NEXT" -> "Next Home Wallpaper"
        "WALLPAPER_LOCK_NEXT" -> "Next Lock Screen Wallpaper"
        "WALLPAPER_IMAGES_NEXT" -> "Next Wallpaper (Selected Images)"
        "WALLPAPER_FOLDER_NEXT" -> "Next Wallpaper (Folder)"
        "OPEN_EDITOR" -> "Open Widget Editor"
        "OPEN_URL" -> "Open link: ${WebLinkHelper.displayName(entity.extraValue)}"
        "TEMP_LOCK_WALLPAPER" -> "Lock wallpaper for 2 minutes"
        "OPEN_NOTES" -> "Open quick notes"
        "TOGGLE_NOTCH" -> "Turn notch gestures on / off"
        "TOGGLE_SIDE_DECK" -> "Turn Side Deck on / off"
        "TOGGLE_OTP" -> "Turn Code Detector on / off"
        "TOGGLE_TEXT_ASSISTANT" -> "Turn Text Assistant on / off"
        "TOGGLE_EQUALIZER" -> "Turn Equalizer on / off"
        "WALLPAPER_CHANGER_TOGGLE" -> "Turn Wallpaper Changer on / off"
        "LIVE_WALLPAPER_NEXT" -> "Next Live Wallpaper"
        "STOP_SPY_CAM" -> "Stop silent recording"
        "AUTOCLICK_MENU_TOGGLE" -> "Show / hide Auto Click menu"
        "AUTOCLICK_START" -> "Start Auto Click"
        "AUTOCLICK_STOP" -> "Stop Auto Click"
        "AUTOCLICK_TOGGLE" -> "Start / stop Auto Click"
        "QUICK_DIAL" -> entity.label.ifBlank { "Quick dial" }
        else -> "Disabled (No Action)"
    }
}

@Composable
fun CalibrationTab(
    config: NotchConfigEntity,
    extraAreas: List<NotchAreaGeometry>,
    onConfigChange: (NotchConfigEntity) -> Unit,
    onAreaSave: (NotchAreaGeometry, Boolean) -> Unit
) {
    com.example.ui.NotchPositionScreen(
        config = config,
        extraAreas = extraAreas,
        onConfigChange = onConfigChange,
        onAreaSave = onAreaSave
    )
}

@Composable
private fun LegacyCalibrationTabUnused(
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
                        Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = "battery", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
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
                        .offset { IntOffset(mockupX.roundToPx(), mockupY.roundToPx()) }
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

    val visibleStats = remember(stats) {
        stats
            .filter { it.gestureName != "SWIPE_DOWN" }
            .sortedBy { val idx = gestureOrder.indexOf(it.gestureName); if (idx != -1) idx else 99 }
    }
    val totalTriggers = remember(visibleStats) { visibleStats.sumOf { it.count } }

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
                            key(stat.gestureName) {
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
                    var isDraggingTarget by remember { mutableStateOf(false) }

                    val animatedDragOffset by animateFloatAsState(
                        targetValue = if (isDraggingTarget) currentDragDeltaX.coerceIn(-50f, 50f) else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "simulated_drag"
                    )
                    val animatedTargetScale by animateFloatAsState(
                        targetValue = if (isDraggingTarget) 1.05f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "simulated_scale"
                    )

                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(animatedDragOffset.roundToInt(), 0) }
                            .graphicsLayer {
                                scaleX = animatedTargetScale
                                scaleY = animatedTargetScale
                            }
                            .size(width = 200.dp, height = 52.dp)
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
                                        isDraggingTarget = true
                                        dragStartX = offset.x
                                        dragStartTime = System.currentTimeMillis()
                                        isSwipeHoldDetected = false
                                        currentDragDeltaX = 0f
                                    },
                                    onDragEnd = {
                                        isDraggingTarget = false
                                        if (!isSwipeHoldDetected) {
                                            if (currentDragDeltaX > 25f) {
                                                feedbackText = "Swipe Right!"
                                                onSimulateGesture("SWIPE_RIGHT")
                                            } else if (currentDragDeltaX < -25f) {
                                                feedbackText = "Swipe Left!"
                                                onSimulateGesture("SWIPE_LEFT")
                                            }
                                        }
                                        currentDragDeltaX = 0f
                                    },
                                    onDragCancel = {
                                        isDraggingTarget = false
                                        currentDragDeltaX = 0f
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (currentDragDeltaX < -10f) 1f else 0.35f
                                ),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = feedbackText.uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (currentDragDeltaX > 10f) 1f else 0.35f
                                ),
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
    val className: String? = null,
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
                val cls = resolveInfo.activityInfo.name
                val iconDrawable = resolveInfo.loadIcon(pm)
                val bitmap = iconDrawable.toBitmapOrNull()
                AppEntry(label = label, packageName = pkg, className = cls, icon = bitmap?.asImageBitmap())
            } catch (e: Throwable) {
                null
            }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
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
                val cls = resolveInfo.activityInfo.name
                val iconDrawable = resolveInfo.loadIcon(pm)
                val bitmap = iconDrawable.toBitmapOrNull()
                AppEntry(label = label, packageName = pkg, className = cls, icon = bitmap?.asImageBitmap())
            } catch (e: Throwable) {
                null
            }
        }.distinctBy { "${it.packageName}:${it.className}" }.sortedBy { it.label.lowercase() }
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
    } catch (e: Throwable) {
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
    // gestureName is the stored key ("SINGLE_TAP", or "A2_SINGLE_TAP" for notch area 2); the plain gesture drives the texts
    val baseGesture = NotchAreas.gestureOfKey(gestureName)
    val areaId = NotchAreas.areaOfKey(gestureName)
    // The picker opens on a single list of categories; a category (or a form such as Web Link) opens on click
    var screen by remember { mutableStateOf("HOME") }
    val selectedCategory = when (screen) {
        "APPS" -> 1
        "SHORTCUTS" -> 2
        "WEB" -> 3
        "DIAL", "LOCKWP" -> 4
        else -> 0
    }
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
    val prettyGesture = (gestureLabels[baseGesture] ?: baseGesture) + if (areaId > 1) " (Notch $areaId)" else ""

    // Query installed launcher apps (cached in-memory, loads only when Open App category is visited)
    val installedApps by produceState<List<AppEntry>>(initialValue = AppInfoCache.cachedLauncherApps ?: emptyList(), selectedCategory) {
        if (selectedCategory == 1 && value.isEmpty()) {
            withContext(Dispatchers.IO) {
                value = AppInfoCache.getOrLoadLauncherApps(context)
            }
        }
    }

    // Query all shortcuts (App shortcuts: YouTube Shorts, Subscriptions, WhatsApp chats, Chrome, etc., custom creators, system)
    var shortcutSearchQuery by remember { mutableStateOf("") }

    val allShortcuts by produceState<List<AppShortcutItem>>(
        initialValue = AppShortcutHelper.cachedAppShortcuts ?: emptyList(),
        selectedCategory
    ) {
        if (selectedCategory == 2) {
            withContext(Dispatchers.IO) {
                value = AppShortcutHelper.getOrLoadAllShortcuts(context)
            }
        }
    }

    // Shortcut creator launcher
    var pendingShortcutPkg by remember { mutableStateOf<String?>(null) }
    var pendingShortcutAppName by remember { mutableStateOf<String?>(null) }
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
                val prefix = pendingShortcutAppName ?: "Shortcut"
                onActionSelected("SHORTCUT", pendingShortcutPkg, "Shortcut: $prefix - $shortcutName", uri)
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
        Triple("VOLUME_DOWN", null, "Lower Music Volume"),
        Triple("OPEN_EDITOR", null, "Open Widget Editor")
    )

    val categories = listOf("System", "Open App", "Shortcuts", "Web Link", "More")

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
                    .heightIn(max = 540.dp)
            ) {
                if (screen != "HOME") {
                    PickerScreenHeader(
                        title = pickerScreenTitle(screen),
                        onBack = { screen = "HOME" }
                    )
                }

                // Category Contents
                androidx.activity.compose.BackHandler(enabled = screen != "HOME") { screen = "HOME" }
                when (selectedCategory) {
                    0 -> {
                        val isHold = baseGesture in listOf("LONG_PRESS", "SWIPE_LEFT_AND_HOLD", "SWIPE_RIGHT_AND_HOLD")
                        if (screen == "HOME") {
                            PickerHomeList(
                                modifier = Modifier.weight(1f),
                                currentAction = currentAction,
                                onOpen = { screen = it },
                                onPick = { type, label -> onActionSelected(type, null, label, null) }
                            )
                        } else {
                            PickerOptionsGrid(
                                modifier = Modifier.weight(1f),
                                screen = screen,
                                isHold = isHold,
                                currentAction = currentAction,
                                customBrightness = customBrightnessSlider,
                                onCustomBrightnessChange = { customBrightnessSlider = it },
                                isWriteGranted = remember(context) { isWriteSettingsGranted(context) },
                                onGrantWrite = {
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
                                onOpen = { screen = it },
                                onPick = { type, label, extra -> onActionSelected(type, null, label, extra) }
                            )
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
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    items(filteredApps, key = { it.packageName }) { app ->
                                        val isSelected = currentAction == "LAUNCH_APP" && currentPackage == app.packageName
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    onActionSelected("LAUNCH_APP", app.packageName, "Open ${app.label}", null)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (app.icon != null) {
                                                Image(
                                                    bitmap = app.icon,
                                                    contentDescription = app.label,
                                                    modifier = Modifier.size(30.dp).clip(CircleShape)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.label,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Shortcuts Tab: shows ALL apps app shortcuts (YouTube Shorts, Subscriptions, WhatsApp chats, etc.)
                        val filteredShortcuts = remember(allShortcuts, shortcutSearchQuery) {
                            if (shortcutSearchQuery.isBlank()) {
                                allShortcuts
                            } else {
                                allShortcuts.filter { item ->
                                    item.title.contains(shortcutSearchQuery, ignoreCase = true) ||
                                    item.appName.contains(shortcutSearchQuery, ignoreCase = true) ||
                                    (item.subtitle?.contains(shortcutSearchQuery, ignoreCase = true) == true)
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // Search bar
                            OutlinedTextField(
                                value = shortcutSearchQuery,
                                onValueChange = { shortcutSearchQuery = it },
                                placeholder = { Text("Search shortcuts (e.g. Shorts, WhatsApp, Alarms)...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    if (shortcutSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { shortcutSearchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )

                            if (filteredShortcuts.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (shortcutSearchQuery.isBlank()) "No shortcuts available" else "No matching shortcuts found",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val grouped = remember(filteredShortcuts) {
                                    filteredShortcuts.groupBy { it.appName }
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    grouped.forEach { (appName, groupItems) ->
                                        item(key = "header_$appName") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                            ) {
                                                val headerIcon = groupItems.firstOrNull { it.iconBitmap != null }?.iconBitmap
                                                if (headerIcon != null) {
                                                    Image(
                                                        bitmap = headerIcon,
                                                        contentDescription = appName,
                                                        modifier = Modifier.size(14.dp).clip(CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = appName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "(${groupItems.size})",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        items(groupItems, key = { it.id }) { shortcut ->
                                            val isSelected = currentAction == "SHORTCUT" && (currentExtra == shortcut.intentUri || currentExtra == shortcut.id)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        if (shortcut.isCreator) {
                                                            pendingShortcutPkg = shortcut.packageName
                                                            pendingShortcutAppName = shortcut.appName
                                                            val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                                if (shortcut.creatorClassName != null) {
                                                                    setClassName(shortcut.packageName, shortcut.creatorClassName)
                                                                } else {
                                                                    setPackage(shortcut.packageName)
                                                                }
                                                            }
                                                            try {
                                                                shortcutPickerLauncher.launch(intent)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "Cannot open shortcut picker for ${shortcut.appName}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            onActionSelected(
                                                                "SHORTCUT",
                                                                shortcut.packageName,
                                                                "Shortcut: ${shortcut.appName} - ${shortcut.title}",
                                                                shortcut.intentUri
                                                            )
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (shortcut.iconBitmap != null) {
                                                        Image(
                                                            bitmap = shortcut.iconBitmap,
                                                            contentDescription = shortcut.title,
                                                            modifier = Modifier.size(20.dp).clip(CircleShape)
                                                        )
                                                    } else if (shortcut.iconVector != null) {
                                                        Icon(
                                                            imageVector = shortcut.iconVector,
                                                            contentDescription = shortcut.title,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Shortcut,
                                                            contentDescription = shortcut.title,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = shortcut.title,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!shortcut.subtitle.isNullOrBlank()) {
                                                        Text(
                                                            text = shortcut.subtitle,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else if (shortcut.isCreator) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Web Link: opens a web page in the default browser
                        var urlText by remember {
                            mutableStateOf(if (currentAction == "OPEN_URL") currentExtra.orEmpty() else "")
                        }
                        val normalizedUrl = WebLinkHelper.normalize(urlText)
                        val showError = urlText.isNotBlank() && normalizedUrl == null

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Open a web link",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "This gesture opens the link below in your phone's default browser for quick access to a website.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Web link") },
                                placeholder = { Text("example.com or https://example.com", fontSize = 13.sp) },
                                singleLine = true,
                                isError = showError,
                                supportingText = {
                                    if (showError) Text("Enter a valid web address (http or https)")
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                leadingIcon = {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            Button(
                                onClick = {
                                    val url = normalizedUrl
                                    if (url != null) {
                                        onActionSelected(
                                            "OPEN_URL",
                                            null,
                                            "Open link: ${WebLinkHelper.displayName(url)}",
                                            url
                                        )
                                    }
                                },
                                enabled = normalizedUrl != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Web Link", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!WebLinkHelper.open(context, normalizedUrl)) {
                                        Toast.makeText(context, "Could not open this link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = normalizedUrl != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Test Link")
                            }
                        }
                    }
                    4 -> {
                        // More: lock wallpaper for 2 minutes, quick notes, quick dial
                        val scope = rememberCoroutineScope()
                        var pickedPicture by remember {
                            mutableStateOf(if (currentAction == "TEMP_LOCK_WALLPAPER") currentExtra else null)
                        }
                        val pictureBitmap = remember(pickedPicture) {
                            pickedPicture?.let { path ->
                                try {
                                    android.graphics.BitmapFactory.decodeFile(
                                        path,
                                        android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                                    )?.asImageBitmap()
                                } catch (_: Exception) { null }
                            }
                        }
                        val pictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                            if (uri != null) {
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) {
                                        com.example.service.TempLockWallpaper.importPicture(context, uri)
                                    }
                                    if (path != null) {
                                        // Drop a previously picked (unsaved) picture again
                                        if (pickedPicture != null && pickedPicture != currentExtra) {
                                            com.example.service.TempLockWallpaper.deletePicture(context, pickedPicture)
                                        }
                                        pickedPicture = path
                                    } else {
                                        Toast.makeText(context, "Could not use this picture", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        var dialNumber by remember {
                            mutableStateOf(if (currentAction == "QUICK_DIAL") currentExtra else null)
                        }
                        var dialName by remember { mutableStateOf<String?>(null) }
                        val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                            val data = result.data?.data
                            if (result.resultCode == Activity.RESULT_OK && data != null) {
                                try {
                                    context.contentResolver.query(
                                        data,
                                        arrayOf(
                                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                                            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                                        ),
                                        null, null, null
                                    )?.use { c ->
                                        if (c.moveToFirst()) {
                                            dialNumber = c.getString(0)
                                            dialName = c.getString(1)
                                        }
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Could not read this contact", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        val launchContactPicker = {
                            contactLauncher.launch(
                                Intent(
                                    Intent.ACTION_PICK,
                                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                            )
                        }
                        val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
                            launchContactPicker()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Temporary lock wallpaper
                            if (screen == "LOCKWP") Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Lock wallpaper for 2 minutes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Shows the picture you choose on the lock screen for 2 minutes, then brings your old lock screen back.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (pictureBitmap != null) {
                                        Image(
                                            bitmap = pictureBitmap,
                                            contentDescription = "Selected lock wallpaper",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { pictureLauncher.launch("image/*") },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text(if (pickedPicture == null) "Choose picture" else "Change picture") }
                                        Button(
                                            onClick = {
                                                val path = pickedPicture
                                                if (path != null) {
                                                    if (currentAction == "TEMP_LOCK_WALLPAPER" && currentExtra != path) {
                                                        com.example.service.TempLockWallpaper.deletePicture(context, currentExtra)
                                                    }
                                                    onActionSelected("TEMP_LOCK_WALLPAPER", null, "Lock wallpaper for 2 min", path)
                                                }
                                            },
                                            enabled = pickedPicture != null,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Use this") }
                                    }
                                }
                            }

                            // 3. Quick dial
                            if (screen == "DIAL") Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Quick Dial", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Calls the contact you choose. Allow the phone permission so it can call directly; otherwise the dialer opens with the number ready.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!dialNumber.isNullOrBlank()) {
                                        Text(
                                            text = (dialName ?: "Selected number") + "  " + dialNumber,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val granted = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
                                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (granted) launchContactPicker()
                                                else callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text(if (dialNumber.isNullOrBlank()) "Choose contact" else "Change contact") }
                                        Button(
                                            onClick = {
                                                val number = dialNumber?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                                if (!number.isNullOrBlank()) {
                                                    onActionSelected("QUICK_DIAL", null, "Call ${dialName ?: number}", number)
                                                }
                                            },
                                            enabled = !dialNumber.isNullOrBlank(),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Use this") }
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
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
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
