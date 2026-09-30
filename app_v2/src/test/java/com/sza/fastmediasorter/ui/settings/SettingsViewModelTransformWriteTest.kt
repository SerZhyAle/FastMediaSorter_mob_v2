package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DeviceStorageState
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.GetDeviceStorageUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * S3819: the settings screen writes through [SettingsViewModel.updateSettings] with a transform, which the
 * repository applies to its latest snapshot - a field another component committed after the screen
 * rendered must survive the screen's write, and the screen must still see its own edit at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTransformWriteTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository(AppSettings(allowRename = false, confirmDelete = false))

    private fun createViewModel(settingsRepository: SettingsRepository = repository): SettingsViewModel {
        val storage = mockk<GetDeviceStorageUseCase>()
        coEvery { storage() } returns DeviceStorageState.Error("test")
        return SettingsViewModel(
            context = mockk(relaxed = true),
            settingsRepository = settingsRepository,
            resourceRepository = mockk(relaxed = true),
            credentialsRepository = mockk(relaxed = true),
            getDestinationsUseCase = mockk(relaxed = true),
            getResourcesUseCase = mockk(relaxed = true),
            updateResourceUseCase = mockk(relaxed = true),
            exportSettingsUseCase = mockk(relaxed = true),
            importSettingsUseCase = mockk(relaxed = true),
            resetSmbConnectionsUseCase = mockk(relaxed = true),
            syncNetworkResourcesUseCase = mockk(relaxed = true),
            cleanupTrashFoldersUseCase = mockk(relaxed = true),
            workManagerScheduler = mockk(relaxed = true),
            getDeviceStorageUseCase = storage,
            prewarmTranslationModelUseCase = mockk(relaxed = true),
            setStatisticsCollectionEnabledUseCase = mockk(relaxed = true),
            clearStreamPlayOutcomesUseCase = mockk(relaxed = true),
            remoteSourceGate = mockk(relaxed = true),
            storeLauncherWallpaperUseCase = mockk(relaxed = true),
            checkLocalFolderWritableUseCase = mockk(relaxed = true),
            resolveLocalFolderResourceUseCase = mockk(relaxed = true),
            cleanupHiddenResourceUseCase = mockk(relaxed = true),
        )
    }

    @Test
    fun `a field committed by another writer survives a screen write`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSettings { it.copy(allowRename = true) }
        repository.setSettings(repository.currentSettings.copy(confirmDelete = true))
        advanceUntilIdle()

        assertTrue(repository.currentSettings.allowRename)
        assertTrue(repository.currentSettings.confirmDelete)
        assertTrue(viewModel.settings.value.confirmDelete)
    }

    @Test
    fun `the screen sees its own edit before the write lands`() = runTest(dispatcherRule.testDispatcher) {
        // Main.immediate in production runs the settings pipeline inline; the unconfined dispatcher is its
        // test stand-in, and the gate holds the repository write so "before it lands" is observable.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val gated = object : SettingsRepository by repository {
            override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
                gate.await()
                repository.updateSettings(transform)
            }
        }
        val viewModel = createViewModel(gated)
        advanceUntilIdle()

        viewModel.updateSettings { it.copy(allowRename = true) }

        assertTrue(viewModel.settings.value.allowRename)
        assertEquals(0, repository.updatedSettings.size)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.updatedSettings.size)
        assertTrue(viewModel.settings.value.allowRename)
    }

    @Test
    fun `a callback fired before storage answered writes nothing`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.updateSettings { it.copy(allowRename = true) }
        advanceUntilIdle()

        assertEquals(0, repository.updatedSettings.size)
        assertEquals(false, repository.currentSettings.allowRename)
    }
}
