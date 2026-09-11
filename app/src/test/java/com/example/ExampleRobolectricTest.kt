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
}

