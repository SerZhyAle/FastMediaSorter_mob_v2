package com.sza.fastmediasorter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.preferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0018: regression tests for the no-op write guard in [SettingsRepositoryImpl.updateSettings].
 *
 * The guard short-circuits when the incoming AppSettings equals the currently stored
 * value, eliminating the spam of redundant DataStore writes triggered by settings UI
 * listeners during initial inflation.
 *
 * S0876: also covers the transform overload's mutex serialization - concurrent read-modify-write
 * writers must never lose an update.
 *
 * Robolectric: the write path reaches StrictModeHelper.allowDiskReads, which needs a real
 * StrictMode implementation (the JVM stub returns null and NPEs on Builder.build()).
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class SettingsRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockDataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    // S1045: real DataStore + real context for genuine persistence round-trips of the key mapping.
    private val realStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * S1449: okio storage, not the default File storage. FileStorage persists via File.renameTo,
     * which on Windows cannot replace an existing destination, so every write after the first one
     * failed. OkioStorage moves via Files.move(ATOMIC_MOVE, REPLACE_EXISTING), which does replace.
     */
    private fun realDataStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve(fileName).toOkioPath()
            },
            scope = realStoreScope,
        )

    @After
    fun tearDown() {
        realStoreScope.cancel()
    }

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockDataStore = mockk(relaxed = true)
        // dataStore.data is invoked by the repo's getSettings() flow chain when not stubbed
        every { mockDataStore.data } returns flowOf(preferencesOf())
        // updateData is the underlying suspend method that DataStore.edit { .. } delegates to
        coEvery { mockDataStore.updateData(any()) } returns preferencesOf()
        repo = spyk(SettingsRepositoryImpl(mockContext, mockDataStore))
    }

    @Test
    fun `updateSettings is idempotent when input equals current state - no DataStore write`() = runTest {
        val seed = AppSettings(language = "en", preventSleep = true, allFiles = false)
        every { repo.getSettings() } returns flowOf(seed)

        repo.updateSettings(seed.copy())

        // No-op write guard must prevent any updateData invocation (which is what edit { } delegates to).
        coVerify(exactly = 0) { mockDataStore.updateData(any()) }
    }

    @Test
    fun `updateSettings writes to DataStore when input differs from current state`() = runTest {
        val seed = AppSettings(language = "en", preventSleep = true)
        val changed = seed.copy(language = "ru")
        every { repo.getSettings() } returns flowOf(seed)

        repo.updateSettings(changed)

        // A real change must reach DataStore.
        coVerify(atLeast = 1) { mockDataStore.updateData(any()) }
    }

    @Test
    fun `updateSettings is idempotent across all default-valued AppSettings - no DataStore write`() = runTest {
        // Defensive case: a freshly-loaded AppSettings() against itself must short-circuit.
        // This is the exact scenario produced by SettingsActivity opening with all toggles
        // at their persisted values and the listeners firing once during inflation.
        val defaults = AppSettings()
        every { repo.getSettings() } returns flowOf(defaults)

        repo.updateSettings(defaults)

        coVerify(exactly = 0) { mockDataStore.updateData(any()) }
    }

    // S0876: regression guard for transformMutex in the updateSettings(transform) overload. Two
    // writers race on disjoint fields the same way the real WelcomeEnableAllManager OCR/Translation
    // enable-on-install writers do; without the mutex, writer B's stale read (taken while A is still
    // mid-transform) would silently revert A's already-committed field once B's own full-snapshot
    // write lands.
    @Test
    fun `updateSettings transform overload serializes concurrent writers - no lost update`() = runTest {
        val realRepo = SettingsRepositoryImpl(
            RuntimeEnvironment.getApplication(),
            realDataStore("s0876_concurrent_settings.preferences_pb")
        )

        val writerA = launch {
            realRepo.updateSettings { current ->
                delay(50) // holds the settings mutex, forcing writer B to queue behind this transform
                current.copy(enableOcr = true)
            }
        }
        val writerB = launch {
            realRepo.updateSettings { current -> current.copy(enableTranslation = true) }
        }
        writerA.join()
        writerB.join()

        val result = realRepo.getSettings().first()
        assertTrue("writer A's field must survive writer B's write", result.enableOcr)
        assertTrue("writer B's field must survive writer A's write", result.enableTranslation)
    }

    // S1045: secureSensitiveScreens must default to true on a fresh install (absent key) and
    // survive a save-false -> load-false round-trip through a real DataStore.
    @Test
    fun `secureSensitiveScreens defaults true and round-trips false through DataStore`() = runTest {
        val realStore = realDataStore("s1045_settings.preferences_pb")
        val realRepo = SettingsRepositoryImpl(
            RuntimeEnvironment.getApplication(),
            realStore
        )

        assertTrue(
            "unset key must load as secure-by-default true",
            realRepo.getSettings().first().secureSensitiveScreens
        )

        val current = realRepo.getSettings().first()
        realRepo.updateSettings(current.copy(secureSensitiveScreens = false))

        assertFalse(
            "saved false must load back as false",
            realRepo.getSettings().first().secureSensitiveScreens
        )
    }

    @Test
    fun `persistent audio defaults true and round-trips false through DataStore`() = runTest {
        val realRepo = SettingsRepositoryImpl(
            RuntimeEnvironment.getApplication(),
            realDataStore("s2428_settings.preferences_pb")
        )

        assertTrue(
            "unset key must keep persistent audio enabled by default (S2247)",
            realRepo.getSettings().first().enablePersistentAudioPlayback
        )

        val current = realRepo.getSettings().first()
        realRepo.updateSettings(current.copy(enablePersistentAudioPlayback = false))

        assertFalse(
            "saved false must remain the user's explicit opt-out",
            realRepo.getSettings().first().enablePersistentAudioPlayback
        )
    }

    // S2017 ADR-1: default flipped to true (hide the system status area on a fresh install),
    // superseding the S1465 ADR-4 off-by-default this test used to assert.
    @Test
    fun `launcherReplaceSystemStatusArea defaults true and round-trips false through DataStore`() = runTest {
        val realStore = realDataStore("s1087_settings.preferences_pb")
        val realRepo = SettingsRepositoryImpl(RuntimeEnvironment.getApplication(), realStore)

        assertTrue(
            "unset key must hide the Android system status area by default (S2017 ADR-1)",
            realRepo.getSettings().first().launcherReplaceSystemStatusArea
        )

        val current = realRepo.getSettings().first()
        realRepo.updateSettings(current.withLauncher { copy(replaceSystemStatusArea = false) })

        assertFalse(
            "saved false must load back as false",
            realRepo.getSettings().first().launcherReplaceSystemStatusArea
        )
    }
}
