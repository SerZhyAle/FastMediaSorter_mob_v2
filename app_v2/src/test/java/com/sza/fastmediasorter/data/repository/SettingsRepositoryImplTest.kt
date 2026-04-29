package com.sza.fastmediasorter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * S0018: regression tests for the no-op write guard in [SettingsRepositoryImpl.updateSettings].
 *
 * The guard short-circuits when the incoming AppSettings equals the currently stored
 * value, eliminating the spam of redundant DataStore writes triggered by settings UI
 * listeners during initial inflation.
 */
@ExperimentalCoroutinesApi
class SettingsRepositoryImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockDataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

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
}
