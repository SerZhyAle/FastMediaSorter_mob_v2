package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.launcher.ConfiguredWidgetInstanceCleaner
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1886: the launcher reset writes the density the reset dialog supplies, in the same settings write
 * that restores every other launcher field.
 *
 * The coverage gate reads the restore list lexically and therefore cannot see whether the density
 * actually reaches the store, so that one field is proven here instead.
 *
 * S2217: the reset also walks every target the desktop delete returns through the instance-cleanup
 * seam - the only point where a dropped target would leak a configured widget's stored instance.
 */
class ResetLauncherToDefaultsUseCaseTest {

    private val desktop = mockk<LauncherDesktopRepository>(relaxed = true)
    private val cleaner = mockk<ConfiguredWidgetInstanceCleaner>(relaxed = true)

    private val settings = FakeSettingsRepository(
        AppSettings().copy(
            launcherDensityFactor = STALE_DENSITY,
            launcherDesktopLocked = true,
        ),
    )

    private val useCase = ResetLauncherToDefaultsUseCase(
        desktop = desktop,
        pins = mockk<LauncherPinsRepository>(relaxed = true),
        journal = mockk<LauncherJournalRepository>(relaxed = true),
        installedApps = mockk<InstalledAppsRepository>(relaxed = true),
        settings = settings,
        storeLauncherWallpaperUseCase = mockk<StoreLauncherWallpaperUseCase>(relaxed = true),
        configuredWidgetInstances = cleaner,
    )

    @Test
    fun `reset keeps the supplied density and restores the other launcher fields`() = runBlocking {
        val completed = useCase(CHOSEN_DENSITY)

        assertTrue("the reset reported failure", completed)
        assertEquals(CHOSEN_DENSITY, settings.currentSettings.launcherDensityFactor, EXACT)
        assertEquals(
            AppSettings().launcherDesktopLocked,
            settings.currentSettings.launcherDesktopLocked,
        )
    }

    // A second write after the reset is the race ADR-3 rejects: the launcher redraws between the two
    // and would seed the desktop at the rolled-back density.
    @Test
    fun `reset writes the settings exactly once`() = runBlocking {
        useCase(CHOSEN_DENSITY)

        assertEquals(1, settings.updatedSettings.size)
    }

    @Test
    fun `reset clears a widget instance for every target the delete returned`() = runBlocking {
        coEvery { desktop.clearAll() } returns listOf(FRAME_TARGET, CAPTURE_TARGET, "app:com.example")

        val completed = useCase(CHOSEN_DENSITY)

        assertTrue("the reset reported failure", completed)
        verify(exactly = 3) { cleaner.clearInstanceOf(any()) }
        verify { cleaner.clearInstanceOf(FRAME_TARGET) }
        verify { cleaner.clearInstanceOf(CAPTURE_TARGET) }
        // A shortcut target goes through the same call rather than being filtered here - every
        // guard lives behind the seam, so the use case never learns which gadgets configure.
        verify { cleaner.clearInstanceOf("app:com.example") }
    }

    @Test
    fun `reset with no deleted targets skips the cleanup seam`() = runBlocking {
        coEvery { desktop.clearAll() } returns emptyList()

        useCase(CHOSEN_DENSITY)

        verify(exactly = 0) { cleaner.clearInstanceOf(any()) }
    }

    private companion object {
        const val CHOSEN_DENSITY = 1.25f
        const val STALE_DENSITY = 0.75f
        const val EXACT = 0f

        /** Configured gadget targets carrying their launcher tokens as the param (S1930 codec). */
        const val FRAME_TARGET = "gadget:random_photo_frame/-1000001"
        const val CAPTURE_TARGET = "gadget:camera_quick_capture/-1000002"
    }
}
