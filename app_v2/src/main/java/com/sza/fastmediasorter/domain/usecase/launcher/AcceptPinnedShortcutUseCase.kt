package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.pm.LauncherApps
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1205: takes a pinned-shortcut request from another app and puts it on the launcher desktop, in the
 * first free slot - the same placement rule as a widget added from Settings (S1170).
 *
 * A UseCase rather than work inside the receiving activity, for the reason
 * [PlaceHomeWidgetOnLauncherDesktopUseCase] gives: choosing the orientation, resolving that
 * orientation's column count and shaping the cell is domain work, and the activity's job ends at
 * "the system handed us this request".
 */
class AcceptPinnedShortcutUseCase @Inject constructor(
    private val dataSource: AppShortcutDataSource,
    private val desktopRepository: LauncherDesktopRepository,
) {

    /**
     * Whether the request both was accepted and landed on the desktop - the caller must say so out
     * loud either way, because accepting in silence and dropping the cell look identical to the user.
     *
     * False also covers "this desktop has never been laid out", where the stored column count is still
     * 0; the launcher writes it when it first measures the grid.
     */
    suspend operator fun invoke(
        request: LauncherApps.PinItemRequest,
        orientation: LauncherOrientation,
        addedAt: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val shortcut = dataSource.acceptPinRequest(request) ?: return@withContext false
        val state = desktopRepository.state()
        val columns = when (orientation) {
            LauncherOrientation.PORTRAIT -> state.columnsPortrait
            LauncherOrientation.LANDSCAPE -> state.columnsLandscape
        }
        if (columns < 1) return@withContext false
        val cell = LauncherCell(
            id = 0,
            orientation = orientation,
            // Ignored: addCellInFirstFreeSlot scans for the anchor and overwrites both.
            rowIndex = 0,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = LauncherCellKind.SHORTCUT,
            target = LauncherCellCommand.PinnedShortcut(
                packageName = shortcut.packageName,
                shortcutId = shortcut.id,
                label = shortcut.label,
            ).encode(),
            // Renaming a desktop cell has no entry point anywhere yet (strategic §4 decision 1).
            labelOverride = null,
            addedAt = addedAt,
        )
        desktopRepository.addCellInFirstFreeSlot(cell, columns) != null
    }
}
