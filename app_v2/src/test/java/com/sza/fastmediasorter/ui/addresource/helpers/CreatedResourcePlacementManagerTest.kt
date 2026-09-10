package com.sza.fastmediasorter.ui.addresource.helpers

import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.PlaceLauncherShortcutTilesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2859: pins the two decisions the manager folds - the ADR-1 target mapping (ids become
 * `res:<id>:BROWSE` targets for the shared placement) and the ADR-2 pin rule (no system pin when
 * this app holds the home role, pin on a foreign home screen).
 */
class CreatedResourcePlacementManagerTest {

    @Test
    fun `placeLauncherTiles forwards res-encoded targets for every id`() = runBlocking {
        val placement = mockk<PlaceLauncherShortcutTilesUseCase>()
        coEvery { placement.invoke(any<Collection<String>>()) } returns true
        val manager = managerWith(placement, homeRoleHeld = false)

        val placed = manager.placeLauncherTiles(listOf(11L, 12L))

        assertTrue(placed)
        coVerify(exactly = 1) {
            placement.invoke(
                listOf(
                    LauncherCellCommand.Resource(11L, LauncherResourceMode.BROWSE).encode(),
                    LauncherCellCommand.Resource(12L, LauncherResourceMode.BROWSE).encode(),
                ),
            )
        }
    }

    // Strategic ADR-2: the section tile replaces the pin on our own desktop - a pin request would
    // land the same resource twice, once as a pinned shortcut in a random free slot.
    @Test
    fun `shouldRequestSystemPin is false when the app holds the home role`() {
        val manager = managerWith(mockk(), homeRoleHeld = true)

        assertFalse(manager.shouldRequestSystemPin())
    }

    @Test
    fun `shouldRequestSystemPin is true on a foreign home screen`() {
        val manager = managerWith(mockk(), homeRoleHeld = false)

        assertTrue(manager.shouldRequestSystemPin())
    }

    // The tile is a convenience, not a commit - the manager must pass the failure through rather
    // than swallow it, so the caller can still finish the flow.
    @Test
    fun `a failed placement is reported and not retried here`() = runBlocking {
        val placement = mockk<PlaceLauncherShortcutTilesUseCase>()
        coEvery { placement.invoke(any<Collection<String>>()) } returns false
        val manager = managerWith(placement, homeRoleHeld = false)

        assertEquals(false, manager.placeLauncherTiles(listOf(11L)))
    }

    private fun managerWith(
        placement: PlaceLauncherShortcutTilesUseCase,
        homeRoleHeld: Boolean,
    ): CreatedResourcePlacementManager {
        val roleManager = mockk<LauncherRoleManager> {
            every { isHomeRoleHeld() } returns homeRoleHeld
        }
        return CreatedResourcePlacementManager(placement, roleManager)
    }
}
