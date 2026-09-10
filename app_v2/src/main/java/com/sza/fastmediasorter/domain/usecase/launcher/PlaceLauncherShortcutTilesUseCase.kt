package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2859: the one placement path into the launcher's Resources section - encoded shortcut targets
 * in, tiles in both orientations out.
 *
 * Extracted from [SyncEnabledResourceTilesUseCase]'s private placement loop (strategic ADR-5): the
 * aggregate sync and the add-resource flow must not grow second copies of the section-first rule,
 * because copies of a placement rule drift the way the add-resource finalizer's five copies did.
 * What a target means is the caller's business - this class only decides where the cell lands.
 *
 * Failing here leaves the desktop exactly as it was: the call may run next to the sync on the
 * collector of a Home-surface ViewModel, where a thrown Room exception would cancel the whole
 * `viewModelScope` and take the desktop's own cell stream down with it.
 *
 * @return true when every target was placed or was already present; false when a read or a write
 * failed, so a caller keeping its own baseline (the aggregate sync, the once-flag backfill) leaves
 * its accounting untouched and retries on the next pass.
 */
class PlaceLauncherShortcutTilesUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val resolveColumns: ResolveLauncherColumnsUseCase,
) {
    suspend operator fun invoke(targets: Collection<String>, screenIndex: Int = 0): Boolean = withContext(
        Dispatchers.IO
    ) {
        runCatching {
            if (targets.isEmpty()) return@runCatching true

            val state = desktop.state()
            val orientations = listOf(
                LauncherOrientation.PORTRAIT to state.columnsPortrait,
                LauncherOrientation.LANDSCAPE to state.columnsLandscape,
            )
            val now = System.currentTimeMillis()

            for ((orientation, storedColumns) in orientations) {
                val columns = resolveColumns(orientation, storedColumns)
                val existingTargets = desktop.observeCells(orientation).first()
                    .mapTo(mutableSetOf()) { it.target }

                for (target in targets) {
                    if (target !in existingTargets) {
                        placeTile(orientation, target, columns, now, screenIndex)
                    }
                }
            }
            true
        }.onFailure { Timber.w(it, "Launcher shortcut tile placement failed; desktop left as it is") }
            .getOrDefault(false)
    }

    /**
     * The resources section first, the whole grid only as a fallback (S2564 strategic ADR-5).
     *
     * [LauncherDesktopRepository.addCellInSection] grows the band when the section is full (S2018),
     * which is what keeps the tile under its own header. Null means this desktop carries no
     * resources header at all - the user deleted it - and the free-slot scan is then the honest
     * answer; it prefers the same section by itself whenever one does exist (S1760).
     */
    private suspend fun placeTile(
        orientation: LauncherOrientation,
        target: String,
        columns: Int,
        now: Long,
        screenIndex: Int = 0,
    ) {
        val cell = LauncherCell(
            id = 0,
            orientation = orientation,
            screenIndex = screenIndex,
            rowIndex = 0,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = LauncherCellKind.SHORTCUT,
            target = target,
            labelOverride = null,
            addedAt = now,
        )
        desktop.addCellInSection(cell, columns, LauncherCellCommand.SECTION_RESOURCES)
            ?: desktop.addCellInFirstFreeSlot(cell, columns)
    }
}
