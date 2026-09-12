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
 * Both decisions answer the strategic ADRs. ADR-1: only the caller knows the flow was entered from
 * a launcher entry point - creation from inside the app must not touch the desktop, so placement
 * happens for exactly the ids the caller hands over. ADR-2: when this app holds the home role, the
 * section tile is the shortcut and a pin request would double it into a random free slot; on a
 * foreign home screen the pin is the only way the resource reaches where the user lives.
 */
@Singleton
class CreatedResourcePlacementManager @Inject constructor(
    private val placeShortcutTiles: PlaceLauncherShortcutTilesUseCase,
    private val roleManager: LauncherRoleManager,
) {

    /**
     * Places one `res:<id>:BROWSE` tile per id into the Resources section of both orientations.
     * Best-effort by contract: a failed pass is logged inside the placement and reported as false,
     * which never blocks the flow's finish - the tile is a convenience, not a commit.
     */
    suspend fun placeLauncherTiles(resourceIds: Collection<Long>): Boolean =
        placeShortcutTiles(
            resourceIds.map { LauncherCellCommand.Resource(it, LauncherResourceMode.BROWSE).encode() },
        )

    /** False when this app is the active home screen - the pin request would duplicate the tile. */
    fun shouldRequestSystemPin(): Boolean = !roleManager.isHomeRoleHeld()
}
