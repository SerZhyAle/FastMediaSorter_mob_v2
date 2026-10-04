package com.sza.fastmediasorter.ui.addresource.helpers

import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.PlaceLauncherShortcutTilesUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2859: the desktop half of "a resource was just created" - places the created resources into the
 * launcher's Resources section and decides whether the S1423 system pin is still wanted.
 *
 * S4088 replaced S2859 ADR-1 (owner, 2026-10-04): creation from inside the app places a tile too, but
 * only into an existing Resources section - a launcher-entry creation keeps the free-slot fallback,
 * because its user is on the desktop right now. ADR-2: when this app holds the home role, the
 * section tile is the shortcut and a pin request would double it into a random free slot; on a
 * foreign home screen the pin is the only way the resource reaches where the user lives.
 */
@Singleton
class CreatedResourcePlacementManager @Inject constructor(
    private val placeShortcutTiles: PlaceLauncherShortcutTilesUseCase,
    private val roleManager: LauncherRoleManager,
) {

    /**
     * Places one `res:<id>:BROWSE` tile per id into the Resources section of both orientations;
     * [sectionOnly] forbids any other place. Best-effort by contract: a failed pass is logged inside the
     * placement and reported as false, which never blocks the flow's finish - the tile is a convenience,
     * not a commit.
     */
    suspend fun placeLauncherTiles(resourceIds: Collection<Long>, sectionOnly: Boolean = false): Boolean =
        placeShortcutTiles(
            targets = resourceIds.map { LauncherCellCommand.Resource(it, LauncherResourceMode.BROWSE).encode() },
            sectionOnly = sectionOnly,
        )

    /** False when this app is the active home screen - the pin request would duplicate the tile. */
    fun shouldRequestSystemPin(): Boolean = !roleManager.isHomeRoleHeld()
}
