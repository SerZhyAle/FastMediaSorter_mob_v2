package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1792: unit tests verifying that saving settings through [SettingsRepositoryImpl]
 * updates synchronous mirrors (color theme, compact player elements) without UI intervention,
 * while leaving language SharedPreferences untouched.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryMirrorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun createDataStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve(fileName).toOkioPath()
            },
            scope = testScope
        )

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `updateSettings updates colorTheme and useCompactElements mirrors without UI classes`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dataStore = createDataStore("settings_mirror_test.preferences_pb")
        val repo = SettingsRepositoryImpl(context, dataStore)

        val initial = repo.getSettings().first()
        assertEquals("AUTO", ColorThemePrefs.getMode(context))
        assertFalse(com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs.isCompact(context))

        val updated = initial.copy(
            colorTheme = "DARK_BLUE",
            useCompactElements = true
        )
        repo.updateSettings(updated)

        assertEquals("DARK_BLUE", ColorThemePrefs.getMode(context))
        assertTrue(com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs.isCompact(context))
    }

    @Test
    fun `updateSettings leaves language SharedPreferences mirror untouched`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dataStore = createDataStore("settings_lang_mirror_test.preferences_pb")
        val repo = SettingsRepositoryImpl(context, dataStore)

        val initialLanguage = LocaleHelper.getLanguage(context)

        val updated = repo.getSettings().first().copy(language = "uk")
        repo.updateSettings(updated)

        // Language mirror must remain unchanged because updateSettings does not call LocaleHelper.saveLanguage
        assertEquals(initialLanguage, LocaleHelper.getLanguage(context))
    }
}
