package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * S2571: the interface language has one owner, [LocaleHelper]. These tests exercise the real
 * DataStore rather than a mocked `getSettings()`, because the defect lived exactly in the gap the
 * mock used to hide - a stored second copy that diverged from the locale the app was running in.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class SettingsLanguageOwnershipTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The key the repository used to write. Spelled out because the constant is gone with it. */
    private val legacyLanguageKey = stringPreferencesKey("language")

    private fun createDataStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve(fileName).toOkioPath()
            },
            scope = testScope
        )

    @After
    fun tearDown() {
        // S2748: join, not just cancel - TemporaryFolder deletes the directory after @After
        // returns, so an unfinished DataStore flush would meet a deleted file.
        runBlocking { testScope.coroutineContext.job.cancelAndJoin() }
        LocaleHelper.resetLanguage(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a stale stored language literal never reaches AppSettings`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        LocaleHelper.resetLanguage(context)
        val dataStore = createDataStore("settings_language_stale_test.preferences_pb")
        dataStore.edit { it[legacyLanguageKey] = "ru" }

        val repo = SettingsRepositoryImpl(context, dataStore)
        val language = repo.getSettings().first().language

        assertEquals(LocaleHelper.getLanguage(context), language)
        assertNotEquals("ru", language)
    }

    @Test
    fun `updateSettings stores no language key`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        LocaleHelper.resetLanguage(context)
        val dataStore = createDataStore("settings_language_write_test.preferences_pb")
        val repo = SettingsRepositoryImpl(context, dataStore)

        repo.updateSettings(repo.getSettings().first().copy(language = "ru", preventSleep = false))

        val stored = dataStore.data.first()
        assertNull(stored[legacyLanguageKey])
        // Proves the write actually happened, so the null above is an omission and not an empty store.
        assertEquals(false, stored[booleanPreferencesKey("prevent_sleep")])
    }

    @Test
    fun `a language change re-emits settings without any DataStore write`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        LocaleHelper.resetLanguage(context)
        val dataStore = createDataStore("settings_language_revision_test.preferences_pb")
        val repo = SettingsRepositoryImpl(context, dataStore)

        val seen = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.IO) { repo.getSettings().collect { seen.add(it.language) } }
        withTimeout(AWAIT_TIMEOUT_MS) { while (seen.isEmpty()) delay(POLL_INTERVAL_MS) }

        LocaleHelper.saveLanguage(context, "uk")

        withTimeout(AWAIT_TIMEOUT_MS) { while (seen.none { it == "uk" }) delay(POLL_INTERVAL_MS) }
        assertNull(dataStore.data.first()[legacyLanguageKey])
        collector.cancel()
    }

    private companion object {
        // S2748: 20 s, not 5 s. These are wall-clock polls, and the joined teardown plus a JVM
        // shared with the rest of the package pushed the re-emit wait past a 5 s budget twice.
        const val AWAIT_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 20L
    }
}
