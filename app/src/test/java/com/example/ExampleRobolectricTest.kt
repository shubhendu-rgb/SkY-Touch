package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.NotchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SkY Touch", appName)
    }

    @Test
    fun `verify text assistant default snippets initialized`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repository = NotchRepository(db)

        repository.initializeDefaultsIfNeeded()

        val snippets = repository.getDirectSnippets()
        assertTrue(snippets.isNotEmpty())

        val addrSnippet = snippets.find { it.triggerKeyword == "addr" }
        assertTrue(addrSnippet != null)
        assertEquals(false, addrSnippet?.isAiAction)

        val fixSnippet = snippets.find { it.triggerKeyword == "fix" }
        assertTrue(fixSnippet != null)
        assertEquals(true, fixSnippet?.isAiAction)

        val config = repository.getDirectTextAssistantConfig()
        assertEquals("?", config.triggerPrefix)
        assertEquals(true, config.enabled)
    }

    @Test
    fun `verify quick settings tile long click opens corresponding app functions`() {
        val testTiles = listOf(
            "com.example.service.tiles.AutoClickTileService" to ScreenType.AUTOCLICK,
            "com.example.service.tiles.EqualizerTileService" to ScreenType.EQUALIZER,
            "com.example.service.tiles.LiveWallpaperTileService" to ScreenType.LIVE_WALLPAPER,
            "com.example.service.tiles.WallpaperFolderTileService" to ScreenType.WALLPAPER,
            "com.example.service.tiles.WallpaperImagesTileService" to ScreenType.WALLPAPER,
            "com.example.service.tiles.WallpaperChangerToggleTileService" to ScreenType.WALLPAPER,
            "com.example.service.tiles.NotchTileService" to ScreenType.DYNAMIC_NOTCH,
            "com.example.service.tiles.SideDeckTileService" to ScreenType.SIDE_DECK,
            "com.example.service.tiles.StopRecordingTileService" to ScreenType.RECORDINGS,
            "com.example.service.tiles.TextAssistantTileService" to ScreenType.TEXT_ASSISTANT,
            "com.example.service.tiles.OtpTileService" to ScreenType.CODE_DETECTION
        )

        for ((tileClass, expectedScreen) in testTiles) {
            val intent = android.content.Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").apply {
                putExtra(android.content.Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName("com.skytouch.sk", tileClass))
            }
            val screen = parseScreenFromIntent(intent)
            assertEquals("Tile $tileClass should route to $expectedScreen", expectedScreen, screen)
        }
    }
}

