package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.widget.registry.HomeWidgetEntry
import timber.log.Timber
import javax.inject.Inject

/**
 * S1170: puts a catalog widget on the launcher desktop as a gadget cell, in the first free slot.
 *
 * A UseCase rather than a call from the settings helper, because deciding the orientation, resolving
 * that orientation's column count and shaping the cell is domain work; the helper's job ends at "the
 * user picked this row" (Rule: UI carries no business logic).
 */
class PlaceHomeWidgetOnLauncherDesktopUseCase @Inject constructor(
    private val desktopRepository: LauncherDesktopRepository,
) {

    /**
     * Placed, or null when the desktop could not take the cell - the caller must say so out loud.
     *
     * Null also covers "the desktop has never been laid out", where the stored column count is still 0:
     * the launcher writes it when it first measures the grid. Both cases are genuinely "it did not land",
     * which is why they share one answer and one message rather than inventing a column count here that
     * the next real layout would contradict.
     */
    suspend operator fun invoke(
        entry: HomeWidgetEntry,
        orientation: LauncherOrientation,
        addedAt: Long,
    ): Long? {
        Timber.d("S1170: place %s on %s desktop", entry.gadgetKey, orientation.name)
        val state = desktopRepository.state()
        val columns = when (orientation) {
            LauncherOrientation.PORTRAIT -> state.columnsPortrait
            LauncherOrientation.LANDSCAPE -> state.columnsLandscape
        }
        if (columns < 1) return null
        val cell = LauncherCell(
            id = 0,
            orientation = orientation,
            // Ignored: addCellInFirstFreeSlot scans for the anchor and overwrites both.
            rowIndex = 0,
            colIndex = 0,
            spanW = entry.gadgetSpanW,
            spanH = entry.gadgetSpanH,
            kind = LauncherCellKind.GADGET,
            // The stored gadget key, never the class name - a rename or an R8 pass would orphan the cell.
            target = entry.gadgetKey,
            labelOverride = null,
            addedAt = addedAt,
        )
        return desktopRepository.addCellInFirstFreeSlot(cell, columns)
    }
}
