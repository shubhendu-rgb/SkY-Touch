package com.example

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.audio.EqualizerController
import com.example.ui.EqualizerTab
import com.example.ui.theme.MyApplicationTheme

/**
 * Opened when a music or video app's own "Equalizer" button fires
 * [AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL]. It shows our equalizer straight away and
 * attaches it to the audio session of the app that opened it.
 */
class EqualizerPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqualizerController.init(this)
        handleCaller(intent)
        setResult(RESULT_OK)

        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent),
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent)
        )
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = if (prefs.contains("dark_mode")) prefs.getBoolean("dark_mode", false) else systemDark
            val dynamic = prefs.getBoolean("dynamic_color", true)

            MyApplicationTheme(darkTheme = dark, dynamicColor = dynamic) {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
                    Column(modifier = Modifier.fillMaxSize().padding(inner)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Text("Equalizer", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        EqualizerTab(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCaller(intent)
    }

    /** The calling app passes its audio session id, so we can attach to it right now. */
    private fun handleCaller(intent: Intent?) {
        if (intent?.action != AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        if (sessionId > 0) {
            EqualizerController.onSessionEvent(this, true, sessionId, pkg)
        }
    }
}
