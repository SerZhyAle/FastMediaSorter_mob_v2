package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * S2859: pins the once-only contract of the Add-resource tile backfill - the first pass places the
 * well-known `act:` cell and raises the flag, no later pass places anything, a cleared flag (the
 * launcher reset) places again, and a failed placement leaves the flag untouched.
 */
class PlaceAddResourceTileUseCaseTest {

    private class FakeLauncherShortcutSyncRepository(
        var backfilled: Boolean = false,
    ) : LauncherShortcutSyncRepository {
        override suspend fun syncedRoutes(): Set<String>? = null
        override suspend fun setSyncedRoutes(routeKeys: Set<String>) = Unit
        override suspend fun clearSyncedRoutes() = Unit
        override suspend fun isStopwatchShortcutBackfilled(): Boolean = false
        override suspend fun setStopwatchShortcutBackfilled() = Unit
        override suspend fun syncedResourcePaths(): Set<String>? = null
        override suspend fun setSyncedResourcePaths(paths: Set<String>) = Unit
        override suspend fun clearSyncedResourcePaths() = Unit

        override suspend fun isResourcesAddTileBackfilled(): Boolean = backfilled

        override suspend fun setResourcesAddTileBackfilled() {
            backfilled = true
        }

        override suspend fun clearResourcesAddTileBackfilled() {
            backfilled = false
        }
    }

    @Test
    fun `the first pass places the Add-resource target and sets the flag`() = runBlocking {
        val baseline = FakeLauncherShortcutSyncRepository()
        val placement = placementMock(placed = true)

        useCaseWith(baseline, placement)()

        coVerify(exactly = 1) {
            placement(listOf(expectedTarget()))
        }
        assertTrue(baseline.backfilled)
    }

    @Test
    fun `a later pass places nothing once the flag is set`() = runBlocking {
        val baseline = FakeLauncherShortcutSyncRepository(backfilled = true)
        val placement = placementMock(placed = true)

        useCaseWith(baseline, placement)()

        coVerify(exactly = 0) { placement(any<Collection<String>>()) }
    }

    // Strategic §2 goal 5: the launcher reset clears the flag, and the next open must place the
    // tile again on the re-seeded desktop.
    @Test
    fun `a cleared flag places the tile again`() = runBlocking {
        val baseline = FakeLauncherShortcutSyncRepository()
        val placement = placementMock(placed = true)

        useCaseWith(baseline, placement)()
        baseline.clearResourcesAddTileBackfilled()
        useCaseWith(baseline, placement)()

        coVerify(exactly = 2) { placement(any<Collection<String>>()) }
    }

    // A failed placement must not raise the flag, or the tile would never come back.
    @Test
    fun `a failed placement leaves the flag unset`() = runBlocking {
        val baseline = FakeLauncherShortcutSyncRepository()
        val placement = placementMock(placed = false)

        useCaseWith(baseline, placement)()

        assertEquals(false, baseline.backfilled)
    }

    @Test
    fun `a placement throw leaves the flag unset and does not propagate`() = runBlocking {
        val baseline = FakeLauncherShortcutSyncRepository()
        val placement = mockk<PlaceLauncherShortcutTilesUseCase>()
        coEvery { placement(any<Collection<String>>()) } throws IOException("disk full")

        useCaseWith(baseline, placement)()

        assertEquals(false, baseline.backfilled)
    }

    private fun placementMock(placed: Boolean): PlaceLauncherShortcutTilesUseCase {
        val placement: PlaceLauncherShortcutTilesUseCase = mockk()
        coEvery { placement.invoke(any<Collection<String>>()) } returns placed
        return placement
    }

    private fun useCaseWith(
        baseline: LauncherShortcutSyncRepository,
        placement: PlaceLauncherShortcutTilesUseCase,
    ): PlaceAddResourceTileUseCase = PlaceAddResourceTileUseCase(baseline, placement)

    private fun expectedTarget(): String =
        LauncherCellCommand.LauncherAction(LauncherActionCatalog.KEY_CREATE_RESOURCE).encode()
}
