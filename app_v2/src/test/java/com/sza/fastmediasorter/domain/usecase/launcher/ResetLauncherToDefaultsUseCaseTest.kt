package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import io.mockk.mockk
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
 */
class ResetLauncherToDefaultsUseCaseTest {

    private val settings = FakeSettingsRepository(
        AppSettings().copy(
            launcherDensityFactor = STALE_DENSITY,
            launcherDesktopLocked = true,
        ),
    )

    private val useCase = ResetLauncherToDefaultsUseCase(
        desktop = mockk<LauncherDesktopRepository>(relaxed = true),
        pins = mockk<LauncherPinsRepository>(relaxed = true),
        journal = mockk<LauncherJournalRepository>(relaxed = true),
        installedApps = mockk<InstalledAppsRepository>(relaxed = true),
        settings = settings,
        storeLauncherWallpaperUseCase = mockk<StoreLauncherWallpaperUseCase>(relaxed = true),
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

    private companion object {
        const val CHOSEN_DENSITY = 1.25f
        const val STALE_DENSITY = 0.75f
        const val EXACT = 0f
    }
}
