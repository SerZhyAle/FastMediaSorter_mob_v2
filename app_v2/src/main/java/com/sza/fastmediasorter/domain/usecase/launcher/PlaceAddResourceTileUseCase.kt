package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2859: puts the persistent Add-resource tile into the launcher's Resources section, once.
 *
 * The tile is one well-known `act:` cell, so its once-only bookkeeping is a single flag rather than
 * a target baseline (strategic ADR-3, on the S2791 precedent): the first pass places it and raises
 * the flag, and no later pass ever places it again - a tile the user deleted stays deleted. The
 * launcher reset clears the flag, which is what lets a re-seeded desktop get the tile back
 * (strategic §2 goal 5). A placement that failed leaves the flag untouched, so the next launcher
 * open retries it.
 */
class PlaceAddResourceTileUseCase @Inject constructor(
    private val syncBaseline: LauncherShortcutSyncRepository,
    private val placeShortcutTiles: PlaceLauncherShortcutTilesUseCase,
) {
    suspend operator fun invoke(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            if (syncBaseline.isResourcesAddTileBackfilled()) return@runCatching

            val placed = placeShortcutTiles(listOf(addResourceTarget()))
            if (!placed) return@runCatching

            syncBaseline.setResourcesAddTileBackfilled()
        }.onFailure { Timber.w(it, "Add-resource tile backfill failed; retried on next launcher open") }
        Unit
    }

    private fun addResourceTarget(): String =
        LauncherCellCommand.LauncherAction(LauncherActionCatalog.KEY_CREATE_RESOURCE).encode()
}
