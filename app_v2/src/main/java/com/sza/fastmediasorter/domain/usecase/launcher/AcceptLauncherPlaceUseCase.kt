package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Places a validated shared place in the first free slot of the active launcher desktop. */
class AcceptLauncherPlaceUseCase @Inject constructor(
    private val desktopRepository: LauncherDesktopRepository,
) {

    suspend operator fun invoke(
        command: LauncherCellCommand.Geographic,
        orientation: LauncherOrientation,
        addedAt: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val state = desktopRepository.state()
        val columns = when (orientation) {
            LauncherOrientation.PORTRAIT -> state.columnsPortrait
            LauncherOrientation.LANDSCAPE -> state.columnsLandscape
        }
        if (columns < MIN_COLUMNS) return@withContext false
        val cell = LauncherCell(
            id = NEW_CELL_ID,
            orientation = orientation,
            rowIndex = FIRST_ROW,
            colIndex = FIRST_COLUMN,
            spanW = SHORTCUT_SPAN,
            spanH = SHORTCUT_SPAN,
            kind = LauncherCellKind.SHORTCUT,
            target = command.encode(),
            labelOverride = null,
            addedAt = addedAt,
        )
        desktopRepository.addCellInFirstFreeSlot(cell, columns) != null
    }

    private companion object {
        const val MIN_COLUMNS = 1
        const val NEW_CELL_ID = 0L
        const val FIRST_ROW = 0
        const val FIRST_COLUMN = 0
        const val SHORTCUT_SPAN = 1
    }
}
