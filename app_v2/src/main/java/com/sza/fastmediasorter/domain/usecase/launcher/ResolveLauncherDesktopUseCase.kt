package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S0404: turns stored desktop cells into renderable ones for a single orientation, resolving each
 * shortcut's label and icon. Mirrors `ResolveAppLaunchPanelTilesUseCase`: all resolution happens
 * here, off the main thread, and an unresolvable target degrades to a null visual instead of
 * throwing - the grid draws it as unavailable so the user can see and remove it.
 */
class ResolveLauncherDesktopUseCase @Inject constructor(
    private val desktopRepository: LauncherDesktopRepository,
    private val resolveVisual: ResolveLauncherCommandLabelUseCase,
) {

    operator fun invoke(orientation: LauncherOrientation): Flow<List<LauncherCellUi>> =
        desktopRepository.observeCells(orientation)
            .map { cells -> cells.map { it.toUi() } }
            .flowOn(Dispatchers.IO)

    private suspend fun LauncherCell.toUi(): LauncherCellUi {
        if (kind == LauncherCellKind.GADGET) return LauncherCellUi(this, visual = null, modeBadge = null)
        val command = LauncherCellCommand.decode(target)
            ?: return LauncherCellUi(this, visual = null, modeBadge = null)
        val resolved = resolveVisual(command)
        // A user-set caption always wins over the resolved one (app-launch panel precedent).
        val visual = resolved?.let { base ->
            labelOverride?.let { base.withLabel(it) } ?: base
        }
        return LauncherCellUi(
            cell = this,
            visual = visual,
            modeBadge = (command as? LauncherCellCommand.Resource)?.mode,
        )
    }
}
