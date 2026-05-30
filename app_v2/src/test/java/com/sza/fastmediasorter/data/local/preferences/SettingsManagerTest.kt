package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [SettingsManager] maps a Preferences DataStore into an [AppSettings] snapshot with
 * per-field defaults, and exposes typed setters. Driven against a real file-backed
 * Preferences DataStore (pure JVM). Covers the empty-store defaults, setter round-trips,
 * and the nullable slideshow-music remove branch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var manager: SettingsManager

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("settings.preferences_pb")
        }
        manager = SettingsManager(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `empty store yields documented defaults`() = runTest {
        val s = manager.settings.first()
        assertTrue(s.confirmDeletion)
        assertTrue(s.enableUndo)
        assertEquals("LIST", s.defaultViewMode)
        assertEquals(3, s.slideshowInterval)
        assertEquals("SYSTEM", s.theme)
        assertEquals("system", s.language)
        assertEquals(10, s.maxRecipients)
        assertEquals(104857600L, s.textSizeMax)
        assertNull(s.slideshowMusicResourceId)
    }

    @Test
    fun `boolean and string setters round-trip into the settings flow`() = runTest {
        manager.setConfirmDeletion(false)
        manager.setDefaultViewMode("GRID")
        manager.setTheme("DARK")
        manager.setMaxRecipients(25)

        val s = manager.settings.first()
        assertEquals(false, s.confirmDeletion)
        assertEquals("GRID", s.defaultViewMode)
        assertEquals("DARK", s.theme)
        assertEquals(25, s.maxRecipients)
    }

    @Test
    fun `setSlideshowMusicResourceId stores a non-null value`() = runTest {
        manager.setSlideshowMusicResourceId(77L)
        assertEquals(77L, manager.settings.first().slideshowMusicResourceId)
    }

    @Test
    fun `setSlideshowMusicResourceId null removes the key`() = runTest {
        manager.setSlideshowMusicResourceId(77L)
        manager.setSlideshowMusicResourceId(null)
        assertNull(manager.settings.first().slideshowMusicResourceId)
    }

    @Test
    fun `setTextSizeMax persists a long`() = runTest {
        manager.setTextSizeMax(42L)
        assertEquals(42L, manager.settings.first().textSizeMax)
    }
}
