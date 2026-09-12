package com.sza.fastmediasorter.domain.usecase.apps

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S2736: the one-time move from the pre-S2736 alphabetical default onto the launch-frequency order. */
class ApplyAllAppsSortDefaultUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setUp() {
        // S1449: okio storage, not File storage - File.renameTo cannot replace an existing destination
        // on Windows.
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve("s2736.preferences_pb").toOkioPath()
            },
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        // S2748: join, not just cancel - TemporaryFolder deletes the directory after @After
        // returns, so an unfinished DataStore flush would meet a deleted file.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test
    fun `stored alphabetical default moves onto the frequency order`() = runTest {
        givenStoredOrder(InstalledAppSortOrder.LABEL)

        useCase().invoke()

        assertEquals(
            InstalledAppSortOrder.LAUNCH_FREQUENCY.name,
            capturedOrderAfterUpdate(),
        )
    }

    @Test
    fun `an order the user picked himself is left alone`() = runTest {
        givenStoredOrder(InstalledAppSortOrder.CATEGORY)

        useCase().invoke()

        coVerify(exactly = 0) { settingsRepository.updateSettings(any<suspend (AppSettings) -> AppSettings>()) }
    }

    @Test
    fun `a second run writes nothing`() = runTest {
        givenStoredOrder(InstalledAppSortOrder.LABEL)
        val useCase = useCase()
        useCase.invoke()

        givenStoredOrder(InstalledAppSortOrder.LABEL)
        useCase.invoke()

        coVerify(exactly = 1) { settingsRepository.updateSettings(any<suspend (AppSettings) -> AppSettings>()) }
    }

    private fun useCase() = ApplyAllAppsSortDefaultUseCase(dataStore, settingsRepository)

    private fun givenStoredOrder(order: InstalledAppSortOrder) {
        val settings = AppSettings().withLauncher { copy(allAppsSortOrder = order.name) }
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)
    }

    private suspend fun capturedOrderAfterUpdate(): String {
        val transforms = mutableListOf<suspend (AppSettings) -> AppSettings>()
        coVerify { settingsRepository.updateSettings(capture(transforms)) }
        val before = AppSettings().withLauncher { copy(allAppsSortOrder = InstalledAppSortOrder.LABEL.name) }
        return transforms.last().invoke(before).allAppsSortOrder
    }
}
