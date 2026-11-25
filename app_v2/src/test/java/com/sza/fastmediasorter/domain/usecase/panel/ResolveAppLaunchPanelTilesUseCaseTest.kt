package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.filler.FillerPendingCleanup
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import com.sza.fastmediasorter.domain.model.programs.ProgramsSettings
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4828: a quick-launch slot follows its program's toggle the way the picker does, and the stored
 * assignment outlives the switch-off so the program returns to its own slot.
 */
class ResolveAppLaunchPanelTilesUseCaseTest {

    private val settings = MutableStateFlow(calculatorEnabled(false))
    private val tiles = MutableStateFlow(listOf(tile(SLOT, AppLaunchPanelRouteTarget.Feature(CALCULATOR))))

    private val repository = object : AppLaunchPanelRepository {
        override fun observeTiles(): Flow<List<AppLaunchPanelTile>> = tiles
        override suspend fun setTile(tile: AppLaunchPanelTile) {
            tiles.value = tiles.value.filterNot { it.slotIndex == tile.slotIndex } + tile
        }
        override suspend fun removeTile(slotIndex: Int) {
            tiles.value = tiles.value.filterNot { it.slotIndex == slotIndex }
        }
        override suspend fun moveTile(fromSlot: Int, toSlot: Int) = Unit
        override suspend fun count(): Int = tiles.value.size
        override suspend fun replaceAll(tiles: List<AppLaunchPanelTile>) {
            this@ResolveAppLaunchPanelTilesUseCaseTest.tiles.value = tiles
        }
    }

    private val useCase = ResolveAppLaunchPanelTilesUseCase(
        repository = repository,
        resourceRepository = mockk<ResourceRepository>(relaxed = true),
        resolveRouteAvailability = ResolvePanelRouteAvailabilityUseCase(
            context = mockk<Context>(relaxed = true),
            capability = CapabilityAvailability(emptySet()),
            settingsRepository = mockk<SettingsRepository> { every { getSettings() } returns settings },
            mediaCapabilities = mockk<MediaCapabilities>(relaxed = true),
            networkMonitorContract = mockk<NetworkMonitorContract>(relaxed = true),
            launcherModeContract = mockk<LauncherModeContract> { every { isAvailableInBuild } returns false },
            screenVideoRecordingControllers = emptySet(),
            fillerPendingCleanup = object : FillerPendingCleanup {
                override val hasPending: StateFlow<Boolean> = MutableStateFlow(false)
            },
        ),
        radioControl = mockk<RadioControlContract>(relaxed = true),
        context = mockk<Context>(relaxed = true),
    )

    @Test
    fun `a switched-off program renders an empty slot and keeps its stored assignment`() = runBlocking {
        val slot = useCase().first()[SLOT]

        assertTrue("a disabled program must not look launchable", slot.isEmpty)
        assertEquals(AppLaunchPanelTileType.RESERVED, slot.type)
        assertEquals("the assignment survives the switch-off", 1, tiles.value.size)
    }

    @Test
    fun `switching the program back on restores it in the same slot`() = runBlocking {
        settings.value = calculatorEnabled(true)

        val slot = useCase().first()[SLOT]

        assertFalse(slot.isEmpty)
        assertEquals(AppLaunchPanelRouteTarget.Feature(CALCULATOR).encode(), slot.targetId)
    }

    @Test
    fun `an open panel follows a toggle change without a tile write`() = runBlocking {
        val emissions = mutableListOf<List<AppLaunchPanelTileUi>>()
        val job = launch(Dispatchers.Default) { useCase().collect { synchronized(emissions) { emissions += it } } }
        awaitSize(emissions, 1)

        settings.value = calculatorEnabled(true)
        awaitSize(emissions, 2)
        job.cancel()

        assertTrue(emissions.first()[SLOT].isEmpty)
        assertFalse(emissions.last()[SLOT].isEmpty)
    }

    @Test
    fun `a switched-off program section shortcut renders an empty slot`() = runBlocking {
        tiles.value = listOf(tile(SLOT, AppLaunchPanelRouteTarget.FeatureSection(CALCULATOR, "history")))

        assertTrue(useCase().first()[SLOT].isEmpty)
    }

    private suspend fun awaitSize(list: List<*>, size: Int) {
        withTimeout(TIMEOUT_MS) {
            while (synchronized(list) { list.size } < size) delay(POLL_MS)
        }
    }

    private companion object {
        const val SLOT = 3
        const val CALCULATOR = InternalRouteCatalog.KEY_CALCULATOR
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L

        fun calculatorEnabled(enabled: Boolean) =
            AppSettings(programs = ProgramsSettings(enableCalculator = enabled))

        fun tile(slot: Int, target: AppLaunchPanelRouteTarget) = AppLaunchPanelTile(
            slotIndex = slot,
            type = AppLaunchPanelTileType.INTERNAL_ROUTE,
            targetId = target.encode(),
            labelOverride = null,
            addedAt = 0L,
        )
    }
}
