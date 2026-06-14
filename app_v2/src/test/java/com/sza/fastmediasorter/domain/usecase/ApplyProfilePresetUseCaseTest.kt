package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.core.xr.VrProfileSettingsSync
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetApplier
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplyProfilePresetUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val profileRepository = mockk<DeviceProfileRepository>(relaxed = true)
    private val dataSource = mockk<DeviceProfilePresetCsvDataSource>()
    private val vrProfileSettingsSync = mockk<VrProfileSettingsSync>()

    // Real applier with a relaxed Context: the coercion paths exercised here never touch the
    // PackageManager (only the `true_if_capable` branch does).
    private val applier = DeviceProfilePresetApplier(mockk<Context>(relaxed = true))

    private lateinit var useCase: ApplyProfilePresetUseCase

    @Before
    fun setup() {
        useCase = ApplyProfilePresetUseCase(
            dataSource,
            applier,
            settingsRepository,
            profileRepository,
            vrProfileSettingsSync,
        )
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        coEvery { vrProfileSettingsSync.align(any(), any()) } answers { secondArg() }
    }

    @Test
    fun `applies overrides for a profile and persists the updated settings`() = runTest {
        // A representative slice of TV_MEDIA_BOX overrides: a boolean, an int, an enum, and the
        // colorTheme BLACK -> DARK mapping.
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.TV_MEDIA_BOX to linkedMapOf(
                "preventSleep" to "TRUE",
                "networkParallelism" to "4",
                "defaultSortMode" to "DATE_DESC",
                "colorTheme" to "BLACK"
            )
        )
        coEvery { profileRepository.updatePresetApplied(1) } returns Result.success(Unit)

        val saved = slot<AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(saved)) } returns Unit

        val result = useCase.apply(DeviceProfileType.TV_MEDIA_BOX, presetVersion = 1)

        assertTrue(result.isSuccess)
        assertTrue(saved.captured.preventSleep)
        assertEquals(4, saved.captured.networkParallelism)
        assertEquals(com.sza.fastmediasorter.domain.model.SortMode.DATE_DESC, saved.captured.defaultSortMode)
        assertEquals("DARK", saved.captured.colorTheme)
        coVerify(exactly = 1) { vrProfileSettingsSync.align(DeviceProfileType.TV_MEDIA_BOX, any()) }
        coVerify(exactly = 1) { settingsRepository.updateSettings(any()) }
        coVerify(exactly = 1) { profileRepository.updatePresetApplied(1) }
    }

    @Test
    fun `skips settings application and preset marker when no overrides and vr sync is noop`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.OTHER to emptyMap()
        )

        val result = useCase.apply(DeviceProfileType.OTHER, presetVersion = 1)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { settingsRepository.updateSettings(any()) }
        coVerify(exactly = 0) { profileRepository.updatePresetApplied(any()) }
    }

    @Test
    fun `persists vr sync changes even when csv overrides are empty`() = runTest {
        every { dataSource.load() } returns mapOf(
            DeviceProfileType.OTHER to emptyMap()
        )
        coEvery {
            vrProfileSettingsSync.align(DeviceProfileType.OTHER, any())
        } answers {
            secondArg<AppSettings>().copy(disable3dVr = true)
        }

        val saved = slot<AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(saved)) } returns Unit

        val result = useCase.apply(DeviceProfileType.OTHER, presetVersion = 1)

        assertTrue(result.isSuccess)
        assertTrue(saved.captured.disable3dVr)
        coVerify(exactly = 1) { settingsRepository.updateSettings(any()) }
        coVerify(exactly = 0) { profileRepository.updatePresetApplied(any()) }
    }
}
