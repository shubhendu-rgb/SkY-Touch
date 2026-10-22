package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.service.wallpaper.DefaultWallpaperManager
import kotlinx.coroutines.launch

/**
 * Switch that turns off every active / automatic wallpaper and puts the chosen default
 * Home and Lock wallpapers on the phone. Shown on both the Static and the Live screen.
 */
@Composable
fun PauseWallpapersCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var paused by remember { mutableStateOf(DefaultWallpaperManager.isPaused(context)) }
    var busy by remember { mutableStateOf(false) }
    var homeVersion by remember { mutableIntStateOf(0) }
    var lockVersion by remember { mutableIntStateOf(0) }

    // The Quick Settings tile can switch this too: re-read it whenever the screen comes back
    DisposableEffect(context) {
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.lifecycle.LifecycleOwner } as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                paused = DefaultWallpaperManager.isPaused(context)
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val homeFile = remember(homeVersion) { DefaultWallpaperManager.getDefaultFile(context, DefaultWallpaperManager.HOME) }
    val lockFile = remember(lockVersion) { DefaultWallpaperManager.getDefaultFile(context, DefaultWallpaperManager.LOCK) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // While paused, a newly chosen default goes on the phone right away
    fun reapplyIfPaused() {
        if (!paused) return
        busy = true
        scope.launch {
            val ok = DefaultWallpaperManager.pauseAllAndApplyDefaults(context)
            paused = ok
            busy = false
            toast(if (ok) "Default wallpaper applied" else "Could not apply the default wallpaper")
        }
    }

    val homePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (DefaultWallpaperManager.importDefault(context, uri, DefaultWallpaperManager.HOME)) {
                    homeVersion++
                    reapplyIfPaused()
                } else toast("Could not use that picture")
            }
        }
    }
    val lockPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (DefaultWallpaperManager.importDefault(context, uri, DefaultWallpaperManager.LOCK)) {
                    lockVersion++
                    reapplyIfPaused()
                } else toast("Could not use that picture")
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("card_pause_wallpapers"),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFFFB74D))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pause all wallpapers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        if (paused) "Everything is off: no automatic wallpaper changes. Switch off to let the changer work again."
                        else "Turns off live wallpaper, auto-change, tiles and gestures, and applies your default Home and Lock wallpapers.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = paused,
                    enabled = !busy,
                    onCheckedChange = { want ->
                        if (want) {
                            if (homeFile == null || lockFile == null) {
                                toast("Choose a default Home and Lock wallpaper first")
                            } else {
                                busy = true
                                scope.launch {
                                    val ok = DefaultWallpaperManager.pauseAllAndApplyDefaults(context)
                                    paused = ok
                                    busy = false
                                    toast(if (ok) "All wallpapers paused. Defaults applied." else "Could not apply the default wallpapers")
                                }
                            }
                        } else {
                            busy = true
                            scope.launch {
                                DefaultWallpaperManager.resumeAll(context)
                                paused = false
                                busy = false
                                toast("Wallpapers resumed")
                            }
                        }
                    },
                    modifier = Modifier.testTag("switch_pause_wallpapers")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DefaultPickTile(
                    label = "Default Home",
                    icon = { Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                    file = homeFile,
                    onChoose = {
                        homePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f).testTag("pick_default_home")
                )
                DefaultPickTile(
                    label = "Default Lock",
                    icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                    file = lockFile,
                    onChoose = {
                        lockPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f).testTag("pick_default_lock")
                )
            }
        }
    }
}

@Composable
private fun DefaultPickTile(
    label: String,
    icon: @Composable () -> Unit,
    file: java.io.File?,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onChoose,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    icon()
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(
                    if (file == null) "Tap to choose" else "Tap to change",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
    }
}
